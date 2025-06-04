package com.example.roadpro

import GasStation
import GasStationAdapter
import GasStationWithDistance
import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.roadpro.databinding.FragmentHomeBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.model.PlaceLikelihood
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.net.URL
import kotlin.concurrent.thread

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var userNameTextView: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var placesClient: PlacesClient
    private lateinit var totalDistanceTextView: TextView
    private lateinit var gasStationsRecyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Odbierz adres e-mail z argumentów
        val userEmail = arguments?.getString("userEmail") ?: "example@gmail.com"
        println("Odebrano email w HomeFragment: $userEmail") // Log

        // Ustaw adres e-mail w TextView
        binding.emailEt.text = userEmail

        userNameTextView = view.findViewById(R.id.userNameTextView)
        loadUserName()

        val logoutBtn = view.findViewById<Button>(R.id.logoutBtn)
        logoutBtn?.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(requireContext(), SignInActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }

        val accountSettingsBtn = view.findViewById<Button>(R.id.accountSettingsBtn)
        accountSettingsBtn?.setOnClickListener {
            showAccountSettingsDialog()
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        // Inicjalizacja Places API
        if (!Places.isInitialized()) {
            Places.initialize(requireContext().applicationContext, "AIzaSyD_j8LMpiIq3ftvQJUzPprukkNTzp-CD7g")
        }
        placesClient = Places.createClient(requireContext())

        gasStationsRecyclerView = view.findViewById(R.id.gasStationsRecyclerView)
        gasStationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        loadNearbyGasStations()

        // Dodaj inicjalizację TextView pod mapą
        totalDistanceTextView = view.findViewById(R.id.totalDistanceTextView)
        loadAndDisplayTotalDistance()

        val gasStationsHeader = view.findViewById<TextView>(R.id.gasStationsHeader)
        gasStationsHeader.text = "Najbliższe stacje:"
    }

    private fun loadUserName() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(user.uid)
            .get()
            .addOnSuccessListener { doc ->
                val firstName = doc.getString("firstName") ?: ""
                val lastName = doc.getString("lastName") ?: ""
                userNameTextView.text = "$firstName $lastName"
            }
    }

    private fun showAccountSettingsDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_account_settings, null)
        val firstNameEdit = dialogView.findViewById<EditText>(R.id.firstNameEditText)
        val lastNameEdit = dialogView.findViewById<EditText>(R.id.lastNameEditText)

        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val userDoc = db.collection("users").document(user.uid)

        // Pobierz aktualne dane i ustaw w polach
        userDoc.get().addOnSuccessListener { doc ->
            firstNameEdit.setText(doc.getString("firstName") ?: "")
            lastNameEdit.setText(doc.getString("lastName") ?: "")
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Account Settings")
            .setView(dialogView)
            .setPositiveButton("Zapisz") { _, _ ->
                val firstName = firstNameEdit.text.toString()
                val lastName = lastNameEdit.text.toString()
                userDoc.set(mapOf("firstName" to firstName, "lastName" to lastName), SetOptions.merge())
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Zapisano dane", Toast.LENGTH_SHORT).show()
                        loadUserName() // odśwież wyświetlany tekst
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Błąd zapisu", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Anuluj", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.BLACK)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.BLACK)
        }
        dialog.show()
    }

    private fun loadNearbyGasStations() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "Brak uprawnień do lokalizacji!", Toast.LENGTH_SHORT).show()
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val apiKey = "AIzaSyD_j8LMpiIq3ftvQJUzPprukkNTzp-CD7g"
                val lat = location.latitude
                val lng = location.longitude
                val radius = 5000 // 5 km
                val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=$lat,$lng&radius=$radius&type=gas_station&key=$apiKey"
                thread {
                    try {
                        val response = URL(url).readText()
                        val json = JSONObject(response)
                        val results = json.getJSONArray("results")
                        val stations = mutableListOf<GasStationWithDistance>()
                        for (i in 0 until results.length()) {
                            val obj = results.getJSONObject(i)
                            val name = obj.getString("name")
                            val loc = obj.getJSONObject("geometry").getJSONObject("location")
                            val stationLat = loc.getDouble("lat")
                            val stationLng = loc.getDouble("lng")
                            val resultsLocation = Location("").apply {
                                latitude = stationLat
                                longitude = stationLng
                            }
                            val distance = location.distanceTo(resultsLocation) / 1000.0 // km
                            stations.add(GasStationWithDistance(name, stationLat, stationLng, distance))
                        }
                        // Sortuj po dystansie rosnąco
                        stations.sortBy { it.distanceKm }
                        if (isAdded && activity != null) {
                            activity?.runOnUiThread {
                                if (stations.isEmpty()) {
                                    Toast.makeText(requireContext(), "Brak stacji w promieniu 5 km!", Toast.LENGTH_SHORT).show()
                                }
                                gasStationsRecyclerView.adapter = GasStationAdapter(stations)
                            }
                        }
                    } catch (e: Exception) {
                        if (isAdded && activity != null) {
                            activity?.runOnUiThread {
                                Toast.makeText(requireContext(), "Błąd pobierania stacji: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            } else {
                Toast.makeText(requireContext(), "Nie można pobrać lokalizacji GPS!", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Błąd pobierania lokalizacji!", Toast.LENGTH_SHORT).show()
        }
    }

    // Dodaj brakującą funkcję do sumowania kilometrów
    private fun loadAndDisplayTotalDistance() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        db.collection("events")
            .whereEqualTo("userId", user.uid)
            .whereEqualTo("done", 1)
            .get()
            .addOnSuccessListener { result ->
                var totalKm = 0.0
                for (doc in result) {
                    val start = doc.getLong("StartLicznik")?.toDouble() ?: 0.0
                    val end = doc.getLong("KoniecLicznik")?.toDouble() ?: 0.0
                    if (end > start) {
                        totalKm += (end - start)
                    }
                }
                totalDistanceTextView.text = "Z aplikacją łącznie przejechano: %.1f km".format(totalKm)
            }
            .addOnFailureListener {
                totalDistanceTextView.text = "Błąd ładowania kilometrów"
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
