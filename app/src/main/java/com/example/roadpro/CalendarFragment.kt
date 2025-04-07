package com.example.roadpro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.roadpro.databinding.FragmentCalendarBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val eventList = mutableListOf<Event>()
    private lateinit var eventAdapter: EventAdapter
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        eventAdapter = EventAdapter(eventList.toMutableList()) { eventToDelete ->
            confirmAndDeleteEvent(eventToDelete)
        }


        binding.eventsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.eventsRecyclerView.adapter = eventAdapter

        loadEventsFromFirestore()

        binding.calendarView.setOnDateChangeListener { _, year, month, day ->
            val selectedDate = "$year-${String.format("%02d", month + 1)}-${String.format("%02d", day)}"
            binding.textView2.text = "Wydarzenia na: $selectedDate"
            updateEventList(selectedDate)
        }
    }

    fun showAddEventDialog() {
        val addEventDialog = AddEventDialog()
        addEventDialog.setListener(object : AddEventDialog.AddEventDialogListener {
            override fun onEventAdded(eventName: String, location: String, startDate: String, endDate: String) {
                val event = Event(eventName, location, startDate, endDate)
                saveEventToFirestore(event)
                eventList.add(event)
                eventAdapter.updateList(eventList)
            }
        })
        addEventDialog.show(parentFragmentManager, "AddEventDialog")
    }

    private fun saveEventToFirestore(event: Event) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val eventRef = db.collection("events").document()

        val data = hashMapOf(
            "userId" to user.uid,
            "name" to event.name,
            "location" to event.location,
            "startDate" to event.startDate,
            "endDate" to event.endDate
        )

        eventRef.set(data)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Wydarzenie zapisane!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Błąd zapisu: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadEventsFromFirestore() {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        db.collection("events")
            .whereEqualTo("userId", user.uid)
            .get()
            .addOnSuccessListener { result ->
                eventList.clear()
                for (document in result) {
                    val event = document.toObject(Event::class.java)
                    eventList.add(event)
                }
                eventAdapter.updateList(eventList)
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Błąd ładowania: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateEventList(selectedDate: String) {
        val filteredEvents = eventList.filter { it.startDate == selectedDate }
        eventAdapter.updateList(filteredEvents)
    }

    private fun confirmAndDeleteEvent(event: Event) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Potwierdzenie")
        builder.setMessage("Czy na pewno chcesz usunąć to wydarzenie?")

        builder.setPositiveButton("Usuń") { _, _ ->
            deleteEventFromFirestore(event)
        }

        builder.setNegativeButton("Anuluj") { dialog, _ ->
            dialog.dismiss()
        }

        builder.create().show()
    }



    private fun deleteEventFromFirestore(event: Event) {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        db.collection("events")
            .whereEqualTo("userId", user.uid)
            .whereEqualTo("name", event.name)
            .whereEqualTo("startDate", event.startDate)
            .whereEqualTo("endDate", event.endDate)
            .get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    db.collection("events").document(document.id)
                        .delete()
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Usunięto wydarzenie", Toast.LENGTH_SHORT).show()
                            eventList.remove(event)
                            eventAdapter.updateList(eventList)
                        }
                        .addOnFailureListener {
                            Toast.makeText(requireContext(), "Błąd usuwania", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Nie znaleziono wydarzenia", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
