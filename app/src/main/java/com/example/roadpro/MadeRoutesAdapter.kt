package com.example.roadpro

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread
import android.location.Location

class MadeRoutesAdapter(
    private var events: MutableList<Event>,
    private val onSettingsClicked: (Event) -> Unit,
    private val onMoneyClicked: (Event) -> Unit,
    function: () -> Unit
) : RecyclerView.Adapter<MadeRoutesAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val eventName: TextView = view.findViewById(R.id.eventNameTextView)
        val settingsButton: ImageButton = view.findViewById(R.id.settingsButton)
        val moneyButton: ImageButton = view.findViewById(R.id.moneyButton)
        val doneButton: Button = view.findViewById(R.id.doneButton)
        val paymentContainer: LinearLayout = view.findViewById(R.id.paymentContainer)
        val paymentValue: TextView = view.findViewById(R.id.paymentValue)
        val editPaymentButton: ImageButton = view.findViewById(R.id.editPaymentButton)
        val profitValue: TextView = view.findViewById(R.id.profitValue) // nowy TextView na zysk
        val expensesSumTextView: TextView = view.findViewById(R.id.expensesSumTextView) // dodane pole
        val daysLeftTextView: TextView = view.findViewById(R.id.daysLeftTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_made_route, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = events[position]
        holder.eventName.text = event.name

        // Wyświetl ile dni do wyjazdu w dedykowanym polu
        holder.daysLeftTextView.text = getDaysLeftText(event)

        // Kliknięcie w cały item - pokaż dialog z lokalizacją, datą wyjazdu i przyjazdu oraz mapą
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_route_info, null)
            val eventNameText = dialogView.findViewById<TextView>(R.id.eventNameTextView) // Dodaj ten wiersz
            val locationText = dialogView.findViewById<TextView>(R.id.locationTextView)
            val dateText = dialogView.findViewById<TextView>(R.id.dateTextView)
            val mapView = dialogView.findViewById<MapView>(R.id.mapView)
            val distanceText = dialogView.findViewById<TextView>(R.id.distanceTextView)
            val routeButton = dialogView.findViewById<Button>(R.id.routeButton)
            val openInMapsButton = dialogView.findViewById<Button>(R.id.openInMapsButton)

            eventNameText?.text = event.name // <-- ustaw nazwę wyjazdu w dialogu
            locationText.text = event.location
            dateText.text = "Wyjazd: ${event.startDate}\nPowrót: ${event.endDate}"

            mapView.onCreate(null)
            mapView.onResume()
            var googleMapRef: GoogleMap? = null
            mapView.getMapAsync(object : OnMapReadyCallback {
                override fun onMapReady(googleMap: GoogleMap) {
                    googleMapRef = googleMap
                    // Geokodowanie adresu na LatLng
                    val geocoder = Geocoder(context, Locale.getDefault())
                    try {
                        val addresses = geocoder.getFromLocationName(event.location, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0]
                            val latLng = LatLng(address.latitude, address.longitude)
                            googleMap.addMarker(MarkerOptions().position(latLng).title(event.location))
                            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 13f))
                        }
                    } catch (e: Exception) {
                        // Obsługa błędu geokodowania
                    }
                }
            })

            val dialog = AlertDialog.Builder(context)
//                .setTitle(event.name)
                .setView(dialogView)
                .setPositiveButton("OK", null)
                .create()

            dialog.setOnShowListener {
                val saveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                saveBtn.setTextColor(android.graphics.Color.BLACK)
                saveBtn.setOnClickListener {
                    dialog.dismiss()
                }
            }

            dialog.show()

            // Obsługa kliknięcia "Wyznacz trasę"
            routeButton.setOnClickListener {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                @SuppressLint("MissingPermission")
                fun getRoute() {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                        if (location != null) {
                            val geocoder = Geocoder(context, Locale.getDefault())
                            try {
                                val addresses = geocoder.getFromLocationName(event.location, 1)
                                if (!addresses.isNullOrEmpty()) {
                                    val dest = addresses[0]
                                    val originLat = location.latitude
                                    val originLng = location.longitude
                                    val destLat = dest.latitude
                                    val destLng = dest.longitude
                                    val apiKey = "AIzaSyD_j8LMpiIq3ftvQJUzPprukkNTzp-CD7g"
                                    val url = "https://maps.googleapis.com/maps/api/directions/json?origin=$originLat,$originLng&destination=$destLat,$destLng&mode=driving&key=$apiKey"
                                    thread {
                                        try {
                                            val result = URL(url).readText()
                                            val json = JSONObject(result)
                                            val routes = json.getJSONArray("routes")
                                            if (routes.length() > 0) {
                                                val overviewPolyline = routes.getJSONObject(0)
                                                    .getJSONObject("overview_polyline")
                                                    .getString("points")
                                                val points = decodePoly(overviewPolyline)
                                                val legs = routes.getJSONObject(0).getJSONArray("legs")
                                                val distanceMeters = if (legs.length() > 0)
                                                    legs.getJSONObject(0).getJSONObject("distance").getInt("value")
                                                else 0
                                                val distanceKm = distanceMeters / 1000.0

                                                Handler(Looper.getMainLooper()).post {
                                                    googleMapRef?.clear()
                                                    googleMapRef?.addMarker(MarkerOptions().position(LatLng(destLat, destLng)).title(event.location))
                                                    googleMapRef?.addMarker(MarkerOptions().position(LatLng(originLat, originLng)).title("Twoja lokalizacja"))
                                                    googleMapRef?.addPolyline(PolylineOptions().addAll(points).color(0xFF2196F3.toInt()).width(8f))
                                                    // Ustaw widok na całą trasę
                                                    if (points.isNotEmpty()) {
                                                        val builder = com.google.android.gms.maps.model.LatLngBounds.Builder()
                                                        for (point in points) {
                                                            builder.include(point)
                                                        }
                                                        val bounds = builder.build()
                                                        val padding = 80
                                                        googleMapRef?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
                                                    }
                                                    distanceText?.text = "Dystans: %.1f km".format(distanceKm)
                                                }
                                            } else {
                                                Handler(Looper.getMainLooper()).post {
                                                    Toast.makeText(context, "Nie znaleziono trasy", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Handler(Looper.getMainLooper()).post {
                                                Toast.makeText(context, "Błąd pobierania trasy", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Błąd geokodowania celu", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Nie można pobrać lokalizacji", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                getRoute()
            }

            openInMapsButton.setOnClickListener {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                @SuppressLint("MissingPermission")
                fun openMaps() {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                        if (location != null) {
                            val geocoder = Geocoder(context, Locale.getDefault())
                            try {
                                val addresses = geocoder.getFromLocationName(event.location, 1)
                                if (!addresses.isNullOrEmpty()) {
                                    val dest = addresses[0]
                                    val originLat = location.latitude
                                    val originLng = location.longitude
                                    val destLat = dest.latitude
                                    val destLng = dest.longitude
                                    val uri = Uri.parse(
                                        "https://www.google.com/maps/dir/?api=1" +
                                                "&origin=$originLat,$originLng" +
                                                "&destination=$destLat,$destLng" +
                                                "&travelmode=driving"
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                    intent.setPackage("com.google.android.apps.maps")
                                    if (intent.resolveActivity(context.packageManager) != null) {
                                        context.startActivity(intent)
                                    } else {
                                        Toast.makeText(context, "Brak aplikacji Google Maps", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Błąd geokodowania celu", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Nie można pobrać lokalizacji", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                openMaps()
            }
        }

        holder.settingsButton.setOnClickListener {
            onSettingsClicked(event)
        }
        holder.moneyButton.setOnClickListener {
            onMoneyClicked(event)
        }

        if (event.done == 1) {
            holder.doneButton.visibility = View.GONE
            holder.paymentContainer.visibility = View.VISIBLE
            val payment = event.payment ?: 0.0
            val sumFees = event.fees?.sumOf { it.amount } ?: 0.0
            val profit = payment - sumFees
            holder.paymentValue.text = "%.2f zł".format(payment)
            holder.expensesSumTextView.visibility = View.VISIBLE
            holder.expensesSumTextView.text = "Suma kosztów: %.2f zł".format(sumFees)
            holder.profitValue.visibility = View.VISIBLE
            holder.profitValue.text = "Zysk: %.2f zł".format(profit)
            holder.editPaymentButton.setOnClickListener {
                val context = holder.itemView.context
                val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_input_payment, null)
                val input = dialogView.findViewById<EditText>(R.id.paymentEditText)
                input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                input.setText((event.payment ?: 0.0).toString())
                val dialog = AlertDialog.Builder(context)
                    .setTitle("Podaj kwotę otrzymaną za przejazd")
                    .setView(dialogView)
                    .setPositiveButton("Zapisz", null) // obsługa ręczna
                    .setNegativeButton("Anuluj", null)
                    .create()

                dialog.setOnShowListener {
                    val saveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    val cancelBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    saveBtn.setTextColor(android.graphics.Color.BLACK)
                    cancelBtn.setTextColor(android.graphics.Color.BLACK)
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
        } else {
            holder.doneButton.visibility = View.VISIBLE
            holder.paymentContainer.visibility = View.GONE
            holder.expensesSumTextView.visibility = View.GONE
            holder.profitValue.visibility = View.GONE

            // Dodaj blokadę przycisku przed dniem powrotu
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val today = java.util.Calendar.getInstance().time
            val endDate = try { sdf.parse(event.endDate) } catch (e: Exception) { null }

            if (endDate != null && today.before(endDate)) {
                holder.doneButton.isEnabled = false
                holder.doneButton.alpha = 0.5f
                holder.doneButton.text = "Zrealizowano (dostępne od ${event.endDate})"
                holder.doneButton.setTextColor(android.graphics.Color.BLACK)
                holder.doneButton.setOnClickListener {
                    Toast.makeText(holder.itemView.context, "Możesz oznaczyć trasę jako zrealizowaną dopiero w dniu powrotu lub później.", Toast.LENGTH_SHORT).show()
                }
            } else {
                holder.doneButton.isEnabled = true
                holder.doneButton.alpha = 1.0f
                holder.doneButton.text = "Zrealizowano!"
                holder.doneButton.setTextColor(android.graphics.Color.BLACK)
                holder.doneButton.setOnClickListener {
                    // WALIDACJA STANU LICZNIKA
                    val startLicznik = event.StartLicznik
                    val koniecLicznik = event.KoniecLicznik
                    if (startLicznik == null || koniecLicznik == null || koniecLicznik <= startLicznik) {
                        Toast.makeText(holder.itemView.context, "Uzupełnij poprawnie stan licznika przed i po wyjeździe!", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    val context = holder.itemView.context
                    val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_input_payment, null)
                    val input = dialogView.findViewById<EditText>(R.id.paymentEditText)
                    input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                    input.hint = "Kwota otrzymana za przejazd"

                    val dialog = AlertDialog.Builder(context)
                        .setTitle("Podaj kwotę otrzymaną za przejazd")
                        .setView(dialogView)
                        .setPositiveButton("Zapisz", null) // obsługa ręczna
                        .setNegativeButton("Anuluj", null)
                        .create()

                    dialog.setOnShowListener {
                        val saveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        val cancelBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                        saveBtn.setTextColor(android.graphics.Color.BLACK)
                        cancelBtn.setTextColor(android.graphics.Color.BLACK)
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

// Funkcja dekodująca polyline Google
private fun decodePoly(encoded: String): List<LatLng> {
    val poly = ArrayList<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0

    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlat = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
        lat += dlat

        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlng = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
        lng += dlng

        val p = LatLng(
            lat / 1E5,
            lng / 1E5
        )
        poly.add(p)
    }
    return poly
}
