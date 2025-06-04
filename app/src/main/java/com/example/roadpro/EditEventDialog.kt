package com.example.roadpro

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import java.util.Calendar

class EditEventDialog : DialogFragment() {
    interface EditEventDialogListener {
        fun onEventUpdated(eventName: String, location: String, startDate: String, endDate: String, phoneNumber: String, sendSms: Boolean)
    }

    private var listener: EditEventDialogListener? = null

    fun setListener(listener: EditEventDialogListener) {
        this.listener = listener
    }

    private lateinit var placesClient: PlacesClient

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_edit_event, null)
        val eventNameEditText = view.findViewById<EditText>(R.id.eventNameEditText)
        val locationEditText = view.findViewById<EditText>(R.id.locationEditText)
        val phoneEditText = view.findViewById<EditText>(R.id.phoneEditText)
        val startDateEditText = view.findViewById<EditText>(R.id.startDateEditText)
        val endDateEditText = view.findViewById<EditText>(R.id.endDateEditText)
        val saveButton = view.findViewById<Button>(R.id.saveButton)
        val sendSmsCheckBox = view.findViewById<android.widget.CheckBox>(R.id.sendSmsCheckBox)

        // Inicjalizacja Places API (tylko raz w aplikacji, najlepiej w Application, ale tu dla uproszczenia)
        if (!Places.isInitialized()) {
            Places.initialize(requireContext().applicationContext, "AIzaSyD_j8LMpiIq3ftvQJUzPprukkNTzp-CD7g")
        }
        placesClient = Places.createClient(requireContext())

        startDateEditText.setOnClickListener {
            val cal = Calendar.getInstance()
            val datePicker = DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val dateStr = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                startDateEditText.setText(dateStr)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            datePicker.setOnShowListener {
                datePicker.getButton(DatePickerDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.BLACK)
                datePicker.getButton(DatePickerDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.BLACK)
            }
            datePicker.show()
        }

        endDateEditText.setOnClickListener {
            val cal = Calendar.getInstance()
            val datePicker = DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val dateStr = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                endDateEditText.setText(dateStr)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            datePicker.setOnShowListener {
                datePicker.getButton(DatePickerDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.BLACK)
                datePicker.getButton(DatePickerDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.BLACK)
            }
            datePicker.show()
        }

        saveButton.setOnClickListener {
            val eventName = eventNameEditText.text.toString().trim()
            val location = locationEditText.text.toString().trim()
            val startDate = startDateEditText.text.toString().trim()
            val endDate = endDateEditText.text.toString().trim()
            val phoneNumber = phoneEditText.text.toString().trim()
            val sendSms = sendSmsCheckBox.isChecked

            var valid = true

            if (eventName.isEmpty() || location.isEmpty() || startDate.isEmpty() || endDate.isEmpty()) {
                Toast.makeText(requireContext(), "Wypełnij wszystkie pola!", Toast.LENGTH_SHORT).show()
                valid = false
            }

            // Nowa walidacja: startDate nie może być po endDate
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val start: java.util.Date? = try { sdf.parse(startDate) } catch (e: Exception) { null }
            val end: java.util.Date? = try { sdf.parse(endDate) } catch (e: Exception) { null }
            if (start != null && end != null && start.after(end)) {
                Toast.makeText(requireContext(), "Data rozpoczęcia musi być wcześniejsza lub równa dacie zakończenia!", Toast.LENGTH_LONG).show()
                startDateEditText.setBackgroundColor(0x30FF0000)
                endDateEditText.setBackgroundColor(0x30FF0000)
                valid = false
            } else {
                startDateEditText.setBackgroundColor(0x00000000)
                endDateEditText.setBackgroundColor(0x00000000)
            }

            // Sprawdź konflikt dat przez MainActivity
            val conflict = (activity as? MainActivity)?.isDateConflict(startDate, endDate) == true
            if (conflict) {
                startDateEditText.setBackgroundColor(0x30FF0000) // półprzezroczysty czerwony
                endDateEditText.setBackgroundColor(0x30FF0000)
                Toast.makeText(requireContext(), "Wyjazd nakłada się z inną trasą!", Toast.LENGTH_LONG).show()
                valid = false
            } else if (valid) {
                startDateEditText.setBackgroundColor(0x00000000)
                endDateEditText.setBackgroundColor(0x00000000)
            }

            if (!valid) return@setOnClickListener

            listener?.onEventUpdated(eventName, location, startDate, endDate, phoneNumber, sendSms)

            dismiss()
        }

        // --- blokada emotek ---
        val noEmojiFilter = android.text.InputFilter { source, _, _, _, _, _ ->
            for (char in source) {
                val type = Character.getType(char)
                // Blokuj znaki typu SURROGATE, OTHER_SYMBOL (emotki, niektóre znaki specjalne)
                if (type == Character.SURROGATE.toInt() || type == Character.OTHER_SYMBOL.toInt()) {
                    return@InputFilter ""
                }
            }
            null
        }
        eventNameEditText.filters = arrayOf(noEmojiFilter)
        locationEditText.filters = arrayOf(noEmojiFilter)
        phoneEditText.filters = arrayOf(noEmojiFilter)
        // --- koniec blokady emotek ---

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("Edytuj wydarzenie")
            .setView(view)
            .setNegativeButton("Anuluj") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.BLACK)
        }
        return dialog
    }
}