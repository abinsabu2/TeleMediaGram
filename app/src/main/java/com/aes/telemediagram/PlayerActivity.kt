package com.aes.telemediagram

import android.content.Context
import androidx.core.content.FileProvider
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.io.File

fun playWithVLC(context: Context, filePath: String?) {
    val file = File(filePath)
    if (!file.exists()) {
        Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
        return
    }

    val contentUri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",  // Must match manifest
        file
    )

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(contentUri, "video/*")
        setPackage("org.videolan.vlc") // Force open with VLC only
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "VLC not installed", Toast.LENGTH_SHORT).show()
    }
}

