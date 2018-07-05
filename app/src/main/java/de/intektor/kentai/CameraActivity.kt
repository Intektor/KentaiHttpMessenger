package de.intektor.kentai

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.google.android.cameraview.CameraView
import de.intektor.kentai.kentai.KEY_FILE_URI
import kotlinx.android.synthetic.main.activity_camera.*
import java.io.BufferedOutputStream
import java.io.File
import java.util.*

class CameraActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        activityCameraFlipButton.setOnClickListener {
            activityCameraCamera.facing = if (activityCameraCamera.facing == CameraView.FACING_BACK) CameraView.FACING_FRONT else CameraView.FACING_BACK
        }

        activityCameraTakePicture.setOnClickListener {
            activityCameraCamera.takePicture()
        }


        activityCameraCamera.addCallback(object : CameraView.Callback() {
            override fun onPictureTaken(cameraView: CameraView, data: ByteArray) {
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)

                val matrix = Matrix()
                matrix.postRotate(90f)

                val scaled = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

                val tempFile = File.createTempFile("${UUID.randomUUID()}", ".png", cacheDir)
                BufferedOutputStream(tempFile.outputStream(), 1024 * 1024).use { output ->
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, output)
                }

                runOnUiThread {
                    activityCameraCamera.stop()

                    val intent = Intent()
                    intent.putExtra(KEY_FILE_URI, Uri.fromFile(tempFile))

                    setResult(Activity.RESULT_OK, intent)
                    finish()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        activityCameraCamera.start()

    }

    override fun onPause() {
        super.onPause()
        activityCameraCamera.stop()
    }

    override fun onDestroy() {
//        activityCameraCamera.relea
        super.onDestroy()
    }
}
