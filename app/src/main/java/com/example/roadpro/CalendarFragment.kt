package com.example.roadpro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.roadpro.databinding.FragmentCalendarBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val eventList = mutableListOf<Event>()
    private lateinit var eventAdapter: EventAdapter
    private lateinit var calendarAdapter: CalendarAdapter

    private var currentMonth: Int = Calendar.getInstance().get(Calendar.MONTH)
    private var currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        // Setup for events RecyclerView
        eventAdapter = EventAdapter(eventList) { eventToDelete ->
            confirmAndDeleteEvent(eventToDelete)
        }
        binding.eventsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.eventsRecyclerView.adapter = eventAdapter

        loadEventsFromFirestore()

        // Setup for calendar RecyclerView
        calendarAdapter = CalendarAdapter(emptyList()) { selectedDate ->
            updateEventList(selectedDate)
        }
        binding.calendarRecyclerView.layoutManager = GridLayoutManager(requireContext(), 7)
        binding.calendarRecyclerView.adapter = calendarAdapter

        setupMonthYearSelectors()
    }

    private fun setupMonthYearSelectors() {
        // Set up month spinner
        val months = resources.getStringArray(R.array.months)
        val monthAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, months)
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.monthSpinner.adapter = monthAdapter
        binding.monthSpinner.setSelection(currentMonth)

        binding.monthSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parentView: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentMonth = position
                loadCalendarData()
            }

            override fun onNothingSelected(parentView: AdapterView<*>?) {}
        }

        // Set up year spinner
        var currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = Array(currentYear - 2000 + 1) { (2000 + it).toString() }
        val yearAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, years)
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.yearSpinner.adapter = yearAdapter
        binding.yearSpinner.setSelection(currentYear - 2000)

        binding.yearSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parentView: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentYear = years[position].toInt()
                loadCalendarData()
            }

            override fun onNothingSelected(parentView: AdapterView<*>?) {}
        }

        // Set up arrow buttons to change months
        binding.previousMonthArrow.setOnClickListener {
            changeMonth(-1)
        }

        binding.nextMonthArrow.setOnClickListener {
            changeMonth(1)
        }
    }

    private fun changeMonth(delta: Int) {
        val calendar = Calendar.getInstance()
        calendar.set(currentYear, currentMonth, 1)
        calendar.add(Calendar.MONTH, delta)
        currentMonth = calendar.get(Calendar.MONTH)
        currentYear = calendar.get(Calendar.YEAR)

        // Update spinners to reflect the new month/year
        binding.monthSpinner.setSelection(currentMonth)
        binding.yearSpinner.setSelection(currentYear - 2000)

        loadCalendarData()
    }

    private fun loadCalendarData() {
        // Generate calendar days for the current month/year
        val days = generateCalendarDaysForMonth(currentYear, currentMonth)

        // Update the calendar adapter
        val calendarDays = days.map { day ->
            val eventCount = eventList.count { it.startDate == day.date }

            // Check if the day is within any event's range
            val isSelected = eventList.any { event ->
                isDateInRange(day.date, event.startDate, event.endDate)
            }

            CalendarDay(day.date, eventCount, isSelected)
        }

        calendarAdapter.updateData(calendarDays)
    }


    private fun isDateInRange(date: String, startDate: String, endDate: String): Boolean {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        return try {
            val day = format.parse(date)
            val start = format.parse(startDate)
            val end = format.parse(endDate)

            day != null && start != null && end != null &&
                    (day.after(start) || day == start) && (day.before(end) || day == end)
        } catch (e: Exception) {
            false
        }
    }


    private fun generateCalendarDaysForMonth(year: Int, month: Int): List<CalendarDay> {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, 1)

        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val days = mutableListOf<CalendarDay>()

        for (i in 1..daysInMonth) {
            val date = String.format("%04d-%02d-%02d", year, month + 1, i)
            days.add(CalendarDay(date, 0)) // 0 is a placeholder for event count
        }

        return days
    }

    private fun loadEventsFromFirestore() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "Nie jesteś zalogowany", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("events")
            .whereEqualTo("userId", user.uid)
            .get()
            .addOnSuccessListener { result ->
                eventList.clear()
                for (document in result) {
                    val event = document.toObject(Event::class.java)
                    eventList.add(event)
                }
                // Po załadowaniu nowych wydarzeń zaktualizuj kalendarz
                loadCalendarData()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Błąd ładowania: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private fun updateCalendarDays() {
        val days = generateCalendarDaysForMonth(currentYear, currentMonth)

        val calendarDays = days.map { day ->
            val eventCount = eventList.count { it.startDate == day.date }
            CalendarDay(day.date, eventCount)
        }

        calendarAdapter.updateData(calendarDays)
    }

    private fun updateEventList(selectedDate: String) {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val selected = format.parse(selectedDate)

        val filteredEvents = eventList.filter { event ->
            val start = format.parse(event.startDate)
            val end = format.parse(event.endDate)

            selected != null && start != null && end != null &&
                    !selected.before(start) && !selected.after(end)
        }

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
                            updateCalendarDays()
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

    fun showAddEventDialog() {
        val addEventDialog = AddEventDialog()
        addEventDialog.setListener(object : AddEventDialog.AddEventDialogListener {
            override fun onEventAdded(eventName: String, location: String, startDate: String, endDate: String) {
                val event = Event(eventName, location, startDate, endDate)
                saveEventToFirestore(event)
                eventList.add(event)
                eventAdapter.updateList(eventList)
                updateCalendarDays()
                loadCalendarData()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
