package com.aes.telemediagram

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.FragmentActivity
import com.aes.telemediagram.R
import com.aes.telemediagram.TelegramClient

class VerifyCodeActivity : FragmentActivity() {

    private lateinit var codeInput: EditText
    private lateinit var verifyButton: Button
    private lateinit var telegramClient: TelegramClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_code)

        codeInput = findViewById(R.id.codeInput)
        verifyButton = findViewById(R.id.verifyButton)

        telegramClient = TelegramClient(this)

        verifyButton.setOnClickListener {
            val code = codeInput.text.toString().trim()
            if (code.isNotEmpty()) {
                telegramClient.sendCode(code)
            }
        }
    }
}