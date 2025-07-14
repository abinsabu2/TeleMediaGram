package com.aes.telemediagram

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.*
import org.drinkless.tdlib.TdApi

class LoginActivity : FragmentActivity() {

    private lateinit var statusText: TextView
    private lateinit var phoneEdit: EditText
    private lateinit var codeEdit: EditText
    private lateinit var loginButton: Button
    private lateinit var codeButton: Button
    private lateinit var chatListView: ListView

    private val chatList = mutableListOf<String>()
    private val chatIdList = mutableListOf<Long>()
    private lateinit var chatAdapter: ArrayAdapter<String>

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var backToChatsButton: Button

    private lateinit var messagesListView: ListView
    private val messagesList = mutableListOf<String>()
    private lateinit var messagesAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize UI elements
        statusText = findViewById(R.id.statusText)
        phoneEdit = findViewById(R.id.phoneEdit)
        codeEdit = findViewById(R.id.codeEdit)
        loginButton = findViewById(R.id.loginButton)
        codeButton = findViewById(R.id.codeButton)
        chatListView = findViewById(R.id.chatListView)

        chatAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, chatList)
        chatListView.adapter = chatAdapter

        // Initialize Telegram Client
        TelegramClientManager.initialize(this, ::onResult)

        loginButton.setOnClickListener {
            val phone = phoneEdit.text.toString().trim()
            if (phone.isNotEmpty()) {
                TelegramClientManager.sendPhoneNumber(phone, ::onResult)
                updateStatus("Phone number sent, waiting for code...")
                phoneEdit.visibility = View.GONE
                loginButton.visibility = View.GONE
            } else {
                showToast("Enter phone number")
            }
        }

        codeButton.setOnClickListener {
            val code = codeEdit.text.toString().trim()
            if (code.isNotEmpty()) {
                TelegramClientManager.sendAuthCode(code, ::onResult)
                updateStatus("Code sent, processing...")
                codeEdit.visibility = View.GONE
                codeButton.visibility = View.GONE
            } else {
                showToast("Enter code")
            }
        }

        chatListView.setOnItemClickListener { _, _, position, _ ->
            val chatId = chatIdList[position]
            loadMessages(chatId)
        }

        messagesListView = findViewById(R.id.messagesListView)
        messagesAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, messagesList)
        messagesListView.adapter = messagesAdapter

        backToChatsButton = findViewById(R.id.backToChatsButton)
        backToChatsButton.setOnClickListener {
            messagesListView.visibility = View.GONE
            chatListView.visibility = View.VISIBLE
            backToChatsButton.visibility = View.GONE
        }
    }

    private fun onResult(obj: TdApi.Object?) {
        when (obj) {
            is TdApi.Error -> updateStatus("Error: ${obj.message}")

            is TdApi.UpdateAuthorizationState -> onAuthorizationStateUpdated(obj.authorizationState)

            is TdApi.AuthorizationStateReady -> updateStatus("Authorization completed")

            is TdApi.UpdateFile -> handleFileUpdate(obj)

            else -> Log.d("TDLib", "Result: $obj")
        }
    }

    private fun onAuthorizationStateUpdated(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                runOnUiThread {
                    statusText.text = "Please enter your phone number"
                    phoneEdit.visibility = View.VISIBLE
                    loginButton.visibility = View.VISIBLE
                }
            }
            is TdApi.AuthorizationStateWaitCode -> {
                runOnUiThread {
                    statusText.text = "Please enter the authentication code"
                    codeEdit.visibility = View.VISIBLE
                    codeButton.visibility = View.VISIBLE
                }
            }
            is TdApi.AuthorizationStateReady -> {
                updateStatus("Authorization successful, loading groups...")
                loadGroups()
            }
            else -> Log.d("TDLib", "Unhandled Auth State: $state")
        }
    }

    private fun loadGroups() {
        TelegramClientManager.loadAllGroups { chat ->
            runOnUiThread {
                chatList.add(chat.title)
                chatIdList.add(chat.id)
                chatAdapter.notifyDataSetChanged()
                Log.d("TDLib", "Loaded group: ${chat.title}")
            }
        }
    }

    private fun loadMessages(chatId: Long) {
        scope.launch {
            val messages = TelegramClientManager.loadMessagesForChat(chatId)
            val mediaMessages = messages.map { parseMessageContent(it.content) }

            runOnUiThread {
                messagesList.clear()
                mediaMessages.forEach {
                    messagesList.add(it.description + if (it.localPath != null) " [Stream]" else "")
                }
                messagesAdapter.notifyDataSetChanged()

                chatListView.visibility = View.GONE
                messagesListView.visibility = View.VISIBLE
                backToChatsButton.visibility = View.VISIBLE

                messagesListView.setOnItemClickListener { _, _, position, _ ->
                    val media = mediaMessages[position]
                    if (media.localPath != null) {
                        playMediaFile(media.localPath)
                    } else {
                        showDownloadPrompt(media)
                    }
                }
            }
        }
    }
    private fun showDownloadPrompt(media: MediaMessage) {
        AlertDialog.Builder(this)
            .setTitle("Download Required")
            .setMessage("This file is not downloaded yet. Do you want to download and stream it?")
            .setPositiveButton("Yes") { _, _ ->
                downloadAndPlayFile(media.fileId) // assuming MediaMessage holds fileId
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun playMediaFile(path: String) {
        val fileUri = Uri.parse("file://$path")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, getMimeType(path))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Open with")
        startActivity(chooser)
    }

    private fun getMimeType(path: String): String {
        return when {
            path.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            path.endsWith(".mkv", ignoreCase = true) -> "video/x-matroska"
            path.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            path.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
            else -> "*/*"
        }
    }
    private fun parseMessageContent(content: TdApi.MessageContent): MediaMessage {
        return when (content) {
            is TdApi.MessageText -> MediaMessage("Text: ${content.text.text}")

            is TdApi.MessageVideo -> {
                val file = content.video.video
                val path = getFileLocalPath(file)
                MediaMessage("Video: duration=${content.video.duration}s", path, file.id)
            }

            is TdApi.MessageDocument -> {
                val file = content.document.document
                val path = getFileLocalPath(file)
                MediaMessage("Document: ${content.document.fileName}", path)
            }

            else -> MediaMessage("Unsupported: ${content.javaClass.simpleName}")
        }
    }

    private fun getFileLocalPath(file: TdApi.File): String? {
        return if (file.local.isDownloadingCompleted && file.local.path != null) {
            file.local.path
        } else {
            downloadAndPlayFile(file.id)
            null
        }
    }


    private fun showMessagesInDialog(messages: List<String>) {
        val builder = android.app.AlertDialog.Builder(this)
            .setTitle("Group Messages")
            .setMessage(if (messages.isNotEmpty()) messages.joinToString("\n\n") else "No messages found.")
            .setPositiveButton("OK", null)
        builder.show()
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            statusText.text = message
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        TelegramClientManager.close()
        scope.cancel()
    }

    data class MediaMessage(
        val description: String,
        val localPath: String? = null,
        val fileId: Int? = null,
        val mimeType: String? = null,
        val width: Int? = null,
        val height: Int? = null,
        val duration: Int? = null,
        val size: Int? = null,
        val thumbnailPath: String? = null,
        val thumbnailMimeType: String? = null,
        val thumbnailWidth: Int? = null,
        val thumbnailHeight: Int? = null,
    )
    private fun downloadAndPlayFile(fileId: Int?) {
        showLoading(true)

        TelegramClientManager.startFileDownload(fileId)
    }

    private fun showLoading(isLoading: Boolean) {
        // Show or hide a progress bar/spinner
        // Example:
        // progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun handleFileUpdate(update: TdApi.UpdateFile) {
        val file = update.file
        val downloaded = file.local.downloadedSize
        val expected = file.expectedSize
        val progress = if (expected > 0) (downloaded * 100 / expected).toInt() else 0

        runOnUiThread {
            updateStatus("Downloading file: $progress%")
            // Optionally update a progress bar here
        }

        if (file.local.isDownloadingCompleted) {
            runOnUiThread {
                updateStatus("Download complete: ${file.local.path}")
                playMediaFile(file.local.path)
            }
        }
    }
}
