package com.example.roadpro

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EventAdapter(
    private var events: MutableList<Event>,
    private val onDeleteClicked: (Event) -> Unit
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    inner class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val eventName: TextView = view.findViewById(R.id.eventNameTextView)
        val eventLocation: TextView = view.findViewById(R.id.eventLocationTextView)
        val eventFrom: TextView = view.findViewById(R.id.eventFrom)
        val eventTo: TextView = view.findViewById(R.id.eventTo)
        val deleteButton: Button = view.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        holder.eventName.text = event.name
        holder.eventLocation.text = "Lokalizacja: ${event.location}"
        holder.eventFrom.text = "Od: ${event.startDate}"
        holder.eventTo.text = "Do: ${event.endDate}"

        holder.deleteButton.setOnClickListener {
            onDeleteClicked(event)
        }
    }

    override fun getItemCount(): Int = events.size

    fun updateList(newEvents: List<Event>) {
        events = newEvents.toMutableList()
        notifyDataSetChanged()
    }

    fun removeEvent(event: Event) {
        val index = events.indexOf(event)
        if (index != -1) {
            events.removeAt(index)
            notifyItemRemoved(index)
        }
    }
}

