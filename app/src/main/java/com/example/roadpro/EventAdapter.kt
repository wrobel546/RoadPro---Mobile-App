package com.example.roadpro

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EventAdapter(private var events: List<Event>) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val eventName: TextView = view.findViewById(R.id.eventNameTextView)
        val eventLocation: TextView = view.findViewById(R.id.eventLocationTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        holder.eventName.text = event.name
        holder.eventLocation.text = "Lokalizacja: ${event.location}"
    }

    override fun getItemCount(): Int = events.size

    fun updateList(newEvents: List<Event>) {
        events = newEvents
        notifyDataSetChanged()
    }
}
