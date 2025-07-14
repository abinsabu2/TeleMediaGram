package com.aes.telemediagram

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
    }

    private fun onResult(obj: TdApi.Object?) {
        when (obj) {
            is TdApi.Error -> updateStatus("Error: ${obj.message}")

            is TdApi.UpdateAuthorizationState -> onAuthorizationStateUpdated(obj.authorizationState)

            is TdApi.AuthorizationStateReady -> updateStatus("Authorization completed")

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
            val messageContents = messages.map { parseMessageContent(it.content) }
            showMessagesInDialog(messageContents)
        }
    }

    private fun parseMessageContent(content: TdApi.MessageContent): String {
        return when (content) {
            is TdApi.MessageText -> "Text: ${content.text.text}"
            is TdApi.MessagePhoto -> {
                val photoInfo = content.photo.sizes.lastOrNull()
                "Photo: fileId=${photoInfo?.photo?.id}"
            }
            is TdApi.MessageVideo -> {
                val video = content.video
                "Video: fileId=${video.video.id}, duration=${video.duration}s"
            }
            is TdApi.MessageSticker -> "Sticker: emoji=${content.sticker.emoji}, fileId=${content.sticker.sticker.id}"
            is TdApi.MessageDocument -> "Document: fileId=${content.document.document.id}, name=${content.document.fileName}"
            is TdApi.MessageAudio -> "Audio: fileId=${content.audio.audio.id}, duration=${content.audio.duration}s"
            else -> "Unsupported message type: ${content.javaClass.simpleName}"
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
}
