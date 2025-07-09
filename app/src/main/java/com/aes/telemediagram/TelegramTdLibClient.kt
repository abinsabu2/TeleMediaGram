// TelegramBotClient.kt
package com.aes.telemediagram

import org.drinkless.td.libcore.telegram.Client
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.drinkless.td.libcore.telegram.TdApi


class TelegramTdLibClient() {

    private lateinit var client: Client
    private lateinit var updateHandler: Client.ResultHandler

    fun getLatestMessages() {
        client = Client.create({ result ->
            when (result) {
                is TdApi.UpdateAuthorizationState -> {
                    when (result.authorizationState) {
                        is TdApi.AuthorizationStateWaitTdlibParameters -> {
                            val params = TdApi.TdlibParameters().apply {
                                databaseDirectory = "tdlib"
                                useMessageDatabase = true
                                useSecretChats = true
                                apiId = 28127871
                                apiHash = "7ce3277bc22c26090df82d87a7d731fe"
                                systemLanguageCode = "en"
                                deviceModel = "Android"
                                applicationVersion = "1.0"
                                enableStorageOptimizer = true
                            }
                            client.send(TdApi.SetTdlibParameters(params), updateHandler)
                        }

                        is TdApi.AuthorizationStateWaitEncryptionKey -> {
                            client.send(TdApi.CheckDatabaseEncryptionKey(), updateHandler)
                        }

                        is TdApi.AuthorizationStateWaitPhoneNumber -> {
                            client.send(
                                TdApi.SetAuthenticationPhoneNumber("+1234567890", null),
                                updateHandler
                            )
                        }

                        is TdApi.AuthorizationStateWaitCode -> {
                            client.send(TdApi.CheckAuthenticationCode("12345"), updateHandler)
                        }
                    }
                }

                is TdApi.Chats -> {
                    // Process chat list
                }

                is TdApi.Error -> {
                    // Handle errors
                }
            }
        }, null, null)

        updateHandler = object : Client.ResultHandler {
            override fun onResult(result: TdApi.Object?) {
                // Handle updates
            }
        }
    }

}