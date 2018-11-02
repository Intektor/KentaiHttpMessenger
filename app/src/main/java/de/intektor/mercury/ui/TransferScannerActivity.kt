package de.intektor.mercury.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury_common.util.encryptRSA
import de.intektor.mercury_common.util.generateAESKey
import de.intektor.mercury_common.util.generateInitVector
import de.intektor.mercury_common.util.toKey
import kotlinx.android.synthetic.main.actitvity_transfer_scanner.*
import me.dm7.barcodescanner.zxing.ZXingScannerView
import kotlin.concurrent.thread

class TransferScannerActivity : AppCompatActivity() {

    private lateinit var scannerView: ZXingScannerView

    companion object {
        private const val REQUEST_CAMERA = 1000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this))

        setContentView(R.layout.actitvity_transfer_scanner)

        scannerView = ZXingScannerView(this)
        content_frame.addView(scannerView)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        val mercuryClient = applicationContext as MercuryClient

        scannerView.setResultHandler { result ->
            if (result.barcodeFormat == BarcodeFormat.QR_CODE) {
                AlertDialog.Builder(this)
                        .setTitle(R.string.transport_message_title)
                        .setMessage(R.string.transport_message_message)
                        .setNegativeButton(R.string.transport_message_cancel, null)
                        .setPositiveButton(R.string.transport_message_proceed) { _, _ ->
                            val list = result.text.split('\u0000')
                            val ip = list[0]
                            val key = list[1].toKey()

                            val encryptedMessage = "Transport".encryptRSA(key)

                            val aesKey = generateAESKey()
                            val initVector = generateInitVector()

                            thread {
                                TODO()
//                                val socket = Socket(ip, 37455)
//                                val dataOut = DataOutputStream(socket.getOutputStream())
//                                dataOut.writeUTF(encryptedMessage)
//
//                                dataOut.writeUTF(BaseEncoding.base64().encode(aesKey.encoded).encryptRSA(key))
//                                dataOut.writeUTF(BaseEncoding.base64().encode(initVector).encryptRSA(key))
//
//                                dataOut.writeUTF(mercuryClient.username.encryptAES(aesKey, initVector))
//                                dataOut.writeUTF(mercuryClient.userUUID.toString().encryptAES(aesKey, initVector))
//
//                                dataOut.writeUTF(BaseEncoding.base64().encode(mercuryClient.privateAuthKey!!.encoded).encryptAES(aesKey, initVector))
//                                dataOut.writeUTF(BaseEncoding.base64().encode(mercuryClient.publicAuthKey!!.encoded).encryptAES(aesKey, initVector))
//                                dataOut.writeUTF(BaseEncoding.base64().encode(mercuryClient.privateMessageKey!!.encoded).encryptAES(aesKey, initVector))
//                                dataOut.writeUTF(BaseEncoding.base64().encode(mercuryClient.publicMessageKey!!.encoded).encryptAES(aesKey, initVector))
                            }
                        }.show()
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
