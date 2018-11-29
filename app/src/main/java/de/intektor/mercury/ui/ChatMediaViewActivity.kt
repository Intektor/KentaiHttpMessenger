package de.intektor.mercury.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.android.getUUIDExtra
import de.intektor.mercury.chat.getContact
import de.intektor.mercury.contacts.ContactUtil
import de.intektor.mercury.database.getUUID
import de.intektor.mercury.media.MediaFile
import de.intektor.mercury.media.ReferenceFile
import de.intektor.mercury.reference.ReferenceUtil
import de.intektor.mercury.ui.support.FragmentViewImage
import de.intektor.mercury.ui.support.FragmentViewImageResetAdapter
import de.intektor.mercury.ui.view.SwipeBackLayout
import de.intektor.mercury_common.chat.MessageData
import de.intektor.mercury_common.chat.data.MessageImage
import de.intektor.mercury_common.chat.data.MessageVideo
import de.intektor.mercury_common.gson.genGson
import kotlinx.android.synthetic.main.activity_view_individual_media.*
import java.text.DateFormat
import java.util.*

class ChatMediaViewActivity : AppCompatActivity(), FragmentViewImage.BindCallback {

    companion object {
        private val whenFormat = DateFormat.getDateTimeInstance()

        private const val EXTRA_CHAT_UUID: String = "de.intektor.mercury.EXTRA_CHAT_UUID"
        private const val EXTRA_TARGET_REFERENCE: String = "de.intektor.mercury.EXTRA_TARGET_REFERENCE"
        private const val EXTRA_START_PLAY: String = "de.intektor.mercury.EXTRA_START_PLAY"

        private fun createIntent(context: Context, chatUuid: UUID, targetReference: UUID, startPlay: Boolean = false) =
                Intent()
                        .setComponent(ComponentName(context, ChatMediaViewActivity::class.java))
                        .putExtra(EXTRA_CHAT_UUID, chatUuid)
                        .putExtra(EXTRA_TARGET_REFERENCE, targetReference)
                        .putExtra(EXTRA_START_PLAY, startPlay)

        fun launch(context: Context, chatUuid: UUID, targetReference: UUID, startPlay: Boolean = false) {
            context.startActivity(createIntent(context, chatUuid, targetReference, startPlay))
        }

        fun getData(intent: Intent): Holder {
            val chatUuid: UUID = intent.getUUIDExtra(EXTRA_CHAT_UUID)
            val targetReference: UUID = intent.getUUIDExtra(EXTRA_TARGET_REFERENCE)
            val startPlay: Boolean = intent.getBooleanExtra(EXTRA_START_PLAY, false)
            return Holder(chatUuid, targetReference, startPlay)
        }

        data class Holder(val chatUuid: UUID, val targetReference: UUID, val startPlay: Boolean)

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_view_individual_media)

        val mercuryClient = applicationContext as MercuryClient

        val (chatUuid, targetReference, startPlay) = getData(intent)

        val targetReferenceTime = mercuryClient.dataBase.rawQuery("SELECT time FROM reference WHERE reference_uuid = ?", arrayOf(targetReference.toString())).use { cursor ->
            if (!cursor.moveToNext()) return@use null
            cursor.getLong(0)
        }

        val targetIndex = if (targetReferenceTime != null) {
            val targetIndex = mercuryClient.dataBase.rawQuery("SELECT COUNT(reference_uuid) FROM reference WHERE time < ? ORDER BY time ASC", arrayOf(targetReferenceTime.toString())).use { cursor ->
                cursor.moveToNext()
                cursor.getInt(0)
            }

            targetIndex
        } else 0

        val pagerAdapter = FragmentMediaAdapter(supportFragmentManager, ReferenceUtil.getAmountChatReferences(mercuryClient.dataBase, chatUuid), startPlay, targetIndex, { position ->
            val (mediaFile, messageUUID) = mercuryClient.dataBase.rawQuery("SELECT reference_uuid, message_uuid, media_type, time FROM reference WHERE chat_uuid = ? ORDER BY time ASC LIMIT 1 OFFSET ?",
                    arrayOf(chatUuid.toString(), position.toString())).use { cursor ->
                cursor.moveToNext()

                val referenceUUID = cursor.getUUID(0)
                val messageUUID = cursor.getUUID(1)
                val mediaType = cursor.getInt(2)
                val time = cursor.getLong(3)

                ReferenceFile(referenceUUID, chatUuid, mediaType, time) to messageUUID
            }

            val (senderUUID, timeCreated) = mercuryClient.dataBase.rawQuery("SELECT sender_uuid, time_created FROM chat_message WHERE message_uuid = ?", arrayOf(messageUUID.toString())).use { cursor ->
                if (!cursor.moveToNext()) return@use null to null

                val senderUUID = cursor.getUUID(0)
                val timeCreated = cursor.getLong(1)

                senderUUID to timeCreated
            }

            val (headline, subtext) = if (senderUUID != null) {
                val data = mercuryClient.dataBase.rawQuery("SELECT data FROM message_data WHERE message_uuid = ?", arrayOf(messageUUID.toString())).use { cursor ->
                    cursor.moveToNext()

                    val rawData = cursor.getString(0)

                    genGson().fromJson(rawData, MessageData::class.java)
                }

                val senderContact = getContact(mercuryClient.dataBase, senderUUID)

                val headline = getString(R.string.view_individual_media_who_when,
                        ContactUtil.getDisplayName(this, mercuryClient.dataBase, senderContact),
                        whenFormat.format(Date(timeCreated ?: 0)))

                val subtext = getText(data)

                headline to subtext
            } else null to null

            MediaData(mediaFile, headline, subtext)
        }) { _, fragment ->
            if (fragment is FragmentViewImage) {
                val view = fragment.view as SwipeBackLayout
                bindSwipeBackListener(view)
            }
        }
        activity_chat_media_view_vp_content.adapter = pagerAdapter
        activity_chat_media_view_vp_content.addOnPageChangeListener(pagerAdapter)

        activity_chat_media_view_iv_close.setOnClickListener {
            finish()
        }

        activity_chat_media_view_vp_content.currentItem = targetIndex
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_view_individual_media, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item != null) {
            when (item.itemId) {
                R.id.menuViewIndividualMediaShare -> {
                    return true
                }
            }
        }
        return false
    }

    private fun getText(data: MessageData): String = when (data) {
        is MessageImage -> data.text
        is MessageVideo -> data.text
        else -> "Error: Wrong type"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private class FragmentMediaAdapter(fragmentManager: FragmentManager,
                                       private val totalAmount: Int,
                                       private val startPlay: Boolean,
                                       private val targetIndex: Int,
                                       private val getMediaData: (Int) -> MediaData,
                                       private val onNewPageSelected: (Int, Fragment?) -> Unit) : FragmentViewImageResetAdapter(fragmentManager) {

        override fun getItem(position: Int): Fragment {
            val (file, headline, subtext) = getMediaData(position)

            val fragment = FragmentViewImage.create(file, headline, subtext)

            if (startPlay && targetIndex == position) {
                fragment.markAutoPlay()
            }

            return fragment
        }

        override fun getCount(): Int = totalAmount

        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)

            onNewPageSelected(position, fragments[position])
        }
    }

    override fun bind(item: FragmentViewImage) {
        val view = item.view as SwipeBackLayout
        bindSwipeBackListener(view)
    }

    private fun bindSwipeBackListener(view: SwipeBackLayout) {
        view.setOnSwipeBackListener(object : SwipeBackLayout.SwipeBackListener {
            override fun onViewPositionChanged(fractionAnchor: Float, fractionScreen: Float) {
                val newAlpha = 1 - fractionScreen
                activity_chat_media_view_iv_background.alpha = newAlpha
                activity_chat_media_view_cv_close.alpha = newAlpha
                activity_chat_media_view_cv_share.alpha = newAlpha
            }
        })
    }

    private data class MediaData(val file: MediaFile, val headline: String?, val subtext: String?)
}
