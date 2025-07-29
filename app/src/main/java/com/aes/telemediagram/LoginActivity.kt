package com.aes.telemediagram

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.fragment.app.FragmentActivity
import com.aes.telemediagram.TelegramClientManager.client
import kotlinx.coroutines.*
import org.drinkless.tdlib.TdApi
import java.io.File

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

    private lateinit var clearMedia: Button

    private lateinit var messagesListView: ListView
    private val messagesList = mutableListOf<String>()
    private lateinit var messagesAdapter: ArrayAdapter<String>

    private var isVLCPlaying = false

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
        clearMedia = findViewById(R.id.btnDeleteMedia)

        clearMedia.visibility = View.VISIBLE

        chatAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, chatList)
        chatListView.adapter = chatAdapter

        // Initialize Telegram Client
        TelegramClientManager.initialize(::onResult)

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


        clearMedia.setOnClickListener {
            deleteTdLibMediaFolders(this@LoginActivity)
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
                TelegramClientManager.client = client
                updateStatus("Authorization successful, loading groups...")
                /*val intent = Intent(this@LoginActivity, MainActivity::class.java)
                startActivity(intent)
                finish()*/
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

                // Get current layout parameters
                val params = clearMedia.layoutParams as RelativeLayout.LayoutParams

                // Clear any existing relative rules
                params.addRule(RelativeLayout.BELOW, 0)
                params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
                params.addRule(RelativeLayout.RIGHT_OF, 0)

                // Set rule to position button2 to the right of button1
                params.addRule(RelativeLayout.RIGHT_OF, backToChatsButton.id)
                params.leftMargin = 16 // optional spacing between buttons (in px)

                // Apply the updated parameters
                clearMedia.layoutParams = params



                messagesListView.setOnItemClickListener { _, _, position, _ ->
                    val media = mediaMessages[position]
                     showDownloadPrompt(media)
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
                val path = ""
                MediaMessage("Video: [${file.id}] - [${content.video.fileName}]", path, file.id)
            }

            is TdApi.MessageDocument -> {
                val file = content.document.document
                val path = ""
                MediaMessage("Document: [${file.id}] - [${content.document.fileName}]", path,file.id)
            }

            else -> MediaMessage("Unsupported chat")
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

        val downloadedSize = file.local.downloadedSize
        val totalSize = file.expectedSize

        if (file.local.path != null && downloadedSize > 300 * 1024 && !isVLCPlaying) {
            // Once buffer threshold reached, play video
            playWithVLC(this@LoginActivity, file.local.path)
            this.isVLCPlaying = true
        }


        }

    fun deleteFolderRecursively(folder: File): Boolean {
        if (folder.isDirectory) {
            folder.listFiles()?.forEach { child ->
                deleteFolderRecursively(child)
            }
        }
        return folder.delete()
    }

    fun deleteTdLibMediaFolders(context: Context) {
        val baseDir = File(context.filesDir, "tdlib")
        val tempDir = File(baseDir, "temp")
        val documentDir = File(baseDir, "documents")

        var deletedCount = 0

        if (tempDir.exists()) {
            deleteFolderRecursively(tempDir)
            deletedCount++
        }

        if (documentDir.exists()) {
            deleteFolderRecursively(documentDir)
            deletedCount++
        }

        Toast.makeText(
            context,
            if (deletedCount > 0) "Deleted $deletedCount folders" else "No folders to delete",
            Toast.LENGTH_SHORT
        ).show()
    }

    }

