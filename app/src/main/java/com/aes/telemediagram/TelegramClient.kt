package com.aes.telemediagram

import android.content.Context
import android.util.Log
import org.drinkless.td.libcore.telegram.Client
import org.drinkless.td.libcore.telegram.TdApi
import org.drinkless.td.libcore.telegram.TdApi.*

class TelegramClient(private val context: Context) {

    private var client: Client? = null

    fun initializeClient() {
        client = Client.create(UpdateHandler(), null, null)

        val parameters = TdlibParameters().apply {
            databaseDirectory = context.filesDir.absolutePath + "/tdlib"
            apiId = 21805799
            apiHash = "b74b95eace7c9327effac15b6a0c8d91"
            systemLanguageCode = "en"
            deviceModel = "Android Device"
            systemVersion = "1.0"
            applicationVersion = "1.0"
            useMessageDatabase = true
            useSecretChats = false
        }

        client?.send(SetTdlibParameters(parameters), AuthorizationRequestHandler())
    }

    fun sendPhoneNumber(phoneNumber: String) {
        if (client == null) initializeClient() // Initialize client if not already done
        client?.send(CheckDatabaseEncryptionKey(), AuthorizationRequestHandler())
        client?.send(SetAuthenticationPhoneNumber(phoneNumber, null), AuthorizationRequestHandler())
    }

    fun sendCode(code: String) {
        client?.send(CheckAuthenticationCode(code), AuthorizationRequestHandler())
    }

    private inner class UpdateHandler : Client.ResultHandler {
        override fun onResult(obj: org.drinkless.td.libcore.telegram.TdApi.Object?) {
            Log.d("TDLib Update", obj.toString())
        }
    }

    private inner class AuthorizationRequestHandler : Client.ResultHandler {
        override fun onResult(obj: TdApi.Object?) {
            when (obj) {
                is AuthorizationStateWaitTdlibParameters -> Log.d("TDLib Auth", "Waiting for TDLib parameters")
                is AuthorizationStateWaitEncryptionKey -> {
                    Log.d("TDLib Auth", "Waiting for encryption key")
                    client?.send(CheckDatabaseEncryptionKey(), this)
                }
                is AuthorizationStateWaitPhoneNumber -> {
                    Log.d("TDLib Auth", "Waiting for phone number input")
                    // Trigger your UI to ask for phone number (or proceed if you already have it)
                }
                is AuthorizationStateWaitCode -> {
                    Log.d("TDLib Auth", "Waiting for authentication code input")
                    // Show code input screen now
                }
                is AuthorizationStateReady -> {
                    Log.d("TDLib Auth", "Authorization complete!")
                    // Ready to use Telegram!
                }
                is TdApi.Ok -> Log.d("TDLib Auth", "Operation successful")
                is TdApi.Error -> Log.e("TDLib Auth", "Error: ${obj.message}")
                else -> Log.d("TDLib Auth", "Unhandled state: $obj")
            }
        }
    }
}
