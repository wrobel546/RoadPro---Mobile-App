package com.example.roadpro

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.roadpro.databinding.FragmentCalendarBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val eventList = mutableListOf<Event>()
    private lateinit var eventAdapter: EventAdapter
    private val db = FirebaseFirestore.getInstance() // Inicjalizowanie Firestore

    private val eventDates = mutableListOf<String>()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        eventAdapter = EventAdapter(eventList)
        binding.eventsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.eventsRecyclerView.adapter = eventAdapter

        // Ładuj wydarzenia tylko dla zalogowanego użytkownika
        loadEventsFromFirestore()

        binding.calendarView.setOnDateChangeListener { _, year, month, day ->
            val selectedDate = "$year-${String.format("%02d", month + 1)}-${String.format("%02d", day)}"
            binding.textView2.text = "Wydarzenia na: $selectedDate"
            updateEventList(selectedDate)
        }
    }

    // Metoda do wywołania z MainActivity
    fun showAddEventDialog() {
        val addEventDialog = AddEventDialog()
        addEventDialog.setListener(object : AddEventDialog.AddEventDialogListener {
            override fun onEventAdded(eventName: String, location: String, startDate: String, endDate: String) {
                val event = Event(eventName, location, startDate, endDate)
                saveEventToFirestore(event) // Zapisz wydarzenie w Firestore
                eventList.add(event)
                eventAdapter.notifyDataSetChanged()
            }
        })
        addEventDialog.show(parentFragmentManager, "AddEventDialog")
    }

    // Metoda zapisywania wydarzenia do Firestore
    private fun saveEventToFirestore(event: Event) {
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: return // Jeśli użytkownik nie jest zalogowany, nie zapisuj

        val eventRef = db.collection("events").document() // Nowy dokument w kolekcji "events"
        eventRef.set(
            hashMapOf(
                "userId" to userId, // Zapisz ID użytkownika
                "name" to event.name,
                "location" to event.location,
                "startDate" to event.startDate,
                "endDate" to event.endDate
            )
        ).addOnSuccessListener {
            Toast.makeText(requireContext(), "Wydarzenie zapisane w bazie!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { exception ->
            Toast.makeText(requireContext(), "Błąd zapisu: ${exception.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Ładowanie wydarzeń tylko dla zalogowanego użytkownika
    private fun loadEventsFromFirestore() {
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: return // Jeśli użytkownik nie jest zalogowany, nic nie ładuj

        db.collection("events")
            .whereEqualTo("userId", userId) // Filtrowanie po ID użytkownika
            .get()
            .addOnSuccessListener { result ->
                eventList.clear() // Wyczyszczenie listy
                for (document in result) {
                    val event = document.toObject(Event::class.java)
                    eventList.add(event)
                }
                eventAdapter.notifyDataSetChanged()

            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "Błąd ładowania wydarzeń: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateEventList(selectedDate: String) {
        val filteredEvents = eventList.filter { it.startDate == selectedDate }
        eventAdapter.updateList(filteredEvents)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


}


