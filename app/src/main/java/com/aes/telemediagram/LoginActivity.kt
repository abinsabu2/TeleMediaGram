package com.aes.telemediagram

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.FragmentActivity

import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.atomic.AtomicBoolean

class LoginActivity : FragmentActivity() {

    private var client: Client? = null
    private val clientReady = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO)

    private lateinit var statusText: TextView
    private lateinit var phoneEdit: EditText
    private lateinit var codeEdit: EditText
    private lateinit var loginButton: Button
    private lateinit var codeButton: Button
    private lateinit var chatListView: ListView

    private val chatList = mutableListOf<String>()
    private val chatIdList = mutableListOf<Long>()
    private lateinit var chatAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        statusText = findViewById(R.id.statusText)
        phoneEdit = findViewById(R.id.phoneEdit)
        codeEdit = findViewById(R.id.codeEdit)
        loginButton = findViewById(R.id.loginButton)
        codeButton = findViewById(R.id.codeButton)
        chatListView = findViewById(R.id.chatListView)

        chatAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, chatList)
        chatListView.adapter = chatAdapter

        loginButton.setOnClickListener {
            val phone = phoneEdit.text.toString().trim()
            if (phone.isNotEmpty()) {
                sendPhoneNumber(phone)
            } else {
                Toast.makeText(this, "Enter phone number", Toast.LENGTH_SHORT).show()
            }
        }

        codeButton.setOnClickListener {
            val code = codeEdit.text.toString().trim()
            if (code.isNotEmpty()) {
                sendAuthCode(code)
            } else {
                Toast.makeText(this, "Enter code", Toast.LENGTH_SHORT).show()
            }
        }

        chatListView.setOnItemClickListener { _, _, position, _ ->
            val chatId = chatIdList[position]
            loadMessagesForChat(chatId)
        }

        initClient()
    }
    private fun initClient() {
        Client.execute(TdApi.SetLogVerbosityLevel(1))
        client = Client.create(::onResult, null, null)

        val parameters = TdApi.SetTdlibParameters().apply {
            apiId = 21805799  // TODO: replace with your API ID
            apiHash = "b74b95eace7c9327effac15b6a0c8d91"  // TODO: replace with your API hash
            systemLanguageCode = "en"
            deviceModel = "Android"
            systemVersion = "10"
            applicationVersion = "1.0"
            databaseDirectory = filesDir.absolutePath + "/tdlib"
            useMessageDatabase = true
            useSecretChats = false
        }
        client?.send(parameters, ::onResult)
    }
    private fun onResult(obj: TdApi.Object?) {
        when (obj) {
            is TdApi.Error -> {
                runOnUiThread {
                    statusText.text = "Error: ${obj.message}"
                }
            }
            is TdApi.UpdateAuthorizationState -> {
                onAuthorizationStateUpdated(obj.authorizationState)
            }
            is TdApi.Ok -> {
                Log.d("TDLib", "Ok")
            }
            is TdApi.AuthorizationStateReady -> {
                clientReady.set(true)
                runOnUiThread {
                    statusText.text = "Authorization completed"
                    phoneEdit.visibility = View.GONE
                    loginButton.visibility = View.GONE
                    codeEdit.visibility = View.GONE
                    codeButton.visibility = View.GONE
                }
            }
            is TdApi.Chat -> {
                runOnUiThread {
                    chatList.add(obj.title)
                    chatIdList.add(obj.id)
                    chatAdapter.notifyDataSetChanged()
                }
            }
            else -> {
                statusText.text = "Authorization completed"
                Log.d("TDLib", "Received: $obj")
            }
        }
    }

    private fun onUpdate(obj: TdApi.Object?) {
        // You can process updates here if needed
        Log.d("TDLib", "Update: $obj")
    }

    private fun onError(e: Exception) {
        e.printStackTrace()
    }

    private fun onAuthorizationStateUpdated(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                // Already sent parameters in initClient
            }
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
                // Already handled
                loadAllGroups(100000)
            }
            else -> {
                Log.d("TDLib", "Auth State: $state")
            }
        }
    }

    private fun sendPhoneNumber(phone: String) {
        client?.send(TdApi.SetAuthenticationPhoneNumber(phone, null), ::onResult)
        runOnUiThread {
            statusText.text = "Phone number sent, waiting for code..."
            phoneEdit.visibility = View.GONE
            loginButton.visibility = View.GONE
        }
    }

    private fun sendAuthCode(code: String) {
        client?.send(TdApi.CheckAuthenticationCode(code), ::onResult)
        runOnUiThread {
            statusText.text = "Code sent, processing..."
            codeEdit.visibility = View.GONE
            codeButton.visibility = View.GONE
        }
    }

    private fun loadChats(limit: Int = 20) {
        client?.send(TdApi.GetChats(TdApi.ChatListMain(), limit)) { result ->
            if (result is TdApi.Chats) {
                result.chatIds.forEach { chatId ->
                    client?.send(TdApi.GetChat(chatId), ::onResult)
                }
            }
        }
    }
    private fun loadAllGroups(limit: Int = 1000) {
        client?.send(TdApi.GetChats(TdApi.ChatListMain(), limit)) { result ->
            if (result is TdApi.Chats) {
                Log.d("TDLib", "Total chat IDs: ${result.chatIds.size}")
                result.chatIds.forEach { chatId ->
                    fetchChatDetails(chatId)
                }
            } else {
                Log.e("TDLib", "Failed to get chats: $result")
            }
        }
    }

    private fun fetchChatDetails(chatId: Long) {
        client?.send(TdApi.GetChat(chatId)) { chatObj ->
            if (chatObj is TdApi.Chat) {
                val chatType = chatObj.type
                if (chatType is TdApi.ChatTypeSupergroup || chatType is TdApi.ChatTypeBasicGroup) {
                    runOnUiThread {
                        chatList.add(chatObj.title)
                        chatIdList.add(chatObj.id)
                        chatAdapter.notifyDataSetChanged()
                        Log.d("TDLib", "Added group: ${chatObj.title}")
                    }
                } else {
                    Log.d("TDLib", "Skipped non-group chat: ${chatObj.title}")
                }
            } else {
                Log.e("TDLib", "Failed to get chat details for ID $chatId: $chatObj")
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        client?.send(TdApi.Close(), null)
        client = null
        scope.cancel()
    }
    private fun loadMessagesForChat(chatId: Long, limit: Int = 20) {
        client?.send(TdApi.GetChatHistory(chatId, 0, 0, limit, false)) { obj ->
            if (obj is TdApi.Messages) {
                runOnUiThread {
                    if (obj.totalCount == 0) {
                        Toast.makeText(this, "No messages in this group.", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }

                    val messageContents = obj.messages.map { message ->
                        parseMessageContent(message.content)
                    }

                    showMessagesInDialog(messageContents)
                }
            } else {
                Log.e("TDLib", "Failed to load messages: $obj")
            }
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
        builder.setTitle("Group Messages")

        val messageText = if (messages.isNotEmpty()) messages.joinToString("\n\n") else "No messages found."
        builder.setMessage(messageText)

        builder.setPositiveButton("OK", null)
        builder.show()
    }
}
