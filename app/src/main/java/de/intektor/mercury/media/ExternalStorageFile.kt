package de.intektor.mercury.media

import android.content.Context
import android.net.Uri

data class ExternalStorageFile(val uriString: String, override val mediaType: Int, override val epochSecondAdded: Long, val storeId: Long? = null) : MediaFile {

    val uri: Uri
        get() = Uri.parse(uriString)

    override fun getUri(context: Context): Uri = Uri.parse(uriString)

    constructor(uri: Uri, mediaType: Int, epochSecondAdded: Long, storeId: Long? = null) : this(uri.toString(), mediaType, epochSecondAdded, storeId)

    override fun getPath(context: Context): String = getUri(context).path
}