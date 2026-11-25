package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object VideoUtils {

    data class ProcessedVideo(
        val filePath: String,
        val base64: String,
        val durationMs: Long
    )

    private const val MAX_CLIP_DURATION_US = 3_000_000L // 3 seconds
    private const val MAX_LAN_VIDEO_BYTES = 4_500_000 // ~4.5 MB safe LAN limit

    suspend fun trimAndSaveVideo(
        context: Context,
        uri: Uri,
        fileNamePrefix: String,
        maxDurationMs: Long = 3000L
    ): Result<ProcessedVideo> = withContext(Dispatchers.IO) {
        runCatching {
            val videosDir = File(context.filesDir, "videos").apply { mkdirs() }
            val tempInput = File(videosDir, "tmp_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempInput.outputStream().use { out -> input.copyTo(out) }
            } ?: error("Could not read video from gallery")

            val outFile = File(videosDir, "${fileNamePrefix}_${System.currentTimeMillis()}.mp4")
            val targetDurationUs = maxDurationMs.coerceIn(500L, 5000L) * 1000L

            val trimmed = tryTrimMp4(tempInput, outFile, targetDurationUs)
            if (!trimmed) {
                tempInput.copyTo(outFile, overwrite = true)
            }
            tempInput.delete()

            if (!outFile.exists() || outFile.length() == 0L) {
                error("Video processing produced empty file")
            }
            if (outFile.length() > MAX_LAN_VIDEO_BYTES) {
                outFile.delete()
                error("Clip too large for LAN transfer (${outFile.length() / 1024}KB). Pick a shorter/smaller clip.")
            }

            val actualDuration = readDurationMs(outFile).coerceAtMost(maxDurationMs)
            val bytes = outFile.readBytes()
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            ProcessedVideo(outFile.absolutePath, b64, actualDuration)
        }
    }

    private fun tryTrimMp4(src: File, dst: File, maxDurationUs: Long): Boolean {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        return runCatching {
            extractor = MediaExtractor().apply { setDataSource(src.absolutePath) }
            val ext = extractor!!
            muxer = MediaMuxer(dst.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val mux = muxer!!

            val trackMap = mutableMapOf<Int, Int>()
            var maxBufferSize = 256 * 1024
            for (i in 0 until ext.trackCount) {
                val format = ext.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    ext.selectTrack(i)
                    val dstIdx = mux.addTrack(format)
                    trackMap[i] = dstIdx
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        val s = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        if (s > maxBufferSize) maxBufferSize = s
                    }
                }
            }
            if (trackMap.isEmpty()) return false

            runCatching {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(src.absolutePath)
                val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
                if (rot != null) mux.setOrientationHint(rot)
                retriever.release()
            }

            mux.start()
            val buffer = ByteBuffer.allocate(maxBufferSize)
            val info = MediaCodec.BufferInfo()
            var firstSampleTs = -1L

            while (true) {
                info.offset = 0
                info.size = ext.readSampleData(buffer, 0)
                if (info.size < 0) break

                val sampleTs = ext.sampleTime
                if (firstSampleTs < 0L) firstSampleTs = sampleTs
                val relTs = sampleTs - firstSampleTs
                if (relTs > maxDurationUs) break

                val srcTrack = ext.sampleTrackIndex
                val dstTrack = trackMap[srcTrack]
                if (dstTrack != null) {
                    info.presentationTimeUs = relTs.coerceAtLeast(0L)
                    @Suppress("DEPRECATION")
                    info.flags = if ((ext.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else 0
                    mux.writeSampleData(dstTrack, buffer, info)
                }
                ext.advance()
            }
            mux.stop()
            true
        }.getOrElse {
            dst.delete()
            false
        }.also {
            runCatching { muxer?.release() }
            runCatching { extractor?.release() }
        }
    }

    private fun readDurationMs(file: File): Long {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 3000L
            retriever.release()
            d
        }.getOrDefault(3000L)
    }

    suspend fun saveBase64Video(context: Context, base64: String, fileKey: String): String? =
        withContext(Dispatchers.IO) {
            if (base64.isBlank()) return@withContext null
            runCatching {
                val bytes = Base64.decode(base64, Base64.NO_WRAP)
                val videosDir = File(context.filesDir, "videos").apply { mkdirs() }
                val safeKey = fileKey.replace(Regex("[^a-zA-Z0-9_]"), "_")
                val file = File(videosDir, "$safeKey.mp4")
                if (!file.exists() || file.length() != bytes.size.toLong()) {
                    file.writeBytes(bytes)
                }
                file.absolutePath
            }.getOrNull()
        }
}
