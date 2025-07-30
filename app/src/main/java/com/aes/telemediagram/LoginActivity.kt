package com.aes.telemediagram

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
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

    private lateinit var cancelButton: Button

    private lateinit var resumeButton: Button

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
    private var currentDownload: DownloadingFileInfo? = null
    val activeDownloads = mutableSetOf<Int>()

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

        cancelButton = findViewById(R.id.btnCancelDownload)

        resumeButton = findViewById(R.id.btnResume)

        cancelButton.setOnClickListener {
            showToast("File Download Cancellation Initiated...")
            TelegramClientManager.cancelDownload(activeDownloads)
            isVLCPlaying = false
            activeDownloads.clear()
            stopVLCPlayback()
            deleteTdLibMediaFolders(this@LoginActivity)
            showToast("Download Cancelled")
            Log.d("TDLib", "Download Stoped")
        }

        resumeButton.setOnClickListener {
            if(currentDownload != null && currentDownload!!.downloadedSize > 500) {
                stopVLCPlayback()
                this.isVLCPlaying = true
                playWithVLC(this@LoginActivity, currentDownload!!.localPath)

            }else{
                showToast("Not Enough Data to Resume Min 500 MB required...")
            }
        }

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
                loadGroups()
            }
            else -> Log.d("TDLib", "Unhandled Auth State: $state")
        }
    }

    private fun loadGroups() {
        TelegramClientManager.loadAllGroups { chat ->
            runOnUiThread {
                updateStatus("Chats")
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
                    messagesList.add(it.description + if (it.localPath != null) "" else "")
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


                // Get current layout parameters
                val cancelParam = cancelButton.layoutParams as RelativeLayout.LayoutParams

                // Clear any existing relative rules
                cancelParam.addRule(RelativeLayout.BELOW, 0)
                cancelParam.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
                cancelParam.addRule(RelativeLayout.RIGHT_OF, 0)
                cancelParam.addRule(RelativeLayout.RIGHT_OF, clearMedia.id)
                cancelParam.leftMargin = 16 // optional spacing between buttons (in px)

                // Apply the updated parameters
                cancelParam.topMargin = dpToPx(20, this@LoginActivity)
                cancelButton.layoutParams = cancelParam



                val resumeParam = resumeButton.layoutParams as RelativeLayout.LayoutParams

                // Clear any existing relative rules
                resumeParam.addRule(RelativeLayout.BELOW, 0)
                resumeParam.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
                resumeParam.addRule(RelativeLayout.RIGHT_OF, 0)
                resumeParam.addRule(RelativeLayout.RIGHT_OF, cancelButton.id)
                resumeParam.leftMargin = 16 // optional spacing between buttons (in px)

                // Apply the updated parameters
                resumeParam.topMargin = dpToPx(20, this@LoginActivity)
                resumeButton.layoutParams = resumeParam


                messagesListView.setOnItemClickListener { _, _, position, _ ->
                    val media = mediaMessages[position]
                     showDownloadPrompt(media)
                }
            }
        }
    }

    fun dpToPx(dp: Int, context: Context): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun showDownloadPrompt(media: MediaMessage) {

        if (!media.isMedia) {
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Download Required")
            .setMessage("This file is not downloaded yet. Do you want to download and stream it?")
            .setPositiveButton("Yes") { _, _ ->
                downloadAndPlayFile(media.fileId) // assuming MediaMessage holds fileId
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun parseMessageContent(content: TdApi.MessageContent): MediaMessage {
        return when (content) {
            is TdApi.MessageVideo -> {
                val file = content.video.video
                val path = ""
                val fileSize = file.size.toFloat() / (1024 * 1024)
                MediaMessage(true,"Video: [${file.id}] - [${fileSize} MB]- [${content.video.fileName}]", path, file.id)
            }

            is TdApi.MessageDocument -> {
                val file = content.document.document
                val path = ""
                val fileSize = file.size.toFloat() / (1024 * 1024)
                MediaMessage(true,"Document: [${file.id}] - [${fileSize} MB] - [${content.document.fileName}]", path,file.id)
            }

            else -> MediaMessage(false,"No Video or Document Found in this chat!")
        }
    }


    @SuppressLint("SetTextI18n")
    private fun updateStatus(message: String) {
        runOnUiThread {
            statusText.text = "Last Message: $message"
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
        val isMedia: Boolean = false,
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

    data class DownloadingFileInfo(
        val fileId: Int,
        val downloadedSize: Float,
        val totalSize: Float,
        val progress: Int,
        val localPath: String? = null,
    )

    private fun downloadAndPlayFile(fileId: Int?) {
        TelegramClientManager.cancelDownload(activeDownloads)
        activeDownloads.clear()
        stopVLCPlayback()
        activeDownloads.add(fileId?.toInt()?:0)
        TelegramClientManager.startFileDownload(fileId)
    }

    private fun handleFileUpdate(update: TdApi.UpdateFile) {
        val file = update.file
        val downloaded = file.local.downloadedSize
        val expected = file.expectedSize
        val progress = if (expected > 0) (downloaded * 100 / expected).toInt() else 0
        val downloadedSize = file.local.downloadedSize.toFloat() / (1024 * 1024)
        val totalSize = file.expectedSize.toFloat() / (1024 * 1024)
        val fileId = file.id

        currentDownload =
            DownloadingFileInfo(fileId, downloadedSize, totalSize, progress, file.local.path)

        runOnUiThread {
            updateStatus("Downloading file [$fileId]: $progress% ($downloadedSize/$totalSize MB)...")
        }



        if (file.local.path != null && downloadedSize > 300 && !isVLCPlaying) {
            // Once buffer threshold reached, play video
            this.isVLCPlaying = true
            playWithVLC(this@LoginActivity, file.local.path)
        }


        }

    fun stopVLCPlayback() {
        val stopIntent = Intent("org.videolan.vlc.remote.StopPlayback")
        stopIntent.setPackage("org.videolan.vlc")
        try {
            sendBroadcast(stopIntent)
            Log.d("VLC", "Stop playback broadcast sent")
        } catch (e: Exception) {
            Log.e("VLC", "Failed to stop VLC: ${e.message}")
        }
        this.isVLCPlaying = false
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

