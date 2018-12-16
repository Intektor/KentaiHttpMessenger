package de.intektor.mercury.connection

import android.app.Service
import android.content.*
import android.net.*
import android.os.Build
import android.os.IBinder
import android.os.Parcelable
import androidx.annotation.RequiresApi
import de.intektor.mercury.action.tcp.ActionDirectConnectionClosed
import de.intektor.mercury.android.getIPacketExtra
import de.intektor.mercury.android.mercuryClient
import de.intektor.mercury.android.putExtra
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.connection.handler.*
import de.intektor.mercury.io.AddressHolder
import de.intektor.mercury.util.Logger
import de.intektor.mercury.util.ProfilePictureUtil
import de.intektor.mercury_common.tcp.*
import de.intektor.mercury_common.tcp.client_to_server.HeartbeatPacketToServer
import de.intektor.mercury_common.tcp.client_to_server.IdentificationPacketToServer
import de.intektor.mercury_common.tcp.client_to_server.InterestedUser
import de.intektor.mercury_common.tcp.client_to_server.UserPreferencePacketToServer
import de.intektor.mercury_common.tcp.server_to_client.*
import de.intektor.mercury_common.util.encryptRSA
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

class DirectConnectionService : Service() {

    override fun onBind(intent: Intent): IBinder? = null

    @RequiresApi(21)
    private val networkCallback = NetworkCallback()

    private val networkListener = NetworkListener()

    @Volatile
    private var keepConnection = true

    @Volatile
    private var currentSocket: Socket? = null

    @Volatile
    private var wantConnection = false

    @Volatile
    private var lastHeartbeatTime: Long = System.currentTimeMillis()

    val isConnected: Boolean
        get() = keepConnection && currentSocket?.isBound == true && currentSocket?.isClosed == false

    private val sendPacketQueue = LinkedBlockingQueue<IPacket>()

    private val scheduledTasks = LinkedBlockingQueue<Runnable>()

    companion object {
        private const val TAG = "DirectConnectionService"
    }

    override fun onCreate() {
        super.onCreate()

        MercuryTCPOperator.packetRegistry.apply {
            registerHandler(UserStatusChangePacketToClient::class.java, UserStatusChangePacketToClientHandler())
            registerHandler(HeartbeatPacketToClient::class.java, HeartbeatPacketToClientHandler())
            registerHandler(TypingPacketToClient::class.java, TypingPacketToClientHandler())
            registerHandler(UserViewChatPacketToClient::class.java, UserViewChatPacketToClientHandler())
            registerHandler(ProfilePictureUpdatedPacketToClient::class.java, ProfilePictureUpdatedPacketToClientHandler())
        }

        if (Build.VERSION.SDK_INT >= 21) {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            connectivityManager.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), networkCallback)
        } else {
            registerReceiver(networkListener, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
        }

        thread {
            while (true) {
                scheduledTasks.take().run()
            }
        }

        thread {
            while (true) {
                if (!isConnected && wantConnection) {
                    connect()
                }
                Thread.sleep(5000L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when {
                ActionSendPacketToServer.isAction(intent) -> {
                    val (packet) = ActionSendPacketToServer.getData(intent)
                    enqueuePacket(packet)
                }
                ActionConnect.isAction(intent) -> {
                    wantConnection = true
                    connect()
                }
                ActionDisconnect.isAction(intent) -> {
                    wantConnection = false
                    val socket = currentSocket
                    if (socket != null) disconnect(socket)
                }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun enqueuePacket(packet: IPacket) {
        sendPacketQueue += packet
    }

    fun scheduleTask(task: () -> Unit) {
        scheduledTasks += Runnable { task() }
    }

    @RequiresApi(21)
    private inner class NetworkCallback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            onConnectedToNetwork()
        }

        override fun onLost(network: Network) {
            onDisconnectedFromNetwork()
        }
    }

    private inner class NetworkListener : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val extras = intent.extras
            val info = extras.getParcelable<Parcelable>("networkInfo") as NetworkInfo
            val state = info.state

            if (state == NetworkInfo.State.CONNECTED) {
                onConnectedToNetwork()
            } else {
                onDisconnectedFromNetwork()
            }
        }
    }

    private fun onConnectedToNetwork() {
        connect()
    }

    private fun onDisconnectedFromNetwork() {
        val socket = currentSocket
        if (socket != null) disconnect(socket)
    }

    @Synchronized
    private fun connect() {
        if (isConnected || !wantConnection) return
        keepConnection = true

        lastHeartbeatTime = System.currentTimeMillis()

        sendPacketQueue.clear()

        ConnectionThread().start()
    }

    @Synchronized
    private fun disconnect(session: Socket) {
        if (!isConnected || currentSocket != session) return
        keepConnection = false
        if (currentSocket?.isBound == true) {
            currentSocket?.close()
        }
        mercuryClient().userStatusMap.clear()

        currentSocket = null

        ActionDirectConnectionClosed.launch(this)
    }

    @Synchronized
    fun heartbeatReceived() {
        lastHeartbeatTime = System.currentTimeMillis()
    }

    override fun onDestroy() {
        super.onDestroy()

        if (Build.VERSION.SDK_INT >= 21) {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            connectivityManager.unregisterNetworkCallback(networkCallback)
        } else {
            unregisterReceiver(networkListener)
        }
    }

    object ActionSendPacketToServer {

        private const val ACTION = "de.intektor.mercury.ACTION_SEND_PACKET_TO_SERVER"

        private const val EXTRA_PACKET: String = "de.intektor.mercury.EXTRA_PACKET"

        fun isAction(intent: Intent) = intent.action == ACTION

        fun getFilter(): IntentFilter = IntentFilter(ACTION)

        private fun createIntent(context: Context, packet: IPacket) =
                Intent()
                        .setAction(ACTION)
                        .setComponent(ComponentName(context, DirectConnectionService::class.java))
                        .putExtra(EXTRA_PACKET, packet)

        fun launch(context: Context, packet: IPacket) {
            context.startService(createIntent(context, packet))
        }

        fun getData(intent: Intent): Holder {
            val packet: IPacket = intent.getIPacketExtra(EXTRA_PACKET)
            return Holder(packet)
        }

        data class Holder(val packet: IPacket)
    }

    object ActionDisconnect {

        private const val ACTION = "de.intektor.mercury.ACTION_DISCONNECT"

        fun isAction(intent: Intent) = intent.action == ACTION

        fun getFilter(): IntentFilter = IntentFilter(ACTION)

        private fun createIntent(context: Context) =
                Intent()
                        .setAction(ACTION)
                        .setComponent(ComponentName(context, DirectConnectionService::class.java))

        fun launch(context: Context) {
            context.startService(createIntent(context))
        }
    }

    object ActionConnect {

        private const val ACTION = "de.intektor.mercury.ACTION_CONNECT"

        fun isAction(intent: Intent) = intent.action == ACTION

        fun getFilter(): IntentFilter = IntentFilter(ACTION)

        private fun createIntent(context: Context) =
                Intent()
                        .setAction(ACTION)
                        .setComponent(ComponentName(context, DirectConnectionService::class.java))

        fun launch(context: Context) {
            context.startService(createIntent(context))
        }
    }

    private inner class ConnectionThread : Thread() {
        override fun run() {
            val mercuryClient = this@DirectConnectionService.mercuryClient()

            try {
                val connectedSocket = Socket(AddressHolder.ADDRESS, 17348)

                currentSocket = connectedSocket

                val dataIn = DataInputStream(connectedSocket.getInputStream())

                val client = ClientPreferences.getClientUUID(this@DirectConnectionService)

                val encryptedUserUUID = client.toString().encryptRSA(ClientPreferences.getPrivateAuthKey(this@DirectConnectionService))

                val dataOut = DataOutputStream(connectedSocket.getOutputStream())
                sendPacket(IdentificationPacketToServer(encryptedUserUUID, client), dataOut)
                sendPacket(UserPreferencePacketToServer(mercuryClient.getCurrentInterestedUsers().map { userUUID ->
                    val time = ProfilePictureUtil.getProfilePicture(userUUID, mercuryClient).lastModified()
                    InterestedUser(userUUID, time)
                }.toSet()), dataOut)

                thread {
                    while (keepConnection && connectedSocket.isBound) {
                        try {
                            sendPacket(HeartbeatPacketToServer(), dataOut)
                        } catch (t: Throwable) {
                            Logger.debug(TAG, "Error while receiving packet", t)
                            if (keepConnection && connectedSocket == connectedSocket && isConnected) {
                                disconnect(connectedSocket)
                            }
                        }
                        Thread.sleep(5000L)
                    }
                }

                thread {
                    while (keepConnection && connectedSocket.isBound) {
                        Thread.sleep(10000)
                        if (System.currentTimeMillis() - 10000 > lastHeartbeatTime) {
                            disconnect(connectedSocket)
                        }
                    }
                }

                thread {
                    try {
                        while (isConnected) {
                            val packet = sendPacketQueue.take()
                            if (isConnected) {
                                sendPacket(packet, DataOutputStream(connectedSocket.getOutputStream()))
                            }
                        }
                    } catch (t: Throwable) {
                        if (keepConnection && connectedSocket == connectedSocket && isConnected) {
                            disconnect(connectedSocket)
                        }
                    }
                }

                while (isConnected && keepConnection) {
                    try {
                        val packet = readPacket(dataIn, MercuryTCPOperator.packetRegistry, Side.CLIENT)
                        handlePacket(packet, connectedSocket, ClientContext(this@DirectConnectionService))
                    } catch (t: Throwable) {
                        if (keepConnection && connectedSocket == connectedSocket && isConnected) {
                            disconnect(connectedSocket)
                        }
                    }
                }
            } catch (t: Throwable) {
            }
        }
    }
}
