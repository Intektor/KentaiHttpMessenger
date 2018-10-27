package de.intektor.mercury.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.support.v7.app.AppCompatActivity
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.Picasso
import com.theartofdev.edmodo.cropper.CropImage
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.android.checkStoragePermission
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury.android.isUsingLightTheme
import de.intektor.mercury.android.setSelectedTheme
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.io.ChatMessageService
import de.intektor.mercury.util.*
import kotlinx.android.synthetic.main.activity_settings_overview.*

class SettingsOverviewActivity : AppCompatActivity() {

    companion object {
        private const val ACTION_PICK_PROFILE_PICTURE = 0
    }

    private lateinit var receiverUploadedProfilePicture: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this))

        setContentView(R.layout.activity_settings_overview)

        val mercuryClient = applicationContext as MercuryClient

        val client = ClientPreferences.getClientUUID(this)

        if (hasProfilePicture(client, this)) {
            val clientFile = getProfilePicture(client, this)
            settingsOverviewActivityProfilePicture.setImageBitmap(BitmapFactory.decodeFile(clientFile.path))
        }

        settingsOverviewActivityProfilePicture.setOnClickListener {
            changeProfilePicture()
        }

        settingsOverviewActivityChangeProfilePictureButton.setOnClickListener {
            changeProfilePicture()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        receiverUploadedProfilePicture = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                Picasso.get()
                        .load(getProfilePicture(client, context))
                        .memoryPolicy(MemoryPolicy.NO_CACHE)
                        .into(settingsOverviewActivityProfilePicture)
            }
        }

        settingsOverviewLightThemeSwitch.isChecked = isUsingLightTheme(this)

        settingsOverviewLightThemeSwitch.setOnCheckedChangeListener { _, isChecked ->
            setSelectedTheme(this, isChecked)
            setTheme(getSelectedTheme(this))

            val i = Intent(this, SettingsOverviewActivity::class.java)
            startActivity(i)
            finish()
        }
    }

    private fun changeProfilePicture() {
        if (checkStoragePermission(this, /*TODO: */0)) {
            val pickPhoto = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(pickPhoto, ACTION_PICK_PROFILE_PICTURE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val mercuryClient = applicationContext as MercuryClient
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

                    val startServiceIntent = Intent(this, ChatMessageService::class.java)
                    startServiceIntent.action = ACTION_UPLOAD_PROFILE_PICTURE
                    startServiceIntent.putExtra(KEY_PICTURE, result.uri)
                    startService(startServiceIntent)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(receiverUploadedProfilePicture, IntentFilter(ACTION_PROFILE_PICTURE_UPLOADED))
    }

    override fun onPause() {
        super.onPause()

        unregisterReceiver(receiverUploadedProfilePicture)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
