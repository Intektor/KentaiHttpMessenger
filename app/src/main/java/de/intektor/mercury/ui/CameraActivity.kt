package de.intektor.mercury.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.MotionEvent
import com.otaliastudios.cameraview.*
import de.intektor.mercury.R
import de.intektor.mercury.util.*
import kotlinx.android.synthetic.main.activity_camera.*
import java.io.BufferedOutputStream
import java.io.File
import java.util.*

class CameraActivity : AppCompatActivity() {

    private var isRecording: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        activityCameraFlipButton.setOnClickListener {
            activityCameraCamera.facing = if (activityCameraCamera.facing == Facing.BACK) Facing.FRONT else Facing.BACK
        }

        activityCameraTakePicture.setOnClickListener {
            activityCameraCamera.capturePicture()
        }

        activityCameraTakePicture.setOnLongClickListener {
            startRecording()
            isRecording = true
            true
        }

        activityCameraTakePicture.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                if (isRecording) {
                    stopRecording()
                }
                return@setOnTouchListener true
            }
            false
        }


        activityCameraCamera.addCameraListener(object : CameraListener() {
            override fun onPictureTaken(jpeg: ByteArray) {
                CameraUtils.decodeBitmap(jpeg) { bitmap ->
                    val tempFile = File.createTempFile("${UUID.randomUUID()}", ".jpg", cacheDir)
                    BufferedOutputStream(tempFile.outputStream(), 1024 * 1024).use { output ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
                    }

                    val intent = Intent()
                    intent.putExtra(KEY_FILE_URI, Uri.fromFile(tempFile))

                    setResult(Activity.RESULT_OK, intent)
                    finish()
                }
            }

            override fun onVideoTaken(video: File) {
                val intent = Intent()
                intent.putExtra(KEY_FILE_URI, Uri.fromFile(video))

                setResult(Activity.RESULT_OK, intent)
                finish()
            }
        })

        val cameraSettings = getSharedPreferences(SP_CAMERA_SETTINGS, Context.MODE_PRIVATE)
        val flashSetting = cameraSettings.getInt(KEY_CAMERA_SETTINGS_FLASH_MODE, 0)


        applyCameraFlashMode(flashSetting)

        activityCameraFlashButton.setOnClickListener {
            val currentFlashSetting = cameraSettings.getInt(KEY_CAMERA_SETTINGS_FLASH_MODE, 0)

            val new = when (currentFlashSetting) {
                CAMERA_SETTINGS_FLASH_OFF -> CAMERA_SETTINGS_FLASH_AUTO
                CAMERA_SETTINGS_FLASH_AUTO -> CAMERA_SETTINGS_FLASH_ON
                CAMERA_SETTINGS_FLASH_ON -> CAMERA_SETTINGS_FLASH_OFF
                else -> CAMERA_SETTINGS_FLASH_OFF
            }

            cameraSettings.edit()
                    .putInt(KEY_CAMERA_SETTINGS_FLASH_MODE, new)
                    .apply()

            applyCameraFlashMode(new)
        }
    }

    private fun applyCameraFlashMode(flashMode: Int) {
        when (flashMode) {
            CAMERA_SETTINGS_FLASH_OFF -> activityCameraCamera.flash = Flash.OFF
            CAMERA_SETTINGS_FLASH_AUTO -> activityCameraCamera.flash = Flash.AUTO
            CAMERA_SETTINGS_FLASH_ON -> activityCameraCamera.flash = Flash.ON
        }

        activityCameraFlashButton.setImageResource(when (flashMode) {
            CAMERA_SETTINGS_FLASH_OFF -> R.drawable.baseline_flash_off_white_36
            CAMERA_SETTINGS_FLASH_AUTO -> R.drawable.baseline_flash_auto_white_36
            CAMERA_SETTINGS_FLASH_ON -> R.drawable.baseline_flash_on_white_36
            else -> R.drawable.baseline_warning_white_36
        })
    }

    private fun startRecording() {
        activityCameraCamera.sessionType = SessionType.VIDEO
        val tempFile = File.createTempFile("${UUID.randomUUID()}", ".mp4", cacheDir)
        activityCameraCamera.startCapturingVideo(tempFile)
    }

    private fun stopRecording() {
        isRecording = false

        activityCameraCamera.stopCapturingVideo()
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
        super.onDestroy()
        activityCameraCamera.destroy()
    }
}
