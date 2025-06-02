package com.example.roadpro

import android.app.Activity
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Calendar

class AddEventDialog : DialogFragment() {

    interface AddEventDialogListener {
        fun onEventAdded(eventName: String, location: String, startDate: String, endDate: String)
    }

    private var listener: AddEventDialogListener? = null

    fun setListener(listener: AddEventDialogListener) {
        this.listener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = MaterialAlertDialogBuilder(it)
            val inflater = requireActivity().layoutInflater
            val view = inflater.inflate(R.layout.fragment_add_event, null)

            val eventNameEditText = view.findViewById<EditText>(R.id.eventNameEditText)
            val locationEditText = view.findViewById<EditText>(R.id.locationEditText)
            val startDateEditText = view.findViewById<EditText>(R.id.startDateEditText)
            val endDateEditText = view.findViewById<EditText>(R.id.endDateEditText)
            val saveButton = view.findViewById<Button>(R.id.saveButton)

            // Obsługa wyboru daty
            startDateEditText.setOnClickListener {
                showDatePickerDialog(startDateEditText)
            }

            endDateEditText.setOnClickListener {
                showDatePickerDialog(endDateEditText)
            }

            saveButton.setOnClickListener {
                val eventName = eventNameEditText.text.toString()
                val location = locationEditText.text.toString()
                val startDate = startDateEditText.text.toString()
                val endDate = endDateEditText.text.toString()

                // Walidacja danych
                if (eventName.isEmpty() || location.isEmpty() || startDate.isEmpty() || endDate.isEmpty()) {
                    Toast.makeText(requireContext(), "Wypełnij wszystkie pola!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (startDate > endDate) {
                    Toast.makeText(requireContext(), "Data rozpoczęcia musi być wcześniejsza niż data zakończenia!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Przekaż dane do CalendarFragment
                listener?.onEventAdded(eventName, location, startDate, endDate)

                // Zamknij dialog
                dismiss()
            }

            builder.setView(view)
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    private fun showDatePickerDialog(editText: EditText) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                val selectedDate = "$selectedYear-${String.format("%02d", selectedMonth + 1)}-${String.format("%02d", selectedDay)}"
                editText.setText(selectedDate)
            },
            year, month, day
        )

        datePickerDialog.show()
    }
}

class AddEventFragment : Fragment() {

    private lateinit var locationEditText: EditText
    private val AUTOCOMPLETE_REQUEST_CODE = 1001

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_add_event, container, false)
        locationEditText = view.findViewById(R.id.locationEditText)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Inicjalizacja Places tylko raz w aplikacji (np. w MainActivity lub Application)
        if (!Places.isInitialized()) {
            Places.initialize(requireContext().applicationContext, "TU_WSTAW_SWÓJ_KLUCZ_API")
        }

        locationEditText.isFocusable = false
        locationEditText.setOnClickListener {
            val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS)
            val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
                .build(requireContext())
            startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AUTOCOMPLETE_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            val place = Autocomplete.getPlaceFromIntent(data)
            locationEditText.setText(place.address)
        }
    }
}