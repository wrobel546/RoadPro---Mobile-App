package com.example.roadpro

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class MadeRoutesAdapter(
    private var events: MutableList<Event>,
    private val onSettingsClicked: (Event) -> Unit,
    private val onMoneyClicked: (Event) -> Unit,
    function: () -> Unit
) : RecyclerView.Adapter<MadeRoutesAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val eventName: TextView = view.findViewById(R.id.eventNameTextView)
        val eventLocation: TextView = view.findViewById(R.id.eventLocationTextView)
        val eventFrom: TextView = view.findViewById(R.id.eventFrom)
        val eventTo: TextView = view.findViewById(R.id.eventTo)
        val settingsButton: ImageButton = view.findViewById(R.id.settingsButton)
        val moneyButton: ImageButton = view.findViewById(R.id.moneyButton)
        val routeButton: Button = view.findViewById(R.id.routeButton)
        val doneButton: Button = view.findViewById(R.id.doneButton)
        val paymentContainer: LinearLayout = view.findViewById(R.id.paymentContainer)
        val paymentValue: TextView = view.findViewById(R.id.paymentValue)
        val editPaymentButton: ImageButton = view.findViewById(R.id.editPaymentButton)
        // Dodaj pole do wyświetlania liczby dni do wyjazdu
        val daysLeftTextView: TextView = view.findViewById(R.id.daysLeftTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_made_route, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = events[position]
        holder.eventName.text = event.name
        holder.eventLocation.text = "Lokalizacja: ${event.location}"
        holder.eventFrom.text = "Od: ${event.startDate}"
        holder.eventTo.text = "Do: ${event.endDate}"

        // Wyświetl ile dni do wyjazdu
        holder.daysLeftTextView.text = getDaysLeftText(event)

        // Kliknięcie w cały item - pokaż dialog z lokalizacją, datą wyjazdu i przyjazdu
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val message = "Lokalizacja: ${event.location}\nData wyjazdu: ${event.startDate}\nData przyjazdu: ${event.endDate}"
            AlertDialog.Builder(context)
                .setTitle(event.name)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }

        holder.settingsButton.setOnClickListener {
            onSettingsClicked(event)
        }
        holder.moneyButton.setOnClickListener {
            onMoneyClicked(event)
        }
        holder.routeButton.setOnClickListener {
            val destination = event.location
            val uri = "https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(destination)}&travelmode=driving"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            intent.setPackage("com.google.android.apps.maps")
            if (intent.resolveActivity(holder.itemView.context.packageManager) != null) {
                holder.itemView.context.startActivity(intent)
            } else {
                Toast.makeText(holder.itemView.context, "Brak aplikacji Google Maps", Toast.LENGTH_SHORT).show()
            }
        }

        // Popraw: sprawdzanie i ustawianie przycisku "Zrealizowana!"
        if (event.done == 1) {
            holder.doneButton.visibility = View.GONE
            holder.paymentContainer.visibility = View.VISIBLE
            holder.paymentValue.text = "%.2f zł".format(event.payment ?: 0.0)
            holder.editPaymentButton.setOnClickListener {
                val context = holder.itemView.context
                val input = EditText(context)
                input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                input.setText((event.payment ?: 0.0).toString())
                AlertDialog.Builder(context)
                    .setTitle("Edytuj kwotę za przejazd")
                    .setView(input)
                    .setPositiveButton("Zapisz") { _, _ ->
                        val payment = input.text.toString().toDoubleOrNull() ?: 0.0
                        val db = FirebaseFirestore.getInstance()
                        db.collection("events")
                            .whereEqualTo("name", event.name)
                            .whereEqualTo("startDate", event.startDate)
                            .whereEqualTo("endDate", event.endDate)
                            .get()
                            .addOnSuccessListener { result ->
                                for (document in result) {
                                    db.collection("events").document(document.id)
                                        .update("payment", payment)
                                }
                                event.payment = payment
                                notifyItemChanged(position)
                            }
                    }
                    .setNegativeButton("Anuluj", null)
                    .show()
            }
        } else {
            holder.doneButton.visibility = View.VISIBLE
            holder.paymentContainer.visibility = View.GONE
            holder.doneButton.setOnClickListener {
                // WALIDACJA STANU LICZNIKA
                val startLicznik = event.StartLicznik
                val koniecLicznik = event.KoniecLicznik
                if (startLicznik == null || koniecLicznik == null || koniecLicznik <= startLicznik) {
                    Toast.makeText(holder.itemView.context, "Uzupełnij poprawnie stan licznika przed i po wyjeździe!", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val context = holder.itemView.context
                val input = EditText(context)
                input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                input.hint = "Kwota otrzymana za przejazd"

                val dialog = AlertDialog.Builder(context)
                    .setTitle("Podaj kwotę otrzymaną za przejazd")
                    .setView(input)
                    .setPositiveButton("Zapisz", null) // obsługa ręczna
                    .setNegativeButton("Anuluj", null)
                    .create()

                dialog.setOnShowListener {
                    val saveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    saveBtn.setOnClickListener {
                        val payment = input.text.toString().toDoubleOrNull()
                        if (payment == null || payment <= 0.0) {
                            input.setBackgroundColor(0x30FF0000) // półprzezroczysty czerwony
                            Toast.makeText(context, "Podaj poprawną kwotę!", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        } else {
                            input.setBackgroundColor(0x00000000) // reset tła
                        }
                        val db = FirebaseFirestore.getInstance()
                        db.collection("events")
                            .whereEqualTo("name", event.name)
                            .whereEqualTo("startDate", event.startDate)
                            .whereEqualTo("endDate", event.endDate)
                            .get()
                            .addOnSuccessListener { result ->
                                for (document in result) {
                                    db.collection("events").document(document.id)
                                        .update(mapOf("done" to 1, "payment" to payment))
                                }
                                (holder.itemView.context as? androidx.fragment.app.FragmentActivity)?.let { activity ->
                                    val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame_layout)
                                    if (fragment is MadeRoutesFragment) {
                                        fragment.reloadRoutes()
                                    }
                                }
                                dialog.dismiss()
                            }
                    }
                }
                dialog.show()
            }
        }
    }

    // Funkcja pomocnicza do wyliczania liczby dni do wyjazdu
    private fun getDaysLeftText(event: Event): String {
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val today = java.util.Calendar.getInstance()
            val start = sdf.parse(event.startDate)
            val end = sdf.parse(event.endDate)
            if (start == null || end == null) return ""
            val now = today.time
            return when {
                now.before(start) -> {
                    val diff = ((start.time - now.time) / (1000 * 60 * 60 * 24)).toInt()
                    if (diff == 0) "Wyjazd dziś" else "Za $diff dni"
                }
                now.after(end) -> "Wyjazd zakończony"
                else -> "Wyjazd trwa"
            }
        } catch (e: Exception) {
            return ""
        }
    }

    override fun getItemCount(): Int = events.size

    fun updateList(newEvents: List<Event>) {
        events = newEvents.toMutableList()
        notifyDataSetChanged()
    }
}

// Upewnij się, że w item_made_route.xml istnieje TextView o id daysLeftTextView
