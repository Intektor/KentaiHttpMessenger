package de.intektor.mercury.media

import android.content.Context
import de.intektor.mercury.reference.ReferenceUtil
import java.util.*

class ReferenceFile(val referenceUUID: UUID, val chatUUID: UUID, override val mediaType: Int, override val epochSecondAdded: Long) : MediaFile {
    override fun getPath(context: Context): String = ReferenceUtil.getFileForReference(context, referenceUUID).path
}