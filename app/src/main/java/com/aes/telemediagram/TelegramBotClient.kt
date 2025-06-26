// TelegramBotClient.kt
package com.aes.telemediagram

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import org.json.JSONObject

data class TelegramUpdateResponse(
    val ok: Boolean,
    val result: List<TelegramUpdate>
)

data class TelegramUpdate(
    val update_id: Long,
    val message: TelegramMessage?
)

data class TelegramMessage(
    val message_id: Long,
    val text: String?,
    val caption: String?,
    val chat: TelegramChat?,
    val document: TelegramDocument?,
    val photo: List<TelegramPhotoSize>?,
    val video: TelegramVideo?,
    val audio: TelegramAudio?,
    val voice: TelegramVoice?,
    val sticker: TelegramSticker?,
    val animation: TelegramAnimation?,
    val video_note: TelegramVideoNote?
)

data class TelegramChat(
    val id: Long,
    val type: String,
    val title: String? = null,
    val username: String? = null,
    val first_name: String? = null,
    val last_name: String? = null
)

data class TelegramDocument(
    val file_id: String,
    val file_unique_id: String,
    val file_name: String?,
    val mime_type: String?,
    val file_size: Long?
)

data class TelegramPhotoSize(
    val file_id: String,
    val file_unique_id: String,
    val width: Int,
    val height: Int,
    val file_size: Long?
)

data class TelegramVideo(
    val file_id: String,
    val file_unique_id: String,
    val width: Int,
    val height: Int,
    val duration: Int,
    val mime_type: String?,
    val file_size: Long?,
    val thumbnail: TelegramPhotoSize?
)

data class TelegramAudio(
    val file_id: String,
    val file_unique_id: String,
    val duration: Int,
    val performer: String?,
    val title: String?,
    val mime_type: String?,
    val file_size: Long?
)

data class TelegramVoice(
    val file_id: String,
    val file_unique_id: String,
    val duration: Int,
    val mime_type: String?,
    val file_size: Long?
)

data class TelegramSticker(
    val file_id: String,
    val file_unique_id: String,
    val width: Int,
    val height: Int,
    val is_animated: Boolean?,
    val emoji: String?,
    val file_size: Long?
)

data class TelegramAnimation(
    val file_id: String,
    val file_unique_id: String,
    val width: Int,
    val height: Int,
    val duration: Int,
    val mime_type: String?,
    val file_size: Long?,
    val thumbnail: TelegramPhotoSize?
)

data class TelegramVideoNote(
    val file_id: String,
    val file_unique_id: String,
    val length: Int,
    val duration: Int,
    val file_size: Long?,
    val thumbnail: TelegramPhotoSize?
)



class TelegramBotClient(private val botToken: String) {
    private val client = OkHttpClient()
    private val gson = Gson()

fun getMessagesFlow(intervalMillis: Long): Flow<Result<List<String>>> = flow {
    while (currentCoroutineContext().isActive) {
        try {
            val messages = getLatestMessages(botToken)
            emit(Result.success(messages))
        } catch (e: Exception) {
            Log.e("TelegramBotClient", "Error fetching messages", e)
            emit(Result.failure(e))
        }
        delay(intervalMillis)
    }
}.flowOn(Dispatchers.IO)


    fun getLatestMessages(botToken: String): List<String> {
        val url = "https://api.telegram.org/bot$botToken/getUpdates"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val updates = gson.fromJson(body, TelegramUpdateResponse::class.java)

            val mediaLinks = mutableListOf<String>()
            val messageTexts = mutableListOf<String>()

            for (update in updates.result) {
                val message = update.message ?: continue

                // ✅ 1. Get text or caption (optional)
                val text = message.text ?: message.caption
                if (!text.isNullOrEmpty()) {
                    messageTexts.add(text)
                }

                // ✅ 2. Get downloadable media link
                val downloadUrl = getDownloadUrlFromMessage(message, botToken)
                if (downloadUrl != null) {
                    println("📥 Download URL: $downloadUrl")
                    mediaLinks.add(downloadUrl)
                }
            }

            // Return the texts or links — your choice here:
            return messageTexts.take(1000)
            // OR: return mediaLinks.take(1000)
        }

    }

    fun getDownloadUrlFromMessage(message: TelegramMessage, botToken: String): String? {
        val fileId: String? = when {
            message.video != null -> message.video.file_id
            message.document != null -> message.document.file_id
            message.audio != null -> message.audio.file_id
            message.voice != null -> message.voice.file_id
            message.animation != null -> message.animation.file_id
            message.video_note != null -> message.video_note.file_id
            !message.photo.isNullOrEmpty() -> {
                // Get the largest photo size (last item)
                message.photo.last().file_id
            }
            else -> null
        }

        fileId ?: return null

        try {
            val url = "https://api.telegram.org/bot$botToken/getFile?file_id=$fileId"
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->

                val responseBody = response.body?.string() ?: return null
                val json = JSONObject(responseBody)
                val filePath = json.getJSONObject("result").getString("file_path")
                return "https://api.telegram.org/file/bot$botToken/$filePath"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }


}