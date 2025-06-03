package com.example.roadpro

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import java.util.Calendar

class AddEventDialog : DialogFragment() {
    interface AddEventDialogListener {
        fun onEventAdded(eventName: String, location: String, startDate: String, endDate: String)
    }

    private var listener: AddEventDialogListener? = null

    fun setListener(listener: AddEventDialogListener) {
        this.listener = listener
    }

    private lateinit var placesClient: PlacesClient

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater.inflate(R.layout.fragment_add_event, null)
        val locationEditText = view.findViewById<AutoCompleteTextView>(R.id.locationEditText)
        val eventNameEditText = view.findViewById<EditText>(R.id.eventNameEditText)
        val startDateEditText = view.findViewById<EditText>(R.id.startDateEditText)
        val endDateEditText = view.findViewById<EditText>(R.id.endDateEditText)
        val saveButton = view.findViewById<Button>(R.id.saveButton)

        // Inicjalizacja Places API (tylko raz w aplikacji, najlepiej w Application, ale tu dla uproszczenia)
        if (!Places.isInitialized()) {
            Places.initialize(requireContext().applicationContext, "TWÓJ_KLUCZ_API")
        }
        placesClient = Places.createClient(requireContext())

        val suggestions = ArrayList<String>()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, suggestions)
        locationEditText.setAdapter(adapter)
        locationEditText.threshold = 1 // po ilu znakach zaczyna podpowiadać

        locationEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                if (query.length < 2) return
                val request = FindAutocompletePredictionsRequest.builder()
                    .setQuery(query)
                    .setTypeFilter(com.google.android.libraries.places.api.model.TypeFilter.CITIES)
                    .build()
                placesClient.findAutocompletePredictions(request)
                    .addOnSuccessListener { response ->
                        suggestions.clear()
                        for (prediction: AutocompletePrediction in response.autocompletePredictions) {
                            suggestions.add(prediction.getFullText(null).toString())
                        }
                        adapter.notifyDataSetChanged()
                    }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        startDateEditText.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val dateStr = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                startDateEditText.setText(dateStr)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        endDateEditText.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val dateStr = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                endDateEditText.setText(dateStr)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        saveButton.setOnClickListener {
            val eventName = eventNameEditText.text.toString().trim()
            val location = locationEditText.text.toString().trim()
            val startDate = startDateEditText.text.toString().trim()
            val endDate = endDateEditText.text.toString().trim()

            if (eventName.isEmpty() || location.isEmpty() || startDate.isEmpty() || endDate.isEmpty()) {
                Toast.makeText(requireContext(), "Wypełnij wszystkie pola!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // TODO: Zapisz wydarzenie do bazy danych
            listener?.onEventAdded(eventName, location, startDate, endDate)

            dismiss()
        }

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("Dodaj wydarzenie")
            .setView(view)
            .setNegativeButton("Anuluj") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        return dialog
    }
}