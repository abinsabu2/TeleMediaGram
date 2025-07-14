package com.aes.telemediagram

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.atomic.AtomicBoolean

object TelegramClientManager {

    private var client: Client? = null
    private val clientReady = AtomicBoolean(false)

    fun initialize(context: Context, resultHandler: (TdApi.Object?) -> Unit) {
        if (client != null) return  // Prevent reinitialization

        Client.execute(TdApi.SetLogVerbosityLevel(1))
        client = Client.create(resultHandler, null, null)

        val parameters = TdApi.SetTdlibParameters().apply {
            apiId = 21805799
            apiHash = "b74b95eace7c9327effac15b6a0c8d91"
            systemLanguageCode = "en"
            deviceModel = "Android"
            systemVersion = "10"
            applicationVersion = "1.0"
            databaseDirectory = context.filesDir.absolutePath + "/tdlib"
            useMessageDatabase = true
            useSecretChats = false
        }

        client?.send(parameters, resultHandler)
    }

    fun sendPhoneNumber(phone: String, callback: (TdApi.Object?) -> Unit) {
        client?.send(TdApi.SetAuthenticationPhoneNumber(phone, null), callback)
    }

    fun sendAuthCode(code: String, callback: (TdApi.Object?) -> Unit) {
        client?.send(TdApi.CheckAuthenticationCode(code), callback)
    }

    fun loadAllGroups(limit: Int = 1000, callback: (TdApi.Chat) -> Unit) {
        client?.send(TdApi.GetChats(TdApi.ChatListMain(), limit)) { result ->
            if (result is TdApi.Chats) {
                Log.d("TDLib", "Total chat IDs: ${result.chatIds.size}")
                result.chatIds.forEach { chatId ->
                    client?.send(TdApi.GetChat(chatId)) { chatObj ->
                        if (chatObj is TdApi.Chat) {
                            val chatType = chatObj.type
                            if (chatType is TdApi.ChatTypeSupergroup || chatType is TdApi.ChatTypeBasicGroup) {
                                callback(chatObj)
                            }
                        }
                    }
                }
            } else {
                Log.e("TDLib", "Failed to get chats: $result")
            }
        }
    }

    suspend fun loadMessagesForChat(chatId: Long, limit: Int = 20): List<TdApi.Message> = withContext(Dispatchers.IO) {
        val response = CompletableDeferred<TdApi.Object?>()

        client?.send(TdApi.GetChatHistory(chatId, 0, 0, limit, false)) {
            response.complete(it)
        }

        val result = response.await()
        if (result is TdApi.Messages) {
            return@withContext result.messages.toList()
        }
        return@withContext emptyList()
    }

    fun close() {
        client?.send(TdApi.Close(), null)
        client = null
    }
}
