package de.intektor.kentai

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.provider.MediaStore
import android.support.v7.app.AppCompatActivity
import com.theartofdev.edmodo.cropper.CropImage
import de.intektor.kentai.kentai.*
import de.intektor.kentai.kentai.firebase.SendService
import de.intektor.kentai_http_common.gson.genGson
import de.intektor.kentai_http_common.server_to_client.UploadProfilePictureResponse
import de.intektor.kentai_http_common.util.writeUUID
import kotlinx.android.synthetic.main.activity_settings_overview.*
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.PrivateKey
import java.security.Signature
import kotlin.math.min

class SettingsOverviewActivity : AppCompatActivity() {

    companion object {
        private const val ACTION_PICK_PROFILE_PICTURE = 0
        private const val ACTION_CROP_PROFILE_PICTURE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_overview)

        val kentaiClient = applicationContext as KentaiClient

        if (hasProfilePicture(kentaiClient.userUUID, this)) {
            val clientFile = getProfilePicture(kentaiClient.userUUID, this)
            settingsOverviewActivityProfilePicture.setImageBitmap(BitmapFactory.decodeFile(clientFile.path))
        }

        settingsOverviewActivityProfilePicture.setOnClickListener {
            changeProfilePicture()
        }

        settingsOverviewActivityChangeProfilePictureButton.setOnClickListener {
            changeProfilePicture()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun changeProfilePicture() {
        if (checkStoragePermission(this, ChatActivity.PERMISSION_REQUEST_EXTERNAL_STORAGE)) {
            val pickPhoto = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(pickPhoto, ACTION_PICK_PROFILE_PICTURE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val kentaiClient = applicationContext as KentaiClient
        when (requestCode) {
            ACTION_PICK_PROFILE_PICTURE -> {
                if (data != null && resultCode == Activity.RESULT_OK) {
                    CropImage.activity(data.data)
                            .setAllowRotation(true)
                            .setAspectRatio(1, 1)
                            .start(this)
                }
            }
            CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE -> {
                if (data != null && resultCode == Activity.RESULT_OK) {
                    val result = CropImage.getActivityResult(data)

                    val startServiceIntent = Intent(this, SendService::class.java)
                    startService(startServiceIntent)

                    val sendIntent = Intent(ACTION_UPLOAD_PROFILE_PICTURE)
                    sendIntent.putExtra(KEY_PICTURE, result.uri)
                    sendBroadcast(sendIntent)
                }
            }
        }
    }

    class UploadProfilePictureTask(val callback: (UploadProfilePictureResponse.Type?) -> (Unit), val kentaiClient: KentaiClient, val data: Uri, private val contentResolver: ContentResolver) : AsyncTask<Unit, Unit, UploadProfilePictureResponse.Type?>() {

        override fun doInBackground(vararg p0: Unit?): UploadProfilePictureResponse.Type? {
            try {
                val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(data))
                val scaled = Bitmap.createBitmap(bitmap, 0, 0, min(800, bitmap.width), min(800, bitmap.height))
                val requestBody = object : RequestBody() {
                    override fun contentType(): MediaType? = MediaType.parse("application/octet-stream")

                    override fun writeTo(sink: BufferedSink) {
                        val dataOut = DataOutputStream(sink.outputStream())
                        val byteOut = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.PNG, 100, byteOut)

                        dataOut.writeUUID(kentaiClient.userUUID)

                        val byteArray = byteOut.toByteArray()

                        val signer = Signature.getInstance("SHA1WithRSA")
                        signer.initSign(kentaiClient.privateAuthKey!! as PrivateKey)
                        signer.update(byteArray)
                        val signed = signer.sign()

                        dataOut.writeInt(signed.size)
                        dataOut.write(signed)

                        dataOut.writeInt(byteArray.size)
                        dataOut.write(byteArray)
                    }
                }

                val request = Request.Builder()
                        .url(httpAddress + "uploadProfilePicture")
                        .post(requestBody)
                        .build()

                httpClient.newCall(request).execute().use { response ->
                    val gson = genGson()
                    val res = gson.fromJson(response.body()?.string(), UploadProfilePictureResponse::class.java)
                    return res.type
                }
            } catch (t: Throwable) {
                return null
            }
        }

        override fun onPostExecute(result: UploadProfilePictureResponse.Type?) {
            callback.invoke(result)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
