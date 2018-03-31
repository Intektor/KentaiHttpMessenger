package de.intektor.kentai

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import com.google.common.io.BaseEncoding
import com.google.zxing.BarcodeFormat
import de.intektor.kentai_http_common.util.*
import kotlinx.android.synthetic.main.actitvity_transfer_scanner.*
import me.dm7.barcodescanner.zxing.ZXingScannerView
import pub.devrel.easypermissions.EasyPermissions
import java.io.DataOutputStream
import java.net.Socket
import kotlin.concurrent.thread

class TransferScannerActivity : AppCompatActivity() {

    private lateinit var scannerView: ZXingScannerView

    companion object {
        private const val REQUEST_CAMERA = 1000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.actitvity_transfer_scanner)

        scannerView = ZXingScannerView(this)
        content_frame.addView(scannerView)

        if (!EasyPermissions.hasPermissions(this, Manifest.permission.CAMERA)) {
            EasyPermissions.requestPermissions(this, "", REQUEST_CAMERA, Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        scannerView.setResultHandler { result ->
            if (result.barcodeFormat == BarcodeFormat.QR_CODE) {
                AlertDialog.Builder(this)
                        .setTitle(R.string.transport_message_title)
                        .setMessage(R.string.transport_message_message)
                        .setNegativeButton(R.string.transport_message_cancel, { _, _ -> })
                        .setPositiveButton(R.string.transport_message_proceed, { _, _ ->
                            val list = result.text.split('\u0000')
                            val ip = list[0]
                            val key = list[1].toKey()

                            val encryptedMessage = "Transport".encryptRSA(key)

                            val aesKey = generateAESKey()
                            val initVector = generateInitVector()

                            thread {
                                val socket = Socket(ip, 37455)
                                val dataOut = DataOutputStream(socket.getOutputStream())
                                dataOut.writeUTF(encryptedMessage)

                                dataOut.writeUTF(BaseEncoding.base64().encode(aesKey.encoded).encryptRSA(key))
                                dataOut.writeUTF(BaseEncoding.base64().encode(initVector).encryptRSA(key))

                                dataOut.writeUTF(KentaiClient.INSTANCE.username.encryptAES(aesKey, initVector))
                                dataOut.writeUTF(KentaiClient.INSTANCE.userUUID.toString().encryptAES(aesKey, initVector))

                                dataOut.writeUTF(BaseEncoding.base64().encode(KentaiClient.INSTANCE.privateAuthKey!!.encoded).encryptAES(aesKey, initVector))
                                dataOut.writeUTF(BaseEncoding.base64().encode(KentaiClient.INSTANCE.publicAuthKey!!.encoded).encryptAES(aesKey, initVector))
                                dataOut.writeUTF(BaseEncoding.base64().encode(KentaiClient.INSTANCE.privateMessageKey!!.encoded).encryptAES(aesKey, initVector))
                                dataOut.writeUTF(BaseEncoding.base64().encode(KentaiClient.INSTANCE.publicMessageKey!!.encoded).encryptAES(aesKey, initVector))
                            }
                        }).show()
            }
        }
        scannerView.startCamera()
    }

    override fun onPause() {
        super.onPause()
        scannerView.stopCamera()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CAMERA -> {
            }
        }
    }
}
