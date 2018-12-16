package de.intektor.mercury.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceFragment
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.Picasso
import com.theartofdev.edmodo.cropper.CropImage
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.action.profpic.ActionProfilePictureUploaded
import de.intektor.mercury.android.checkWriteStoragePermission
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury.android.setSelectedTheme
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.io.ChatMessageService
import de.intektor.mercury.util.ProfilePictureUtil
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

        val client = ClientPreferences.getClientUUID(this)

        if (ProfilePictureUtil.hasProfilePicture(client, this)) {
            val clientFile = ProfilePictureUtil.getProfilePicture(client, this)
            activity_settings_overview_iv_pp.setImageBitmap(BitmapFactory.decodeFile(clientFile.path))
        }

        activity_settings_overview_cl_change_pp_parent.setOnClickListener {
            changeProfilePicture()
        }

        activity_settings_overview_tv_username.text = ClientPreferences.getUsername(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        receiverUploadedProfilePicture = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                Picasso.get()
                        .load(ProfilePictureUtil.getProfilePicture(client, context))
                        .memoryPolicy(MemoryPolicy.NO_CACHE)
                        .into(activity_settings_overview_iv_pp)
            }
        }

        fragmentManager.beginTransaction()
                .replace(R.id.activity_settings_overview_fl_settings, SettingsFragment().setOnChangeThemeCallback {
                    finish()
                    startActivity(Intent(this, SettingsOverviewActivity::class.java))
                })
                .commit()
    }

    private fun changeProfilePicture() {
        if (checkWriteStoragePermission(this, /*TODO: */0)) {
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

                    ChatMessageService.ActionUploadProfilePicture.launch(this, result.uri)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).apply {
            registerReceiver(receiverUploadedProfilePicture, IntentFilter(ActionProfilePictureUploaded.getFilter()))
        }
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).apply {
            unregisterReceiver(receiverUploadedProfilePicture)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    class SettingsFragment : PreferenceFragment() {

        private var onChangeTheme: (() -> Unit)? = null

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            findPreference(getString(R.string.preference_use_light_theme)).setOnPreferenceChangeListener { preference, newValue ->
                if (newValue is Boolean) {
                    setSelectedTheme(preference.context, newValue)

                    onChangeTheme?.invoke()
                    return@setOnPreferenceChangeListener true
                }
                return@setOnPreferenceChangeListener false
            }
        }

        fun setOnChangeThemeCallback(callback: () -> Unit): SettingsFragment {
            this.onChangeTheme = callback
            return this
        }
    }
}
