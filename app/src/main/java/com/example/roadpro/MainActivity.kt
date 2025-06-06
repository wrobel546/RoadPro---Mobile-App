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

    // Lista pastelowych fioletowych kolorów
    private val pastelPurpleColors = listOf(
        Color.parseColor("#B39DDB"), // jasny fiolet
        Color.parseColor("#CE93D8"), // pastelowy fiolet
        Color.parseColor("#D1C4E9"), // bardzo jasny fiolet
        Color.parseColor("#E1BEE7"), // pastelowy różowo-fioletowy
        Color.parseColor("#9575CD")  // ciemniejszy pastelowy fiolet
    )
    private var lastColorIndex = -1

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


    fun showAddEventDialog() {
        val addEventDialog = AddEventDialog()
        addEventDialog.setListener(object : AddEventDialog.AddEventDialogListener {
            override fun onEventAdded(
                eventName: String,
                location: String,
                startDate: String,
                endDate: String,
                phoneNumber: String,
                sendSms: Boolean
            ) {
                // Sprawdź konflikt dat
                if (isDateConflict(startDate, endDate)) {
                    Toast.makeText(this@MainActivity, "Wyjazd nakłada się z inną trasą!", Toast.LENGTH_LONG).show()
                    return
                }
                val color = generatePastelPurpleColor() // Użyj nowej funkcji
                val event = Event(
                    name = eventName,
                    location = location,
                    startDate = startDate,
                    endDate = endDate,
                    color = color,
                    done = 0,
                    payment = 0.0
                )
                saveEventToFirestore(event, phoneNumber, eventName, location, startDate, endDate, sendSms)
            }
        })
        addEventDialog.show(supportFragmentManager, "AddEventDialog")
    }

    // Funkcja losująca jeden z powyższych kolorów, nie powtarza dwa razy pod rząd
    private fun generatePastelPurpleColor(): Int {
        var idx: Int
        do {
            idx = Random.nextInt(pastelPurpleColors.size)
        } while (idx == lastColorIndex && pastelPurpleColors.size > 1)
        lastColorIndex = idx
        return pastelPurpleColors[idx]
    }

    // Sprawdzenie konfliktu dat
    fun isDateConflict(newStart: String, newEnd: String): Boolean {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val newStartDate = format.parse(newStart)
        val newEndDate = format.parse(newEnd)
        for (event in eventList) {
            val start = format.parse(event.startDate)
            val end = format.parse(event.endDate)
            if (start != null && end != null && newStartDate != null && newEndDate != null) {
                if (!(newEndDate.before(start) || newStartDate.after(end))) {
                    return true
                }
            }
        }
        return false
    }

    // Zapisz wydarzenie do Firestore i odśwież listę
    private fun saveEventToFirestore(
        event: Event,
        phoneNumber: String,
        eventName: String,
        location: String,
        startDate: String,
        endDate: String,
        sendSms: Boolean
    ) {
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
            "payment" to event.payment,
            "phoneNumber" to phoneNumber
        )
        eventRef.set(data)
            .addOnSuccessListener {
                Toast.makeText(this, "Wydarzenie zapisane!", Toast.LENGTH_SHORT).show()
                eventList.add(event)
                val currentFragment = supportFragmentManager.findFragmentById(R.id.frame_layout)
                if (currentFragment is CalendarFragment) {
                    currentFragment.reloadEvents(eventList)
                }
                // Przekierowanie do aplikacji SMS tylko jeśli checkbox jest zaznaczony
                if (sendSms && phoneNumber.isNotEmpty()) {
                    val smsText = "Informacja o wyjeździe: $eventName, miejsce: $location, od $startDate do $endDate"
                    val smsIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setData(android.net.Uri.parse("sms:$phoneNumber"))
                        putExtra("sms_body", smsText)
                    }
                    try {
                        startActivity(smsIntent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Nie można otworzyć aplikacji SMS", Toast.LENGTH_SHORT).show()
                    }
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
                    val color = document.getLong("color")?.toInt() ?: generatePastelPurpleColor()
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

    // Nowa funkcja do oznaczania wyjazdu jako zrealizowanego
    fun markTripAsDone(event: Event, documentId: String) {
        val db = FirebaseFirestore.getInstance()
        val eventRef = db.collection("events").document(documentId)

        // Pobierz istniejące fees z event (nie nadpisuj pustą listą!)
        val data = hashMapOf(
            "done" to 1,
            "fees" to event.fees // <-- zachowaj istniejące opłaty!
        )
        eventRef.update(data as Map<String, Any>)
            .addOnSuccessListener {
                Toast.makeText(this, "Wyjazd oznaczony jako zrealizowany!", Toast.LENGTH_SHORT).show()
                // Odśwież listę wyjazdów w odpowiednim fragmencie
                val currentFragment = supportFragmentManager.findFragmentById(R.id.frame_layout)
                if (currentFragment is MadeRoutesFragment) {
                    reloadEventsFromFirestore()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Błąd aktualizacji: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
