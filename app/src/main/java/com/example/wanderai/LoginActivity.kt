package com.wanderai.travelplanner

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.wanderai.travelplanner.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // If already logged in, go straight to Profile
        if (auth.currentUser != null) {
            startActivity(Intent(this, TripHistoryProfileActivity::class.java))
            finish()
            return
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topBar.setPadding(
                binding.topBar.paddingLeft,
                systemBars.top + (14 * resources.displayMetrics.density).toInt(),
                binding.topBar.paddingRight,
                binding.topBar.paddingBottom
            )
            view.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        val prefs = getSharedPreferences("WanderAI_Prefs", MODE_PRIVATE)
        val savedName = prefs.getString("USER_NAME", "").orEmpty()
        if (savedName.isNotEmpty()) {
            binding.etUsername.setText(savedName)
        }

        binding.btnSignIn.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (username.isEmpty()) {
                Toast.makeText(this, "Please enter a username", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnSignIn.isEnabled = false
            binding.btnSignIn.text = "Signing in..."

            // Save entered username locally right away
            prefs.edit().putString("USER_NAME", username).apply()

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    updateFirebaseDisplayNameAndProceed(result.user, username, "Welcome back, $username!")
                }
                .addOnFailureListener { signInException ->
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener { result ->
                            updateFirebaseDisplayNameAndProceed(result.user, username, "Account created for $username!")
                        }
                        .addOnFailureListener {
                            binding.btnSignIn.isEnabled = true
                            binding.btnSignIn.text = "Sign In"
                            Toast.makeText(
                                this,
                                "Auth failed: ${signInException.localizedMessage}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
        }

        // Continue without Sign In -> saves typed username if entered, then opens Profile
        binding.btnQuickBypass.setOnClickListener {
            val typedUsername = binding.etUsername.text.toString().trim()
            if (typedUsername.isNotEmpty()) {
                prefs.edit().putString("USER_NAME", typedUsername).apply()
            }
            startActivity(Intent(this, TripHistoryProfileActivity::class.java))
            finish()
        }
    }

    private fun updateFirebaseDisplayNameAndProceed(
        user: com.google.firebase.auth.FirebaseUser?,
        username: String,
        toastMsg: String
    ) {
        if (user != null) {
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(username)
                .build()
            user.updateProfile(profileUpdates).addOnCompleteListener {
                Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, TripHistoryProfileActivity::class.java))
                finish()
            }
        } else {
            Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, TripHistoryProfileActivity::class.java))
            finish()
        }
    }
}