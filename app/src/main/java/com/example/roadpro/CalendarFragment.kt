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
        eventAdapter = EventAdapter(mutableListOf()) { eventToDelete ->
            confirmAndDeleteEvent(eventToDelete)
        }
        binding.eventsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.eventsRecyclerView.adapter = eventAdapter

        // Nie trzymaj eventów w RAM, zawsze pobieraj z Firestore
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
                loadEventsFromFirestore()
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
                loadEventsFromFirestore()
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

        loadEventsFromFirestore()
    }

    private fun loadCalendarData(events: List<Event>) {
        // Generate calendar days for the current month/year
        val days = generateCalendarDaysForMonth(currentYear, currentMonth)

        // Update the calendar adapter
        val calendarDays = days.map { day ->
            val eventCount = events.count { it.startDate == day.date }
            val isSelected = events.any { event ->
                isDateInRange(day.date, event.startDate, event.endDate)
            }
            CalendarDay(day.date, eventCount, isSelected)
        }

        // Przygotuj mapę: data -> kolor (pierwszego eventu tego dnia)
        val colorMap = mutableMapOf<String, Int>()
        for (event in events) {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val start = format.parse(event.startDate)
            val end = format.parse(event.endDate)
            if (start != null && end != null) {
                var cal = Calendar.getInstance()
                cal.time = start
                while (!cal.time.after(end)) {
                    val dateStr = format.format(cal.time)
                    if (!colorMap.containsKey(dateStr)) colorMap[dateStr] = event.color
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                }
            }
        }
        calendarAdapter.eventColors = colorMap
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

    private fun loadEventsFromFirestore(onLoaded: ((List<Event>) -> Unit)? = null) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "Nie jesteś zalogowany", Toast.LENGTH_SHORT).show()
            onLoaded?.invoke(emptyList())
            return
        }

        db.collection("events")
            .whereEqualTo("userId", user.uid)
            .get()
            .addOnSuccessListener { result ->
                val events = result.map { it.toObject(Event::class.java) }
                eventAdapter.updateList(events)
                loadCalendarData(events)
                onLoaded?.invoke(events)
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Błąd ładowania: ${e.message}", Toast.LENGTH_SHORT).show()
                onLoaded?.invoke(emptyList())
            }
    }

    private fun updateCalendarDays() {
        // Zawsze pobieraj z Firestore
        loadEventsFromFirestore()
    }

    private fun updateEventList(selectedDate: String) {
        loadEventsFromFirestore { events ->
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val selected = format.parse(selectedDate)
            val filteredEvents = events.filter { event ->
                val start = format.parse(event.startDate)
                val end = format.parse(event.endDate)
                selected != null && start != null && end != null &&
                        !selected.before(start) && !selected.after(end)
            }
            eventAdapter.updateList(filteredEvents)
        }
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
                            // Po usunięciu odśwież widok z Firestore
                            loadEventsFromFirestore()
                            // Dodaj odświeżenie eventList w MainActivity
                            (activity as? MainActivity)?.reloadEventsFromFirestore()
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

    // Dodaj tę metodę do CalendarFragment
    fun reloadEvents(newEvents: List<Event>) {
        // Zamiast trzymać w RAM, odśwież z Firestore
        loadEventsFromFirestore()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
