package com.example.roadpro

import GasStation
import GasStationAdapter
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
            Places.initialize(requireContext().applicationContext, "TU_WSTAW_SWÓJ_KLUCZ_API")
        }
        placesClient = Places.createClient(requireContext())

        gasStationsRecyclerView = view.findViewById(R.id.gasStationsRecyclerView)
        gasStationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        loadNearbyGasStations()

        // Dodaj inicjalizację TextView pod mapą
        totalDistanceTextView = view.findViewById(R.id.totalDistanceTextView)
        loadAndDisplayTotalDistance()
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

        AlertDialog.Builder(requireContext())
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
            .show()
    }

    private fun loadNearbyGasStations() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val placeFields = listOf(
                    com.google.android.libraries.places.api.model.Place.Field.NAME,
                    com.google.android.libraries.places.api.model.Place.Field.LAT_LNG,
                    com.google.android.libraries.places.api.model.Place.Field.TYPES
                )
                val request = FindCurrentPlaceRequest.newInstance(placeFields)
                placesClient.findCurrentPlace(request)
                    .addOnSuccessListener { response ->
                        val stations = response.placeLikelihoods
                            .mapNotNull { pl ->
                                val place = pl.place
                                if (place.types?.contains(com.google.android.libraries.places.api.model.Place.Type.GAS_STATION) == true && place.latLng != null) {
                                    GasStation(place.name ?: "Stacja", place.latLng!!.latitude, place.latLng!!.longitude)
                                } else null
                            }
                        gasStationsRecyclerView.adapter = GasStationAdapter(stations)
                    }
            }
        }
    }

    // Dodaj tę funkcję do pobierania i wyświetlania sumy kilometrów
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
                totalDistanceTextView.text = "Łącznie przejechano: %.1f km".format(totalKm)
            }
            .addOnFailureListener {
                totalDistanceTextView.text = "Błąd ładowania kilometrów"
            }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadNearbyGasStations()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}