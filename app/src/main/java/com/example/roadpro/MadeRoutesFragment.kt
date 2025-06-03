package com.example.roadpro

import android.app.AlertDialog
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.Button

class MadeRoutesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MadeRoutesAdapter
    private val db = FirebaseFirestore.getInstance()
    private var showDone: Boolean = false // false = niezrealizowane, true = zrealizowane

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_made_routes, container, false)
        recyclerView = view.findViewById(R.id.madeRoutesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = MadeRoutesAdapter(
            mutableListOf(),
            { event -> showEditDialog(event) },
            { event -> showFeesDialog(event) },
            { reloadRoutes() }
        )
        recyclerView.adapter = adapter

        // Zakładki
        val tabNotDone = view.findViewById<Button>(R.id.tabNotDone)
        val tabDone = view.findViewById<Button>(R.id.tabDone)

        tabNotDone.setOnClickListener {
            showDone = false
            reloadRoutes()
            tabNotDone.isEnabled = false
            tabDone.isEnabled = true
        }
        tabDone.setOnClickListener {
            showDone = true
            reloadRoutes()
            tabNotDone.isEnabled = true
            tabDone.isEnabled = false
        }
        // Domyślnie aktywna zakładka: niezrealizowane
        tabNotDone.isEnabled = false
        tabDone.isEnabled = true

        loadRoutes()
        return view
    }

    fun reloadRoutes() {
        loadRoutes()
    }

    private fun loadRoutes() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "Nie jesteś zalogowany", Toast.LENGTH_SHORT).show()
            adapter.updateList(emptyList())
            return
        }
        db.collection("events")
            .whereEqualTo("userId", user.uid)
            .get()
            .addOnSuccessListener { result ->
                val routes = result.map { doc ->
                    val event = doc.toObject(Event::class.java)
                    // Jeśli nie ma koloru (stare wydarzenia), przypisz losowy pastelowy
                    val color = doc.getLong("color")?.toInt() ?: 0xFFBDBDBD.toInt()
                    event.copy(color = color)
                }
                // Filtrowanie po statusie done
                val filtered = routes.filter { (it.done ?: 0) == if (showDone) 1 else 0 }
                adapter.updateList(filtered)
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Błąd ładowania tras", Toast.LENGTH_SHORT).show()
                adapter.updateList(emptyList())
            }
    }

    private fun showEditDialog(event: Event) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_mileage, null)
        val beforeEdit = dialogView.findViewById<EditText>(R.id.mileageBeforeEditText)
        val afterEdit = dialogView.findViewById<EditText>(R.id.mileageAfterEditText)

        beforeEdit.setText(event.StartLicznik.toString())
        afterEdit.setText(event.KoniecLicznik.toString())

        val feesList = mutableListOf<Fee>()
        // TODO: Inicjalizuj feesList danymi z event.fees jeśli istnieje

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Edytuj wyjazd")
            .setView(dialogView)
            .setPositiveButton("Zapisz", null) // obsłużymy kliknięcie ręcznie
            .setNegativeButton("Anuluj", null)
            .create()

        dialog.setOnShowListener {
            val saveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveBtn.setOnClickListener {
                val before = beforeEdit.text.toString().toLongOrNull() ?: 0L
                val after = afterEdit.text.toString().toLongOrNull() ?: 0L
                if (after <= before) {
                    afterEdit.setBackgroundColor(0x30FF0000) // półprzezroczysty czerwony
                    Toast.makeText(requireContext(), "Stan licznika po wyjeździe musi być większy niż przed wyjazdem!", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                } else {
                    afterEdit.setBackgroundColor(0x00000000) // przezroczyste tło (reset)
                }
                // Zapisz do Firestore
                val user = FirebaseAuth.getInstance().currentUser ?: return@setOnClickListener
                db.collection("events")
                    .whereEqualTo("userId", user.uid)
                    .whereEqualTo("name", event.name)
                    .whereEqualTo("startDate", event.startDate)
                    .whereEqualTo("endDate", event.endDate)
                    .get()
                    .addOnSuccessListener { result ->
                        for (document in result) {
                            // Przygotuj dane do zapisania
                            val feesToSave = feesList.map { mapOf("name" to it.name, "amount" to it.amount) }
                            db.collection("events").document(document.id)
                                .update(
                                    mapOf(
                                        "StartLicznik" to before,
                                        "KoniecLicznik" to after,
                                        "fees" to feesToSave
                                    )
                                )
                        }
                        Toast.makeText(requireContext(), "Zapisano zmiany", Toast.LENGTH_SHORT).show()
                        reloadRoutes()
                        dialog.dismiss()
                    }
            }
        }
        dialog.show()
    }

    private fun showFeesDialog(event: Event) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_fees, null)
        val feesContainer = dialogView.findViewById<LinearLayout>(R.id.feesContainer)
        val addFeeButton = dialogView.findViewById<ImageButton>(R.id.addFeeButton)

        // Załóż, że event ma pole fees: MutableList<Fee>
        val tempFees = event.fees?.toMutableList() ?: mutableListOf()

        fun refreshFeesList() {
            feesContainer.removeAllViews()
            tempFees.forEachIndexed { index, fee ->
                val feeView = LayoutInflater.from(requireContext()).inflate(R.layout.item_fee, feesContainer, false)
                feeView.findViewById<TextView>(R.id.feeNameTextView).text = fee.name
                feeView.findViewById<TextView>(R.id.feeAmountTextView).text = "%.2f zł".format(fee.amount)
                val deleteBtn = feeView.findViewById<ImageButton>(R.id.deleteFeeButton)
                deleteBtn.setOnClickListener {
                    tempFees.removeAt(index)
                    refreshFeesList()
                }
                feesContainer.addView(feeView)
            }
        }
        refreshFeesList()

        addFeeButton.setOnClickListener {
            // Otwórz dialog do wpisania nowej opłaty
            val feeEditView = LayoutInflater.from(requireContext()).inflate(R.layout.item_fee_edit, null)
            val nameEdit = feeEditView.findViewById<EditText>(R.id.feeNameEditText)
            val amountEdit = feeEditView.findViewById<EditText>(R.id.feeAmountEditText)

            AlertDialog.Builder(requireContext())
                .setTitle("Dodaj opłatę")
                .setView(feeEditView)
                .setPositiveButton("Dodaj") { _, _ ->
                    val name = nameEdit.text.toString().trim()
                    val amount = amountEdit.text.toString().toDoubleOrNull() ?: 0.0
                    if (name.isNotEmpty()) {
                        tempFees.add(Fee(name, amount))
                        refreshFeesList()
                    } else {
                        Toast.makeText(requireContext(), "Podaj nazwę opłaty", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Anuluj", null)
                .show()
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Opłaty")
            .setView(dialogView)
            .setPositiveButton("Zapisz") { _, _ ->
                // Zapisz tempFees do event.fees i do bazy
                val db = FirebaseFirestore.getInstance()
                db.collection("events")
                    .whereEqualTo("name", event.name)
                    .whereEqualTo("startDate", event.startDate)
                    .whereEqualTo("endDate", event.endDate)
                    .get()
                    .addOnSuccessListener { result ->
                        for (document in result) {
                            db.collection("events").document(document.id)
                                .update("fees", tempFees.map { fee -> mapOf("name" to fee.name, "amount" to fee.amount) })
                        }
                        reloadRoutes()
                    }
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }
}

// Upewnij się, że masz klasę Fee:
data class Fee(
    val name: String = "",
    val amount: Double = 0.0
)