package de.intektor.mercury.media

import android.os.AsyncTask
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.reference.ReferenceUtil
import java.io.File
import java.io.InputStream
import java.util.*

/**
 * Saves a file into the given reference file
 * @author Intektor
 */
class SaveReferenceTask(private val mercuryClient: MercuryClient,
                        private val inputStream: InputStream,
                        private val referenceUUID: UUID,
                        private val onFinishedCallback: () -> Unit) : AsyncTask<Unit, Unit, Unit>() {

    override fun doInBackground(vararg params: Unit?) {
        saveReference(mercuryClient, inputStream, referenceUUID)
    }

    override fun onPostExecute(result: Unit?) {
        onFinishedCallback()
    }

    companion object {
        fun saveReference(mercuryClient: MercuryClient, originalFile: File, referenceUUID: UUID) {
            saveReference(mercuryClient, originalFile.inputStream(), referenceUUID)
        }

        fun saveReference(mercuryClient: MercuryClient, inputStream: InputStream, referenceUUID: UUID) {
            inputStream.use { input ->
                ReferenceUtil.getFileForReference(mercuryClient, referenceUUID).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}