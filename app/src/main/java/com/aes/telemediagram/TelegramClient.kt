package com.aes.telemediagram

import android.content.Context
import android.util.Log
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.drinkless.tdlib.TdApi.*

class TelegramClient(private val context: Context) {

    private var client: Client? = null

    fun initializeClient() {
        client = Client.create(UpdateHandler(), null, null)
        
        val request = SetTdlibParameters()
        request.databaseDirectory = context.filesDir.absolutePath + "/tdlib"
        request.useMessageDatabase = true
        request.useSecretChats = true
        request.apiId = 21805799
        request.apiHash = "b74b95eace7c9327effac15b6a0c8d91"
        request.systemLanguageCode = "en"
        request.deviceModel = "Android Device"
        request.applicationVersion = "1.0"

        client?.send(request, AuthorizationRequestHandler())
    }

    fun sendPhoneNumber(phoneNumber: String) {
        if (client == null) initializeClient() // Initialize client if not already done
        client?.send(SetAuthenticationPhoneNumber(phoneNumber, null), AuthorizationRequestHandler())
    }

    fun sendCode(code: String) {
        if (client == null) initializeClient() // Initialize client if not already done
        try {
            client?.send(
                CheckAuthenticationCode(code),
                AuthorizationRequestHandler(),
                { exception ->
                    Log.e("TelegramClient", "Failed to send auth code", exception)
                }
            )
        } catch (e: Exception) {
            Log.e("TelegramClient", "Error sending authentication code", e)
        }
    }

    fun getClient(): Client? = client

    private fun loadChatMessages(chatId: Long, fromMessageId: Long = 0) {
        client?.send(TdApi.GetChatHistory(chatId, fromMessageId, 0, 100, false)) { result ->
            when (result) {
                is TdApi.Messages -> {
                    for (message in result.messages) {
                        handleNewMessage(message)
                    }
                }

                is TdApi.Error -> Log.e("TDLib Messages", "Error: ${result.message}")
            }
        }
    }

    private fun handleNewMessage(message: TdApi.Message) {
        when (val content = message.content) {
            is TdApi.MessageText -> {
                Log.d("TDLib Message", "Text message: ${content.text.text}")
            }

            is TdApi.MessagePhoto -> {
                Log.d("TDLib Message", "Photo message with caption: ${content.caption.text}")
            }

            else -> Log.d("TDLib Message", "Other message type: ${content.javaClass.simpleName}")
        }
    }

    private inner class UpdateHandler : Client.ResultHandler {
        override fun onResult(obj: TdApi.Object?) {
            when (obj) {
                is UpdateNewMessage -> handleNewMessage(obj.message)
                else -> Log.d("TDLib Update", obj.toString())
            }
        }
    }

    private inner class AuthorizationRequestHandler : Client.ResultHandler {
        override fun onResult(obj: TdApi.Object?) {
            when (obj) {
                is AuthorizationStateWaitTdlibParameters -> Log.d("TDLib Auth", "Waiting for TDLib parameters")
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
                    val limit = 100 // Number of chats to load
                    val offsetOrder: Long = Long.MAX_VALUE
                    val offsetChatId: Long = 0

                    client?.send(TdApi.GetChats(TdApi.ChatListMain(), limit)) { response ->
                        if (response is TdApi.Chats) {
                            for (chatId in response.chatIds) {
                                loadChatDetails(chatId)
                            }
                        }
                    }
                }
                is TdApi.Ok -> Log.d("TDLib Auth", "Operation successful")
                is TdApi.Error -> Log.e("TDLib Auth", "Error: ${obj.message}")
                else -> Log.d("TDLib Auth", "Unhandled state: $obj")
            }
        }
    }

    private fun loadChatDetails(chatId: Long) {
        client?.send(TdApi.GetChat(chatId)) { chatResponse ->
            if (chatResponse is TdApi.Chat) {
                val chat = chatResponse
                // Filter only groups
                when (chat.type) {
                    is TdApi.ChatTypeSupergroup -> {
                        val supergroup = chat.type as TdApi.ChatTypeSupergroup
                        if (!supergroup.isChannel) {
                            println("Supergroup: ${chat.title}")
                            loadChatMessages(chatId)
                        } else {
                            println("Channel: ${chat.title}")
                        }
                    }

                    is TdApi.ChatTypeBasicGroup -> {
                        println("Basic Group: ${chat.title}")
                        loadChatMessages(chatId)
                    }
                }
            }
        }
    }
}