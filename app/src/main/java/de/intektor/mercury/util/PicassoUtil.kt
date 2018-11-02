package de.intektor.mercury.util

import android.content.Context
import com.squareup.picasso.Picasso
import de.intektor.mercury.task.ThumbnailUtil

object PicassoUtil {
    fun buildPicasso(context: Context): Picasso {
        return Picasso.Builder(context)
                .addRequestHandler(ThumbnailUtil.createThumbnailRequestHandler(context))
                .build()

    }
}