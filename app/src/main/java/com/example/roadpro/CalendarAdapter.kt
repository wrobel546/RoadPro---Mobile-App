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

    var eventColors: Map<String, Int> = emptyMap() // data: date -> color

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

        // Zawsze ustawiamy zaokrąglone tło
        holder.dayContainer.setBackgroundResource(R.drawable.bg_default)

        // Jeśli jest wyjazd tego dnia, nakładamy pastelowy kolor jako tint
        val color = eventColors[calendarDay.date]
        if (color != null) {
            holder.dayContainer.background.setTint(color)
        } else if (calendarDay.isSelected) {
            // Jeśli zaznaczony, możesz nałożyć np. ramkę lub inny efekt, ale tło zostaje zaokrąglone
            holder.dayContainer.background.setTint(0xFFE0E0E0.toInt()) // lub inny stonowany kolor
        } else {
            holder.dayContainer.background.setTintList(null)
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
