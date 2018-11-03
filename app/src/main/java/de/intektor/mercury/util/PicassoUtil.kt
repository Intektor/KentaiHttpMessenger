package de.intektor.mercury.util

import android.content.Context
import com.squareup.picasso.Picasso
import de.intektor.mercury.media.ThumbnailUtil

object PicassoUtil {
    fun buildPicasso(context: Context): Picasso {
        return Picasso.Builder(context)
                .addRequestHandler(ThumbnailUtil.createExternalThumbnailRequestHandler(context))
                .build()

    }
}