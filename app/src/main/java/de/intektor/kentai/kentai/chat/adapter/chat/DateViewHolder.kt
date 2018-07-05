package de.intektor.kentai.kentai.chat.adapter.chat

import android.text.format.DateUtils
import android.view.View
import android.widget.TextView
import de.intektor.kentai.R
import java.lang.Math.abs
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * @author Intektor
 */
class DateViewHolder(itemView: View, chatAdapter: ChatAdapter) : AbstractViewHolder(itemView, chatAdapter) {

    private val timeLabel: TextView = itemView.findViewById(R.id.chatDateLabel)

    companion object {
        private val weekday: SimpleDateFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        private val date: DateFormat = SimpleDateFormat.getDateInstance(SimpleDateFormat.SHORT)

        private val calendar = Calendar.getInstance()
    }

    override fun setComponent(component: Any) {
        component as DateInfo

        calendar.timeInMillis = System.currentTimeMillis()
        val dateNow = calendar.get(Calendar.DATE)

        calendar.timeInMillis = component.time
        val dateThen = calendar.get(Calendar.DATE)

        when {
            DateUtils.isToday(component.time) -> timeLabel.text = itemView.context.getString(R.string.chat_activity_today_label)
            abs(dateNow - dateThen) == 1 -> timeLabel.text = itemView.context.getString(R.string.chat_activity_yesterday_label)
            abs(dateNow - dateThen) < 4 -> timeLabel.text = weekday.format(Date(component.time))
            else -> timeLabel.text = date.format(Date(component.time))
        }
    }

    override fun registerForContextMenu(): Boolean = false
}