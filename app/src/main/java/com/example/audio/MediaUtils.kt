package com.example.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

object MediaUtils {

    data class ProcessedAvatar(
        val filePath: String,
        val base64: String
    )

    suspend fun savePickedImage(context: Context, uri: Uri, fileNamePrefix: String): ProcessedAvatar? =
        withContext(Dispatchers.IO) {
            runCatching {
                val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
                val original = input.use { BitmapFactory.decodeStream(it) } ?: return@withContext null

                val maxDim = 256
                val scale = minOf(
                    maxDim.toFloat() / original.width.coerceAtLeast(1),
                    maxDim.toFloat() / original.height.coerceAtLeast(1),
                    1f
                )
                val targetW = (original.width * scale).toInt().coerceAtLeast(1)
                val targetH = (original.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(original, targetW, targetH, true)

                val avatarsDir = File(context.filesDir, "avatars").apply { mkdirs() }
                val outFile = File(avatarsDir, "${fileNamePrefix}_${System.currentTimeMillis()}.jpg")
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 78, baos)
                val bytes = baos.toByteArray()
                outFile.writeBytes(bytes)

                if (scaled != original) scaled.recycle()
                original.recycle()

                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                ProcessedAvatar(outFile.absolutePath, b64)
            }.getOrNull()
        }

    suspend fun saveBase64Avatar(context: Context, base64: String, fileKey: String): String? =
        withContext(Dispatchers.IO) {
            if (base64.isBlank()) return@withContext null
            runCatching {
                val bytes = Base64.decode(base64, Base64.NO_WRAP)
                val avatarsDir = File(context.filesDir, "avatars").apply { mkdirs() }
                val file = File(avatarsDir, "${fileKey.replace(Regex("[^a-zA-Z0-9_]"), "_")}.jpg")
                file.writeBytes(bytes)
                file.absolutePath
            }.getOrNull()
        }
}
