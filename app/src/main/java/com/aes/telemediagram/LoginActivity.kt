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
import java.io.FileOutputStream
import java.io.InputStream

class LoginActivity : FragmentActivity() {

    // Testing mode flag - activated when specific phone number is entered
    private var TESTING_MODE = false

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

    // Sample video file path for testing mode
    private var sampleVideoPath: String? = null

    // Mock data for testing
    private val mockChats = listOf(
        MockChat(1L, "Tech Discussion Group", "Latest technology trends and discussions"),
        MockChat(2L, "Movie Lovers", "Share and discuss your favorite movies"),
        MockChat(3L, "Travel Adventures", "Share your travel experiences and photos"),
        MockChat(4L, "Cooking Enthusiasts", "Recipe sharing and cooking tips"),
        MockChat(5L, "Gaming Community", "Latest games and gaming news"),
        MockChat(6L, "Photography Club", "Share your best shots and photography tips"),
        MockChat(7L, "Book Club", "Monthly book discussions and recommendations"),
        MockChat(8L, "Fitness Motivation", "Workout tips and fitness journey sharing")
    )

    private val mockMessages = mapOf(
        1L to listOf(
            MockMessage("Sample_Video_1.mp4", "Video: [101] - [45.2 MB] - [Tech Review 2024.mp4]", 101),
            MockMessage("Sample_Doc_1.pdf", "Document: [102] - [12.8 MB] - [Programming Guide.pdf]", 102),
            MockMessage("Sample_Video_2.mp4", "Video: [103] - [67.9 MB] - [Tutorial Series Part 1.mp4]", 103)
        ),
        2L to listOf(
            MockMessage("Movie_Trailer.mp4", "Video: [201] - [89.3 MB] - [Latest Movie Trailer.mp4]", 201),
            MockMessage("Movie_Review.mp4", "Video: [202] - [34.7 MB] - [Movie Review Analysis.mp4]", 202)
        ),
        3L to listOf(
            MockMessage("Travel_Vlog.mp4", "Video: [301] - [156.4 MB] - [Europe Travel Vlog.mp4]", 301),
            MockMessage("Travel_Guide.pdf", "Document: [302] - [8.9 MB] - [Complete Travel Guide.pdf]", 302)
        ),
        4L to listOf(
            MockMessage("Cooking_Tutorial.mp4", "Video: [401] - [78.1 MB] - [Master Chef Tutorial.mp4]", 401),
            MockMessage("Recipe_Book.pdf", "Document: [402] - [15.6 MB] - [100 Best Recipes.pdf]", 402)
        ),
        5L to listOf(
            MockMessage("Game_Review.mp4", "Video: [501] - [92.8 MB] - [Game Review 2024.mp4]", 501),
            MockMessage("Gaming_Guide.pdf", "Document: [502] - [11.3 MB] - [Pro Gaming Strategies.pdf]", 502)
        ),
        6L to listOf(
            MockMessage("Photo_Tutorial.mp4", "Video: [601] - [134.7 MB] - [Photography Masterclass.mp4]", 601),
            MockMessage("Camera_Manual.pdf", "Document: [602] - [23.4 MB] - [DSLR Camera Manual.pdf]", 602)
        ),
        7L to listOf(
            MockMessage("Book_Review.mp4", "Video: [701] - [43.2 MB] - [Monthly Book Review.mp4]", 701),
            MockMessage("Reading_List.pdf", "Document: [702] - [5.7 MB] - [2024 Reading List.pdf]", 702)
        ),
        8L to listOf(
            MockMessage("Workout_Video.mp4", "Video: [801] - [98.5 MB] - [30 Min Full Body Workout.mp4]", 801),
            MockMessage("Fitness_Plan.pdf", "Document: [802] - [7.2 MB] - [12 Week Fitness Plan.pdf]", 802)
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize UI elements
        initializeUiElements()

        // Create sample video file for testing mode
        createSampleVideoFile()

        // Always initialize Telegram client (will be bypassed if testing mode is activated)
        TelegramClientManager.initialize(::onResult)

        // Initialize ON Click Listeners
        initializeOnClickListeners()
    }


    private fun onResult(obj: TdApi.Object?) {
        if (TESTING_MODE) return // Skip real Telegram handling in testing mode

        when (obj) {
            is TdApi.Error -> updateStatus("Error: ${obj.message}")
            is TdApi.UpdateAuthorizationState -> onAuthorizationStateUpdated(obj.authorizationState)
            is TdApi.AuthorizationStateReady -> updateStatus("Authorization completed")
            is TdApi.UpdateFile -> handleFileUpdate(obj)
            else -> Log.d("TDLib", "Result: $obj")
        }
    }

    private fun onAuthorizationStateUpdated(state: TdApi.AuthorizationState) {
        if (TESTING_MODE) return // Skip in testing mode

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
        if (TESTING_MODE) {
            loadMockGroups()
            return
        }

        TelegramClientManager.loadAllGroups { chat ->
            runOnUiThread {
                chatListView.visibility = View.VISIBLE
                updateStatus("Chats")
                chatList.add(chat.title)
                chatIdList.add(chat.id)
                chatAdapter.notifyDataSetChanged()
                Log.d("TDLib", "Loaded group: ${chat.title}")
            }
        }
    }

    private fun loadMockGroups() {
        runOnUiThread {
            chatListView.visibility = View.VISIBLE
            updateStatus("Chats")

            mockChats.forEach { mockChat ->
                chatList.add(mockChat.title)
                chatIdList.add(mockChat.id)
            }
            chatAdapter.notifyDataSetChanged()
            Log.d("TDLib", "Loaded ${mockChats.size} groups")
        }
    }

    private fun loadMessages(chatId: Long) {
        if (TESTING_MODE) {
            loadMockMessages(chatId)
            return
        }

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

                messagesListView.setOnItemClickListener { _, _, position, _ ->
                    val media = mediaMessages[position]
                    showDownloadPrompt(media)
                }
            }
        }
    }

    private fun loadMockMessages(chatId: Long) {
        val messages = mockMessages[chatId] ?: emptyList()

        runOnUiThread {
            messagesList.clear()
            messages.forEach { mockMessage ->
                messagesList.add(mockMessage.description)
            }
            messagesAdapter.notifyDataSetChanged()

            chatListView.visibility = View.GONE
            messagesListView.visibility = View.VISIBLE
            backToChatsButton.visibility = View.VISIBLE

            messagesListView.setOnItemClickListener { _, _, position, _ ->
                if (position < messages.size) {
                    val mockMessage = messages[position]
                    val media = MediaMessage(
                        isMedia = true,
                        description = mockMessage.description,
                        localPath = null,
                        fileId = mockMessage.fileId
                    )
                    showMockDownloadPrompt(media)
                }
            }
        }
    }

    private fun showMockDownloadPrompt(media: MediaMessage) {
        AlertDialog.Builder(this)
            .setTitle("Download Required")
            .setMessage("This file is not downloaded yet. Do you want to download and stream it?")
            .setPositiveButton("Yes") { _, _ ->
                simulateDownload(media.fileId ?: 0)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun simulateDownload(fileId: Int) {
        // Simulate download progress
        scope.launch {
            activeDownloads.add(fileId)
            val mockPath = "mock/path/to/file_$fileId.mp4"

            for (progress in 0..100 step 10) {
                delay(500) // Simulate download time
                val downloadedSize = (progress * 50f) / 100f // Simulate up to 50MB
                val totalSize = 50f

                currentDownload = DownloadingFileInfo(
                    fileId = fileId,
                    downloadedSize = downloadedSize,
                    totalSize = totalSize,
                    progress = progress,
                    localPath = if (progress == 100) mockPath else null
                )

                runOnUiThread {
                    updateStatus("Downloading file [$fileId]: $progress% ($downloadedSize/$totalSize MB)...")
                }

                // Simulate playback threshold
                if (progress >= 60 && !isVLCPlaying) {
                    runOnUiThread {
                        resumeButton.visibility = View.VISIBLE
                        showToast("Sufficient data downloaded, you can now resume playback")
                    }
                    isVLCPlaying = true
                }
            }

            runOnUiThread {
                updateStatus("Download completed!")
                showToast("Download completed successfully")
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

        if (TESTING_MODE) {
            showMockDownloadPrompt(media)
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Download Required")
            .setMessage("This file is not downloaded yet. Do you want to download and stream it?")
            .setPositiveButton("Yes") { _, _ ->
                downloadAndPlayFile(media.fileId)
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
        if (!TESTING_MODE) {
            TelegramClientManager.close()
        }
        scope.cancel()
    }

    private fun downloadAndPlayFile(fileId: Int?) {
        if (TESTING_MODE) {
            simulateDownload(fileId ?: 0)
            return
        }

        TelegramClientManager.cancelDownload(activeDownloads)
        activeDownloads.clear()
        stopVLCPlayback()
        activeDownloads.add(fileId?.toInt()?:0)
        TelegramClientManager.startFileDownload(fileId)
    }

    private fun handleFileUpdate(update: TdApi.UpdateFile) {
        if (TESTING_MODE) return // Skip in testing mode

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

        if(file.local.isDownloadingCompleted){
            currentDownload?.localPath = file.local.path
            Log.d("TDLib", "Download Complete: ${file.local.path}")
        }

        if (file.local.path != null && downloadedSize > 300 && !isVLCPlaying) {
            resumeButton.visibility = View.VISIBLE
            this.isVLCPlaying = true
            playWithVLC(this@LoginActivity, file.local.path)
        }
    }

    fun stopVLCPlayback() {
        if (TESTING_MODE) {
            Log.d("VLC", "VLC playback stopped")
            isVLCPlaying = false
            return
        }

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
        if (TESTING_MODE) {
            showToast("Media folders cleared")
            return
        }

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

    private fun initializeOnClickListeners() {

        loginButton.setOnClickListener {
            val phone = phoneEdit.text.toString().trim()
            if (phone.isNotEmpty()) {
                // Check if this is the testing phone number
                if (phone == "+1234567890") {
                    TESTING_MODE = true
                    updateStatus("Please enter the authentication code")
                    phoneEdit.visibility = View.GONE
                    loginButton.visibility = View.GONE
                    codeEdit.visibility = View.VISIBLE
                    codeButton.visibility = View.VISIBLE
                } else {
                    TESTING_MODE = false
                    TelegramClientManager.sendPhoneNumber(phone, ::onResult)
                    updateStatus("Phone number sent, waiting for code...")
                    phoneEdit.visibility = View.GONE
                    loginButton.visibility = View.GONE
                }
            } else {
                showToast("Enter phone number")
            }
        }

        codeButton.setOnClickListener {
            val code = codeEdit.text.toString().trim()
            if (code.isNotEmpty()) {
                if (TESTING_MODE) {
                    // In testing mode, any code works
                    updateStatus("Authorization successful, loading groups...")
                    codeEdit.visibility = View.GONE
                    codeButton.visibility = View.GONE
                    // Simulate successful login and load mock groups
                    scope.launch {
                        delay(1000) // Simulate network delay
                        loadMockGroups()
                    }
                } else {
                    TelegramClientManager.sendAuthCode(code, ::onResult)
                    updateStatus("Code sent, processing...")
                    codeEdit.visibility = View.GONE
                    codeButton.visibility = View.GONE
                }
            } else {
                showToast("Enter code")
            }
        }

        cancelButton.setOnClickListener {
            if (TESTING_MODE) {
                activeDownloads.clear()
                isVLCPlaying = false
                showToast("Download cancelled")
                return@setOnClickListener
            }

            TelegramClientManager.cancelDownload(activeDownloads)
            isVLCPlaying = false
            activeDownloads.clear()
            stopVLCPlayback()
            deleteTdLibMediaFolders(this@LoginActivity)
            showToast("Download Cancelled")
            Log.d("TDLib", "Download Stopped")
        }

        resumeButton.setOnClickListener {
            if (TESTING_MODE) {
                if (sampleVideoPath != null) {
                    showToast("Playing video")
                    stopVLCPlayback()
                    isVLCPlaying = true
                    playWithVLC(this@LoginActivity, sampleVideoPath!!)
                } else {
                    showToast("Video not available")
                }
                return@setOnClickListener
            }

            if(currentDownload != null && currentDownload!!.downloadedSize > 300) {
                stopVLCPlayback()
                this.isVLCPlaying = true
                playWithVLC(this@LoginActivity, currentDownload!!.localPath)
            }else{
                showToast("Not Enough Data to Resume Min 500 MB required...")
            }
        }

        clearMedia.setOnClickListener {
            deleteTdLibMediaFolders(this@LoginActivity)
        }

        chatListView.setOnItemClickListener { _, _, position, _ ->
            clearMedia.visibility = View.VISIBLE
            cancelButton.visibility = View.VISIBLE
            resumeButton.visibility = View.VISIBLE
            val chatId = chatIdList[position]
            loadMessages(chatId)
        }

        backToChatsButton.setOnClickListener {
            clearMedia.visibility = View.GONE
            cancelButton.visibility = View.GONE
            messagesListView.visibility = View.GONE
            backToChatsButton.visibility = View.GONE
            resumeButton.visibility = View.GONE
            chatListView.visibility = View.VISIBLE
        }
    }

    private fun initializeUiElements() {
        statusText = findViewById(R.id.statusText)
        phoneEdit = findViewById(R.id.phoneEdit)
        codeEdit = findViewById(R.id.codeEdit)
        loginButton = findViewById(R.id.loginButton)
        codeButton = findViewById(R.id.codeButton)
        chatListView = findViewById(R.id.chatListView)
        clearMedia = findViewById(R.id.btnDeleteMedia)
        cancelButton = findViewById(R.id.btnCancelDownload)
        resumeButton = findViewById(R.id.btnResume)

        chatAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, chatList)
        chatListView.adapter = chatAdapter
        messagesListView = findViewById(R.id.messagesListView)
        messagesAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, messagesList)
        messagesListView.adapter = messagesAdapter
        backToChatsButton = findViewById(R.id.backToChatsButton)
    }

    private fun createSampleVideoFile() {
        try {
            // Create a sample directory in internal storage
            val sampleDir = File(filesDir, "sample")
            if (!sampleDir.exists()) {
                sampleDir.mkdirs()
            }

            val sampleFile = File(sampleDir, "sample_video.mkv")

            // Check if sample file already exists
            if (sampleFile.exists()) {
                sampleVideoPath = sampleFile.absolutePath
                Log.d("Sample", "Sample video already exists: $sampleVideoPath")
                return
            }

            // Try to copy from assets if available
            try {
                val inputStream: InputStream = assets.open("sample_video.mkv")
                val outputStream = FileOutputStream(sampleFile)

                val buffer = ByteArray(1024)
                var length: Int
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }

                inputStream.close()
                outputStream.close()

                sampleVideoPath = sampleFile.absolutePath
                Log.d("Sample", "Sample video created from assets: $sampleVideoPath")

            } catch (e: Exception) {
                // If no asset file, create a placeholder file
                Log.w("Sample", "No sample video in assets, creating placeholder: ${e.message}")

                // Create a small placeholder file
                val placeholderContent = "Sample MKV Video Placeholder"
                sampleFile.writeText(placeholderContent)
                sampleVideoPath = sampleFile.absolutePath

                Log.d("Sample", "Sample placeholder created: $sampleVideoPath")
            }

        } catch (e: Exception) {
            Log.e("Sample", "Failed to create sample video file: ${e.message}")
            sampleVideoPath = null
        }
    }

    private fun buttonModifier(){
        val params = clearMedia.layoutParams as RelativeLayout.LayoutParams
        params.addRule(RelativeLayout.BELOW, 0)
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
        params.addRule(RelativeLayout.RIGHT_OF, 0)
        params.addRule(RelativeLayout.RIGHT_OF, backToChatsButton.id)
        params.leftMargin = 16
        clearMedia.layoutParams = params

        val cancelParam = cancelButton.layoutParams as RelativeLayout.LayoutParams
        cancelParam.addRule(RelativeLayout.BELOW, 0)
        cancelParam.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
        cancelParam.addRule(RelativeLayout.RIGHT_OF, 0)
        cancelParam.addRule(RelativeLayout.RIGHT_OF, clearMedia.id)
        cancelParam.leftMargin = 16
        cancelParam.topMargin = dpToPx(20, this@LoginActivity)
        cancelButton.layoutParams = cancelParam

        val resumeParam = resumeButton.layoutParams as RelativeLayout.LayoutParams
        resumeParam.addRule(RelativeLayout.BELOW, 0)
        resumeParam.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
        resumeParam.addRule(RelativeLayout.RIGHT_OF, 0)
        resumeParam.addRule(RelativeLayout.RIGHT_OF, cancelButton.id)
        resumeParam.leftMargin = 16
        resumeParam.topMargin = dpToPx(20, this@LoginActivity)
        resumeButton.layoutParams = resumeParam
    }
}

// Mock data classes for testing
data class MockChat(
    val id: Long,
    val title: String,
    val description: String
)

data class MockMessage(
    val fileName: String,
    val description: String,
    val fileId: Int
)

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
    var localPath: String? = null,
)