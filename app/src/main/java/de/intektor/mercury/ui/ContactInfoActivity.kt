package de.intektor.mercury.ui

import android.content.*
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury.android.getUUIDExtra
import de.intektor.mercury.chat.getContact
import de.intektor.mercury.chat.getUserChat
import de.intektor.mercury.contacts.ContactUtil
import de.intektor.mercury.media.MediaFile
import de.intektor.mercury.media.MediaProviderReference
import de.intektor.mercury.ui.util.MediaAdapter
import de.intektor.mercury.util.ACTION_PROFILE_PICTURE_UPDATED
import de.intektor.mercury.util.KEY_USER_UUID
import de.intektor.mercury.util.ProfilePictureUtil
import de.intektor.mercury_common.users.ProfilePictureType
import kotlinx.android.synthetic.main.activity_user_chat_info.*
import java.util.*

class ContactInfoActivity : AppCompatActivity() {

    private lateinit var updateProfilePictureReceiver: BroadcastReceiver

    companion object {

        private const val EXTRA_USER_U_U_I_D: String = "de.intektor.mercury.EXTRA_USER_U_U_I_D"

        private fun createIntent(context: Context, userUUID: UUID) =
                Intent()
                        .setComponent(ComponentName(context, ContactInfoActivity::class.java))
                        .putExtra(EXTRA_USER_U_U_I_D, userUUID)

        fun launch(context: Context, userUUID: UUID) {
            context.startActivity(createIntent(context, userUUID))
        }

        fun getData(intent: Intent): Holder {
            val userUUID: UUID = intent.getUUIDExtra(EXTRA_USER_U_U_I_D)
            return Holder(userUUID)
        }

        data class Holder(val userUUID: UUID)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this, false))
        setContentView(R.layout.activity_user_chat_info)

        setSupportActionBar(activity_user_chat_info_tb)

        val mercuryClient = applicationContext as MercuryClient

        val (userUUID) = getData(intent)

        val contact = getContact(mercuryClient.dataBase, userUUID)

        supportActionBar?.title = ContactUtil.getDisplayName(this, mercuryClient.dataBase, contact)

        ProfilePictureUtil.loadProfilePicture(userUUID, ProfilePictureType.NORMAL, object : Target {
            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                println()
            }

            override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
                println()
            }

            override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom?) {
                activity_user_chat_info_iv_profile_picture.setImageBitmap(bitmap)

                activity_user_chat_info_iv_profile_picture.layoutParams.height = resources.displayMetrics.widthPixels
            }
        })

        val chatInfo = getUserChat(this, mercuryClient.dataBase, contact)

        activity_user_chat_info_content_cl_media_parent.setOnClickListener {
            ViewMediaActivity.launch(this, chatInfo)
        }

        updateProfilePictureReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val updatedUserUUID = intent.getSerializableExtra(KEY_USER_UUID) as UUID
                if (updatedUserUUID == userUUID) {
                    Picasso.get().load(ProfilePictureUtil.getProfilePicture(userUUID, context)).placeholder(R.drawable.baseline_account_circle_24).memoryPolicy(MemoryPolicy.NO_CACHE).into(activity_user_chat_info_iv_profile_picture)
                }
            }
        }

        val previewFiles = MediaProviderReference(chatInfo.chatUUID).loadMediaFiles(this, 0L, Long.MAX_VALUE, limit = 5)
                .map { BigPreviewFile(it) }

        activity_user_chat_info_rv_media.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        activity_user_chat_info_rv_media.adapter = MediaAdapter<BigPreviewFile>(previewFiles, { _, _ -> }, { _, _ -> })

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }


    override fun onResume() {
        super.onResume()

        registerReceiver(updateProfilePictureReceiver, IntentFilter(ACTION_PROFILE_PICTURE_UPDATED))
    }

    override fun onPause() {
        super.onPause()

        unregisterReceiver(updateProfilePictureReceiver)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private class BigPreviewFile(file: MediaFile) : MediaAdapter.MediaFileWrapper(file)
}
