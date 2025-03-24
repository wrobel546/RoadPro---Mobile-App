package com.example.roadpro

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.roadpro.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private var userEmail: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()

        // Odbierz adres e-mail użytkownika z Intent
        userEmail = intent.getStringExtra("userEmail") ?: "example@gmail.com"
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

        // FloatingActionButton - dodawanie wydarzeń w CalendarFragment
        binding.floatingActionButton.setOnClickListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.frame_layout)
            if (currentFragment is CalendarFragment) {
                currentFragment.showAddEventDialog()
            } else {
                Toast.makeText(this, "Dodawanie wydarzeń działa tylko w kalendarzu!", Toast.LENGTH_SHORT).show()
            }
        }

        // Obsługa wylogowania
        setupLogoutButton()
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.frame_layout, fragment)
            .commit()
    }

    private fun setupLogoutButton() {
        val logoutBtn = findViewById<Button>(R.id.logoutBtn)
        logoutBtn.setOnClickListener {
            firebaseAuth.signOut()
            val intent = Intent(this, SignInActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish() // Zamyka MainActivity
        }
    }
}
