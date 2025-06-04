package com.example.roadpro

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.ImageButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.recyclerview.widget.RecyclerView

class EventAdapter(
    private var events: MutableList<Event>,
    private val onDeleteClicked: (Event) -> Unit,
    private val onEditClicked: (Event) -> Unit
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    inner class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val eventName: TextView = view.findViewById(R.id.eventNameTextView)
        val eventLocation: TextView = view.findViewById(R.id.eventLocationTextView)
        val eventFrom: TextView = view.findViewById(R.id.eventFrom)
        val eventTo: TextView = view.findViewById(R.id.eventTo)
        val deleteButton: AppCompatImageButton = view.findViewById(R.id.deleteButton)
        val editButton: ImageButton = view.findViewById(R.id.editButton)
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
        holder.editButton.setOnClickListener {
            onEditClicked(event)
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

