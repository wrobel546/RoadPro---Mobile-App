package com.example.roadpro


import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.roadpro.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private var userEmail: String? = null
    private val db = FirebaseFirestore.getInstance()
    private val eventList = mutableListOf<Event>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()

        // Jeśli użytkownik nie jest zalogowany, przejdź do ekranu logowania
        if (firebaseAuth.currentUser == null) {
            val intent = Intent(this, SignInActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        // Odbierz adres e-mail użytkownika z Intent lub z Firebase
        userEmail = firebaseAuth.currentUser?.email
        println("Odebrano email w MainActivity: $userEmail") // Log

        // Ustaw domyślny fragment (HomeFragment)
        val homeFragment = HomeFragment().apply {
            arguments = Bundle().apply { putString("userEmail", userEmail) }
        }
        replaceFragment(homeFragment)

        // Obsługa Bottom Navigation Bar
        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.home -> HomeFragment().apply { arguments = Bundle().apply { putString("userEmail", userEmail) } }
                R.id.calendar -> CalendarFragment()
                R.id.madeRoates -> MadeRoutesFragment()
                R.id.statitics -> StatisticFragment()
                else -> null
            }
            fragment?.let { replaceFragment(it) }
            true
        }

        // FloatingActionButton - dodawanie wydarzeń z każdej zakładki
        binding.floatingActionButton.setOnClickListener {
            showAddEventDialog()
        }

        // Wczytaj wydarzenia z Firestore przy starcie
        loadEventsFromFirestore()
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.frame_layout, fragment)
            .commit()
    }

    // Dodaj tę metodę do MainActivity
    fun showAddEventDialog() {
        val addEventDialog = AddEventDialog()
        addEventDialog.setListener(object : AddEventDialog.AddEventDialogListener {
            override fun onEventAdded(eventName: String, location: String, startDate: String, endDate: String) {
                // Sprawdź konflikt dat
                if (isDateConflict(startDate, endDate)) {
                    Toast.makeText(this@MainActivity, "Wyjazd nakłada się z inną trasą!", Toast.LENGTH_LONG).show()
                    return
                }
                val color = generatePastelColor()
                val event = Event(
                    name = eventName,
                    location = location,
                    startDate = startDate,
                    endDate = endDate,
                    color = color,
                    done = 0,
                    payment = 0.0 // <-- dodaj ten argument, jeśli Event wymaga payment
                )
                saveEventToFirestore(event)
            }
        })
        addEventDialog.show(supportFragmentManager, "AddEventDialog")
    }

    // Dodaj tę funkcję do MainActivity
    private fun generatePastelColor(): Int {
        val hue = Random.nextInt(0, 360)
        val saturation = 0.4f + Random.nextFloat() * 0.2f // 0.4-0.6
        val value = 0.85f + Random.nextFloat() * 0.1f // 0.85-0.95
        val hsv = floatArrayOf(hue.toFloat(), saturation, value)
        return Color.HSVToColor(hsv)
    }

    // Sprawdzenie konfliktu dat
    private fun isDateConflict(newStart: String, newEnd: String): Boolean {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val newStartDate = format.parse(newStart)
        val newEndDate = format.parse(newEnd)
        for (event in eventList) {
            val start = format.parse(event.startDate)
            val end = format.parse(event.endDate)
            if (start != null && end != null && newStartDate != null && newEndDate != null) {
                // Jeśli zakresy się nakładają
                if (!(newEndDate.before(start) || newStartDate.after(end))) {
                    return true
                }
            }
        }
        return false
    }

    // Zapisz wydarzenie do Firestore i odśwież listę
    private fun saveEventToFirestore(event: Event) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val eventRef = db.collection("events").document()
        val data = hashMapOf(
            "userId" to user.uid,
            "name" to event.name,
            "location" to event.location,
            "startDate" to event.startDate,
            "endDate" to event.endDate,
            "color" to event.color,
            "done" to 0,
            "payment" to event.payment // <-- dodaj to pole, jeśli Event ma payment
        )
        eventRef.set(data)
            .addOnSuccessListener {
                Toast.makeText(this, "Wydarzenie zapisane!", Toast.LENGTH_SHORT).show()
                eventList.add(event)
                // Odśwież CalendarFragment jeśli jest widoczny
                val currentFragment = supportFragmentManager.findFragmentById(R.id.frame_layout)
                if (currentFragment is CalendarFragment) {
                    currentFragment.reloadEvents(eventList)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Błąd zapisu: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Wczytaj wydarzenia z Firestore przy starcie
    private fun loadEventsFromFirestore() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        db.collection("events")
            .whereEqualTo("userId", user.uid)
            .get()
            .addOnSuccessListener { result ->
                eventList.clear()
                for (document in result) {
                    val event = document.toObject(Event::class.java)
                    // Jeśli nie ma koloru (stare wydarzenia), przypisz losowy pastelowy
                    val color = document.getLong("color")?.toInt() ?: generatePastelColor()
                    eventList.add(event.copy(color = color))
                }
                // Odśwież CalendarFragment jeśli jest widoczny
                val currentFragment = supportFragmentManager.findFragmentById(R.id.frame_layout)
                if (currentFragment is CalendarFragment) {
                    currentFragment.reloadEvents(eventList)
                }
            }
    }

    // Dodaj tę metodę do MainActivity
    fun reloadEventsFromFirestore() {
        loadEventsFromFirestore()
    }
}
