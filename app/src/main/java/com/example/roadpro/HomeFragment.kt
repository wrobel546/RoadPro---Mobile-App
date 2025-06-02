package com.example.roadpro

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
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.roadpro.databinding.FragmentHomeBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.model.PlaceLikelihood
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var userNameTextView: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var placesClient: PlacesClient

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

        // Dodaj mapę do kontenera po id (nie używaj zmiennej mapContainer)
        var mapFragment = childFragmentManager.findFragmentById(R.id.mapContainer) as? SupportMapFragment
        if (mapFragment == null) {
            mapFragment = SupportMapFragment.newInstance()
            childFragmentManager.beginTransaction()
                .replace(R.id.mapContainer, mapFragment)
                .commitNow()
        }
        if (mapFragment == null) {
            println("MapFragment is still null after commitNow!")
        } else {
            mapFragment.getMapAsync(object : OnMapReadyCallback {
                override fun onMapReady(googleMap: GoogleMap) {
                    showCurrentLocationOnMap(googleMap)
                    showNearbyGasStations(googleMap)
                }
            })
        }
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

    private fun showCurrentLocationOnMap(googleMap: GoogleMap) {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Poproś o uprawnienia jeśli nie są przyznane
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
            return
        }
        googleMap.isMyLocationEnabled = true
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val currentLatLng = LatLng(location.latitude, location.longitude)
                googleMap.clear()
                // Usuń dodawanie markera (pinezki)
                // googleMap.addMarker(MarkerOptions().position(currentLatLng).title("Twoja lokalizacja"))
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 14f))
            } else {
                Toast.makeText(requireContext(), "Nie udało się pobrać lokalizacji", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showNearbyGasStations(googleMap: GoogleMap) {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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
                        for (placeLikelihood: PlaceLikelihood in response.placeLikelihoods) {
                            val place = placeLikelihood.place
                            // Filtruj tylko stacje benzynowe
                            if (place.types?.contains(com.google.android.libraries.places.api.model.Place.Type.GAS_STATION) == true) {
                                val latLng = place.latLng
                                if (latLng != null) {
                                    googleMap.addMarker(
                                        MarkerOptions()
                                            .position(latLng)
                                            .title(place.name)
                                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                                    )
                                }
                            }
                        }
                    }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            val mapFragment = childFragmentManager.findFragmentById(R.id.mapContainer) as? SupportMapFragment
            mapFragment?.getMapAsync { googleMap ->
                showCurrentLocationOnMap(googleMap)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}