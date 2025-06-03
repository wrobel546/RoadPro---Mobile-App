package com.example.roadpro

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
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
        eventAdapter = EventAdapter(
            mutableListOf(),
            { eventToDelete -> confirmAndDeleteEvent(eventToDelete) },
            { eventToEdit -> showEditDialog(eventToEdit) } // Dodaj obsługę edycji
        )
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

    // Dodaj metodę do obsługi dialogu edycji wyjazdu (możesz skopiować z MadeRoutesFragment)
    private fun showEditDialog(event: Event) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_event, null)
        val nameEdit = dialogView.findViewById<EditText>(R.id.editEventName)
        val locationEdit = dialogView.findViewById<EditText>(R.id.editEventLocation)
        val startDateEdit = dialogView.findViewById<EditText>(R.id.editEventStartDate)
        val endDateEdit = dialogView.findViewById<EditText>(R.id.editEventEndDate)

        nameEdit.setText(event.name)
        locationEdit.setText(event.location)
        startDateEdit.setText(event.startDate)
        endDateEdit.setText(event.endDate)

        // Obsługa wyboru daty przez DatePicker
        startDateEdit.setOnClickListener {
            val cal = Calendar.getInstance()
            val parts = event.startDate.split("-")
            if (parts.size == 3) {
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
            DatePickerDialog(requireContext(), { _, y, m, d ->
                startDateEdit.setText(String.format("%04d-%02d-%02d", y, m + 1, d))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        endDateEdit.setOnClickListener {
            val cal = Calendar.getInstance()
            val parts = event.endDate.split("-")
            if (parts.size == 3) {
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
            DatePickerDialog(requireContext(), { _, y, m, d ->
                endDateEdit.setText(String.format("%04d-%02d-%02d", y, m + 1, d))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("Edytuj wyjazd")
            .setView(dialogView)
            .setPositiveButton("Zapisz", null)
            .setNegativeButton("Anuluj", null)
            .create()

        dialog.setOnShowListener {
            val saveBtn = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            saveBtn.setOnClickListener {
                val newName = nameEdit.text.toString().trim()
                val newLocation = locationEdit.text.toString().trim()
                val newStart = startDateEdit.text.toString().trim()
                val newEnd = endDateEdit.text.toString().trim()

                if (newName.isEmpty() || newLocation.isEmpty() || newStart.isEmpty() || newEnd.isEmpty()) {
                    Toast.makeText(requireContext(), "Wypełnij wszystkie pola!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (newStart > newEnd) {
                    Toast.makeText(requireContext(), "Data rozpoczęcia musi być wcześniejsza niż data zakończenia!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val user = FirebaseAuth.getInstance().currentUser ?: return@setOnClickListener
                // Pobierz wszystkie eventy użytkownika i sprawdź kolizje dat
                db.collection("events")
                    .whereEqualTo("userId", user.uid)
                    .get()
                    .addOnSuccessListener { result ->
                        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        val newStartDate = format.parse(newStart)
                        val newEndDate = format.parse(newEnd)
                        var overlap = false
                        for (doc in result) {
                            val other = doc.toObject(Event::class.java)
                            // Pomijamy aktualnie edytowany event
                            if (other.name == event.name && other.startDate == event.startDate && other.endDate == event.endDate) continue
                            val otherStart = format.parse(other.startDate)
                            val otherEnd = format.parse(other.endDate)
                            if (newStartDate != null && newEndDate != null && otherStart != null && otherEnd != null) {
                                // Sprawdź czy zakresy się nakładają
                                if (!(newEndDate.before(otherStart) || newStartDate.after(otherEnd))) {
                                    overlap = true
                                    break
                                }
                            }
                        }
                        if (overlap) {
                            Toast.makeText(requireContext(), "Wyjazd nachodzi na inny wyjazd!", Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }
                        // Jeśli nie ma kolizji, zaktualizuj event
                        db.collection("events")
                            .whereEqualTo("userId", user.uid)
                            .whereEqualTo("name", event.name)
                            .whereEqualTo("startDate", event.startDate)
                            .whereEqualTo("endDate", event.endDate)
                            .get()
                            .addOnSuccessListener { result2 ->
                                for (document in result2) {
                                    db.collection("events").document(document.id)
                                        .update(
                                            mapOf(
                                                "name" to newName,
                                                "location" to newLocation,
                                                "startDate" to newStart,
                                                "endDate" to newEnd
                                            )
                                        )
                                }
                                Toast.makeText(requireContext(), "Zapisano zmiany", Toast.LENGTH_SHORT).show()
                                loadEventsFromFirestore()
                                dialog.dismiss()
                            }
                    }
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
