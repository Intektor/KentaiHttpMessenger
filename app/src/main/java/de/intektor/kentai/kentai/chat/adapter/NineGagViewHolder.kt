package de.intektor.kentai.kentai.chat.adapter

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.AsyncTask
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import de.intektor.kentai.R
import de.intektor.kentai.kentai.android.addImageToGallery
import de.intektor.kentai.kentai.android.saveImageExternal9Gag
import de.intektor.kentai.kentai.android.saveImageExternalKentai
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.kentai.httpClient
import de.intektor.kentai.kentai.internalFile
import okhttp3.Request
import java.io.File
import java.net.URL

/**
 * @author Intektor
 */
class NineGagViewHolder(mView: View, chatAdapter: ChatAdapter) : ChatMessageViewHolder(mView, chatAdapter) {

    private val title = itemView.findViewById<TextView>(R.id.nineGagBubbleTitle)
    private val image = itemView.findViewById<ImageView>(R.id.nineGagBubbleImageView)

    companion object {
        val NINE_GAG_STORAGE_REFERENCE_FILE = "NINE_GAG_STORAGE"
    }

    override fun setComponent(component: Any) {
        component as ChatMessageWrapper

        val title = component.message.text.substringBefore("https://9gag.com/gag/")
        val gagId = component.message.text.substringAfter("https://9gag.com/gag/").substring(0, 7)

        val sharedPreferences = itemView.context.getSharedPreferences(NINE_GAG_STORAGE_REFERENCE_FILE, Context.MODE_PRIVATE)
        if (!sharedPreferences.getBoolean(sharedPreferenceGag(gagId, Type.IMAGE), false)) {
            CheckNineGagTask().execute(Triple(gagId, image, this.title))
        } else {
            val imageFile = File(Environment.getExternalStorageDirectory().toString() + "/Pictures/Kentai/$gagId.jpg")
            if (sharedPreferences.getBoolean(sharedPreferenceGag(gagId, Type.VIDEO), false)) {
                val videoFile = internalFile("nine_gag/$gagId.mp4")
                setNineGagClickListener(videoFile, image, Type.VIDEO)
            } else {
                setNineGagClickListener(imageFile, image, Type.IMAGE)
            }
            Picasso.with(image.context).load(imageFile).into(image)
        }

        this.title.text = title
        image.visibility = View.VISIBLE


        val layout = itemView.findViewById<LinearLayout>(R.id.bubble_layout) as LinearLayout
        val parentLayout = itemView.findViewById<LinearLayout>(R.id.bubble_layout_parent) as LinearLayout

        if (component.client) {
            parentLayout.gravity = Gravity.END
        } else {
            parentLayout.gravity = Gravity.START
        }
    }

    class CheckNineGagTask : AsyncTask<Triple<String, ImageView, TextView>, Unit, Four<ImageView, Type, String, TextView>>() {
        override fun doInBackground(vararg p0: Triple<String, ImageView, TextView>): Four<ImageView, Type, String, TextView> {
            val triple = p0[0]
            try {
                val request = Request.Builder()
                        .url("https://img-9gag-fun.9cache.com/photo/${triple.first}_460sv.mp4")
                        .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.code() == 200) {
                        return Four(triple.second, Type.VIDEO, triple.first, triple.third)
                    } else {
                        httpClient.newCall(Request.Builder().url("https://img-9gag-fun.9cache.com/photo/${triple.first}_700b.jpg").build()).execute().use { responseGif ->
                            if (responseGif.code() == 200) {
                                return Four(triple.second, Type.IMAGE, triple.first, triple.third)
                            }
                        }
                        return Four(triple.second, Type.NONE, triple.first, triple.third)
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
            return Four(triple.second, Type.NONE, triple.first, triple.third)
        }

        override fun onPostExecute(result: Four<ImageView, Type, String, TextView>) {
            val sharedPreferences = result.first.context.getSharedPreferences(NINE_GAG_STORAGE_REFERENCE_FILE, Context.MODE_PRIVATE)

            val nineGagTarget = object : Target {
                override fun onPrepareLoad(placeHolderDrawable: Drawable?) {

                }

                override fun onBitmapFailed(errorDrawable: Drawable?) {

                }

                override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom) {
                    val imageFile = File(Environment.getExternalStorageDirectory().toString() + "/Pictures/Kentai/${result.third}.jpg")

                    File(Environment.getExternalStorageDirectory().toString() + "/Kentai/").mkdirs()

                    try {
                        saveImageExternal9Gag(result.third, bitmap, result.first.context)
                        saveImageExternalKentai(result.third, bitmap, result.first.context)
                    } catch (t: Throwable) {
                        Toast.makeText(result.first.context, "Saving image failed!", Toast.LENGTH_SHORT).show()
                    }

                    val editor = sharedPreferences.edit()
                    editor.putBoolean(sharedPreferenceGag(result.third, result.second), true)
                    editor.apply()
                    result.first.setImageBitmap(bitmap)

                    if (result.second == Type.IMAGE) {
                        setNineGagClickListener(imageFile, result.first, Type.IMAGE)
                    } else if (result.second == Type.VIDEO) {
                        DownloadVideoTask().execute(Pair(result.third, result.first))
                    }
                }
            }

            if (result.second == Type.IMAGE) {
                Picasso.with(result.first.context).load(Uri.parse("https://img-9gag-fun.9cache.com/photo/${result.third}_700b.jpg")).into(nineGagTarget)
            } else if (result.second == Type.VIDEO) {
                Picasso.with(result.first.context).load(Uri.parse("https://img-9gag-fun.9cache.com/photo/${result.third}_460s.jpg")).into(nineGagTarget)
                result.fourth.text = result.first.context.getString(R.string.chat_activity_nine_gag_video_title, result.fourth.text)
            }
        }
    }

    class DownloadVideoTask : AsyncTask<Pair<String, ImageView>, Unit, Unit>() {
        override fun doInBackground(vararg p0: Pair<String, ImageView>) {
            val input = p0[0]

            val file = File(Environment.getExternalStorageDirectory().toString() + "/Pictures/Kentai/${input.first}.mp4")

            addImageToGallery(file.path, input.second.context)

            val connection = URL("https://img-9gag-fun.9cache.com/photo/${input.first}_460sv.mp4").openConnection()
            connection.connect()
            connection.getInputStream().copyTo(file.outputStream())
            val nineGagFile = File(Environment.getExternalStorageDirectory().toString() + "/Pictures/9GAG/${input.first}.mp4")
            file.inputStream().copyTo(nineGagFile.outputStream())

            addImageToGallery(nineGagFile.path, input.second.context)

            setNineGagClickListener(file, input.second, Type.VIDEO)
        }
    }

    enum class Type {
        IMAGE,
        VIDEO,
        NONE
    }

    class Four<out A, out B, out C, out D>(val first: A, val second: B, val third: C, val fourth: D)
}

fun sharedPreferenceGag(gagId: String, type: NineGagViewHolder.Type) = "$gagId/${when (type) {
    NineGagViewHolder.Type.IMAGE -> "i"
    NineGagViewHolder.Type.VIDEO -> "v"
    NineGagViewHolder.Type.NONE -> throw RuntimeException("Tried to get 9gag reference with a [NONE] Type!")
}
}"

fun setNineGagClickListener(file: File, imageView: ImageView, type: NineGagViewHolder.Type) {
    if (type == NineGagViewHolder.Type.IMAGE) {
        imageView.setOnClickListener {
            val showImage = Intent(Intent.ACTION_VIEW)
            showImage.setDataAndType(Uri.fromFile(file), "image/*")
            showImage.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            imageView.context.startActivity(showImage)
        }
    } else if (type == NineGagViewHolder.Type.VIDEO) {
        imageView.setOnClickListener {
            val showVideo = Intent(Intent.ACTION_VIEW)
            showVideo.setDataAndType(Uri.fromFile(file), "video/*")
            showVideo.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            imageView.context.startActivity(showVideo)
        }
    }
}