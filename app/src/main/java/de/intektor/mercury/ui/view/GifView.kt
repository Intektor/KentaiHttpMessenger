package de.intektor.mercury.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Movie
import android.graphics.Paint
import android.os.AsyncTask
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import java.io.File

/**
 * @author Intektor
 */
class GifView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var movie: Movie? = null

    private var movieStart: Long = 0

    private val paint = Paint()

    fun setGif(file: File) {
        LoadGifTask(file) { movie ->
            this.movie = movie
            invalidate()
        }.execute()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(0)

        val m = movie ?: return

        paint.isAntiAlias = true

        val now = SystemClock.uptimeMillis()

        if (movieStart == 0L) {
            movieStart = now
        }

        val duration = if (m.duration() == 0) 1000 else m.duration()

        val relTime = ((now - movieStart) % duration).toInt()
        m.setTime(relTime)
        m.draw(canvas, 0f, 0f, paint)

        invalidate()
    }

    private class LoadGifTask(private val file: File, private val callback: (Movie) -> Unit) : AsyncTask<Unit, Unit, Movie>() {
        override fun doInBackground(vararg params: Unit?): Movie {
            return Movie.decodeFile(file.path)
        }

        override fun onPostExecute(result: Movie) {
            callback(result)
        }
    }
}