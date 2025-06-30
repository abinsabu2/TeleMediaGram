// TelegramBotClient.kt
package com.aes.telemediagram

import org.drinkless.tdlib.JsonClient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class TdlibRequest(
    @SerialName("@type")
    val type: String = "setTdlibParameters",
    val parameters: TdlibParameters
)

@Serializable
data class TdlibParameters(
    val use_test_dc: Boolean = false,
    val database_directory: String = "tdlib",
    val files_directory: String = "tdlib",
    val use_file_database: Boolean = true,
    val use_chat_info_database: Boolean = true,
    val use_message_database: Boolean = true,
    val api_id: Int, // Replace YOUR_API_ID with actual integer
    val api_hash: String, // Replace YOUR_API_HASH with actual string
    val system_language_code: String = "en",
    val device_model: String = "",
    val system_version: String = "",
    val application_version: String = "1.0"
)

class TelegramTdLibClient() {

    fun getLatestMessages(){
        JsonClient.setLogMessageHandler(0, LogMessageHandler())

        // create client identifier
        val clientId = JsonClient.createClientId()
        val request = createTdlibParameters(
            apiId = 21805799, // Replace with your actual API ID
            apiHash = "b74b95eace7c9327effac15b6a0c8d91" // Replace with your actual API hash
        )
        val jsonString = json.encodeToString(request)

        JsonClient.send(clientId, jsonString);
        // main loop
        while (true) {
            val result = JsonClient.receive(100.0)
            if (result != null) {
                println(result)
            }
        }
    }
    // Configure JSON to include default values
    val json = Json {
        encodeDefaults = true  // This is the key configuration
        prettyPrint = true
    }

    fun createTdlibParameters(apiId: Int, apiHash: String): TdlibRequest {
        return TdlibRequest(
            parameters = TdlibParameters(
                api_id = apiId,
                api_hash = apiHash
            )
        )
    }

    class LogMessageHandler : JsonClient.LogMessageHandler {
        override fun onLogMessage(verbosityLevel: Int, message: String?) {
            System.err.print(message)
            if (verbosityLevel == 0) {
                System.err.println("Receive fatal error; the process will crash now")
            }
        }
    }

}