package com.example.roadpro

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import com.example.roadpro.GoogleAuthClient
//import com.ag_apps.googlesignin.GoogleAuthClient
import com.example.roadpro.databinding.ActivitySignInBinding
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Api.ApiOptions.HasGoogleSignInAccountOptions
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.firebase.auth.FirebaseAuth
import android.app.AlertDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SignInActivity : AppCompatActivity() {

    private lateinit var binding :ActivitySignInBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var googleAuthClient: GoogleAuthClient


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        googleAuthClient = GoogleAuthClient(this)


        binding.textView.setOnClickListener{
            val intent = Intent(this,SignUpActivity::class.java)
            startActivity(intent)
        }

        binding.button.setOnClickListener {
            val email = binding.emailEt.text.toString()
            val pass = binding.passEt.text.toString()

            if (email.isNotEmpty() && pass.isNotEmpty()) {
                firebaseAuth.signInWithEmailAndPassword(email, pass).addOnCompleteListener {
                    if (it.isSuccessful) {
                        val intent = Intent(this, MainActivity::class.java)
                        intent.putExtra("userEmail", email) // Przekazanie adresu e-mail
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, it.exception.toString(), Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Empty Fields Are not Allowed!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.gSignIn.setOnClickListener{
            lifecycleScope.launch{
                val isSignedIn = googleAuthClient.signIn()
                if (isSignedIn) {
                    val userEmail = googleAuthClient.getSignedInUserEmail()
                    val intent = Intent(this@SignInActivity, MainActivity::class.java)
                    intent.putExtra("userEmail", userEmail)
                    startActivity(intent)
                }
            }
        }

        val forgotPasswordBtn = findViewById<Button>(R.id.forgotPasswordBtn)
        forgotPasswordBtn.setOnClickListener {
            showResetPasswordDialog()
        }

        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showResetPasswordDialog() {
        val emailEdit = EditText(this)
        emailEdit.hint = "Podaj swój email"
        AlertDialog.Builder(this)
            .setTitle("Resetowanie hasła")
            .setMessage("Podaj adres email, na który wyślemy link do zmiany hasła.")
            .setView(emailEdit)
            .setPositiveButton("Wyślij") { _, _ ->
                val email = emailEdit.text.toString()
                if (email.isNotBlank()) {
                    FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Wysłano email do resetowania hasła", Toast.LENGTH_LONG).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Błąd: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                } else {
                    Toast.makeText(this, "Podaj adres email", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    override fun onStart() {
        super.onStart()

        if(firebaseAuth.currentUser != null){
            val intent = Intent(this,MainActivity::class.java)
            startActivity(intent)
        }

        if (googleAuthClient.isSignedIn()) {
            startMainActivity()
        }
    }
}
