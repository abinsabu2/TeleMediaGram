package com.aes.telemediagram

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.FragmentActivity

class LoginActivity : FragmentActivity() {

    private lateinit var phoneInput: EditText
    private lateinit var loginButton: Button
    private lateinit var telegramClient: TelegramClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        phoneInput = findViewById(R.id.phoneInput)
        loginButton = findViewById(R.id.loginButton)

        // Initialize TelegramClient with Context
        telegramClient = TelegramClient(this)

        loginButton.setOnClickListener {
            val phoneNumber = phoneInput.text.toString().trim()
            if (phoneNumber.isNotEmpty()) {
                telegramClient.sendPhoneNumber(phoneNumber)

                // Start VerifyCodeActivity
                startActivity(Intent(this, VerifyCodeActivity::class.java))
            } else {
                Toast.makeText(this, "Please enter your phone number", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
