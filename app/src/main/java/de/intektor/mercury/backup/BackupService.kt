package de.intektor.mercury.backup

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import java.text.DateFormat
import java.util.*
import kotlin.concurrent.thread

/**
 * @author Intektor
 */
class BackupService : Service() {

    companion object {
        val BACKUP_NAME_EXTRA = "backup_name"

        val NOTIFICATION_CREATE_BACKUP = 1100
        val SCOPES = arrayOf(DriveScopes.DRIVE_FILE)
        val DRIVE_SHARED_PREFERENCES = "mercury.drive.preferences"

        val PREF_ACCOUNT_NAME = "accountName"
    }

    override fun onCreate() {
        super.onCreate()
        //TODO
//        registerReceiver(object : BroadcastReceiver() {
//            override fun onReceive(context: Context, intent: Intent) {
//                val backupName = intent.getStringExtra(BACKUP_NAME_EXTRA)
//                createAndUploadBackup(backupName, context.applicationContext as MercuryClient)
//            }
//
//        }, IntentFilter(Mercury.ACTION_BACKUP))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun createAndUploadBackup(backupName: String, mercuryClient: MercuryClient) {
        val credential = GoogleAccountCredential
                .usingOAuth2(applicationContext, SCOPES.asList())
                .setBackOff(ExponentialBackOff())


        val accountName = applicationContext.getSharedPreferences(DRIVE_SHARED_PREFERENCES, Context.MODE_PRIVATE).getString(PREF_ACCOUNT_NAME, null)

        credential.selectedAccountName = accountName

        val dateFormat = DateFormat.getDateTimeInstance()

        val fileName = "$backupName-${dateFormat.format(Date())}"

        val notM = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(this, "mercury_progress")
        builder.setContentTitle(getString(R.string.overview_activity_chat_backup_alert_progress_title))
        builder.setContentText(getString(R.string.overview_activity_chat_backup_alert_progress_message))
        builder.setSmallIcon(android.R.drawable.ic_menu_upload)

        builder.setProgress(100, 0, false)

        thread {
            val backupFile = createChatBackup(this, backupName, { progress: Int ->
                builder.setProgress(100, progress, false)
                notM.notify(NOTIFICATION_CREATE_BACKUP, builder.build())
            }, mercuryClient)

            builder.setContentTitle(getString(R.string.overview_activity_chat_backup_alert_progress_title))
            builder.setContentText(getString(R.string.overview_activity_chat_backup_alert_progress_upload_title))

            builder.setProgress(100, 0, false)
            notM.notify(NOTIFICATION_CREATE_BACKUP, builder.build())

            val transport = AndroidHttp.newCompatibleTransport()
            val jsonFactory = JacksonFactory.getDefaultInstance()
            val driveService = Drive.Builder(transport, jsonFactory, credential)
                    .setApplicationName("Mercury Messenger")
                    .build()

            val fileMetadata = File()
            fileMetadata.name = "$fileName.zip"
//            val mediaContent = FileContent("application/mercury.backup.zip", backupFile)
//
//            val temp: Drive.Files.Create = driveService.files().create(fileMetadata, mediaContent)
//            temp.mediaHttpUploader.setProgressListener { uploader ->
//                if (uploader.uploadState == MediaHttpUploader.UploadState.MEDIA_IN_PROGRESS) {
//                    builder.setProgress(100, (uploader.progress * 100).toInt(), false)
//                    notM.notify(NOTIFICATION_CREATE_BACKUP, builder.build())
//                }
//            }
//            temp.setFields("id").execute()
//
//            builder.setProgress(100, 100, false)
//            notM.notify(NOTIFICATION_CREATE_BACKUP, builder.build())
        }
    }
}