package com.example.roadpro

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.roadpro.databinding.ItemCalendarDayBinding

class CalendarAdapter(
    private var calendarDays: List<CalendarDay>,
    private val onDaySelected: (String) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

    // ViewHolder class for calendar days
    inner class CalendarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dayText: TextView = itemView.findViewById(R.id.dayText)
        val dayContainer: View = itemView.findViewById(R.id.dayContainer) // Kontener dnia (może to być layout, w którym jest tło)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_day, parent, false)
        return CalendarViewHolder(view)
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        val calendarDay = calendarDays[position]

        // Ustaw datę dnia
        holder.dayText.text = calendarDay.date.split("-")[2] // dzień (np. 1, 2, 3, itd.)

        // Ustaw tło w zależności od tego, czy dzień jest wybrany
        if (calendarDay.isSelected) {
            holder.dayContainer.setBackgroundResource(R.drawable.bg_selected) // lub inny sposób podświetlenia
        } else {
            holder.dayContainer.setBackgroundResource(R.drawable.bg_default)
        }

        // Kliknięcie na dzień
        holder.dayContainer.setOnClickListener {
            onDaySelected(calendarDay.date)
        }
    }

    override fun getItemCount(): Int = calendarDays.size

    fun updateData(newCalendarDays: List<CalendarDay>) {
        calendarDays = newCalendarDays
        notifyDataSetChanged()
    }
}
