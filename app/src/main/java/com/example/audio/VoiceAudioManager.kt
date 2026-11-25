package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class VoiceRecordingState(
    val isRecording: Boolean = false,
    val elapsedMs: Long = 0L,
    val amplitudes: List<Float> = emptyList()
)

data class VoicePlaybackState(
    val playingMsgId: String? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L
)

data class RecordedVoiceResult(
    val filePath: String,
    val durationMs: Long,
    val base64: String
)

class VoiceAudioManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var recorder: MediaRecorder? = null
    private var activeFile: File? = null
    private var recordStartMs: Long = 0L
    private var meterJob: Job? = null

    private var player: MediaPlayer? = null
    private var playbackJob: Job? = null

    private val _recordingState = MutableStateFlow(VoiceRecordingState())
    val recordingState: StateFlow<VoiceRecordingState> = _recordingState.asStateFlow()

    private val _playbackState = MutableStateFlow(VoicePlaybackState())
    val playbackState: StateFlow<VoicePlaybackState> = _playbackState.asStateFlow()

    fun startRecording(): Boolean {
        stopPlayback()
        if (_recordingState.value.isRecording) return false
        return runCatching {
            val dir = File(context.filesDir, "voice").apply { mkdirs() }
            val outFile = File(dir, "voice_${System.currentTimeMillis()}.m4a")
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(48000)
                setAudioSamplingRate(24000)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            activeFile = outFile
            recordStartMs = System.currentTimeMillis()
            _recordingState.value = VoiceRecordingState(isRecording = true, elapsedMs = 0L, amplitudes = emptyList())

            meterJob?.cancel()
            meterJob = scope.launch(Dispatchers.Default) {
                while (isActive && _recordingState.value.isRecording) {
                    val amp = runCatching { (recorder?.maxAmplitude ?: 0) / 32767f }.getOrDefault(0f)
                        .coerceIn(0.08f, 1f)
                    val elapsed = System.currentTimeMillis() - recordStartMs
                    val currentList = _recordingState.value.amplitudes
                    val nextList = (currentList + amp).takeLast(28)
                    _recordingState.value = VoiceRecordingState(
                        isRecording = true,
                        elapsedMs = elapsed,
                        amplitudes = nextList
                    )
                    delay(65)
                }
            }
            true
        }.getOrElse {
            cancelRecording()
            false
        }
    }

    suspend fun stopAndSaveRecording(): RecordedVoiceResult? = withContext(Dispatchers.IO) {
        meterJob?.cancel()
        val duration = (System.currentTimeMillis() - recordStartMs).coerceAtLeast(0L)
        val file = activeFile
        runCatching {
            recorder?.apply {
                stop()
                release()
            }
        }
        recorder = null
        activeFile = null
        _recordingState.value = VoiceRecordingState()

        if (file == null || !file.exists() || duration < 350L) {
            file?.delete()
            return@withContext null
        }

        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return@withContext null
        if (bytes.isEmpty()) return@withContext null
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        RecordedVoiceResult(
            filePath = file.absolutePath,
            durationMs = duration,
            base64 = b64
        )
    }

    fun cancelRecording() {
        meterJob?.cancel()
        runCatching {
            recorder?.stop()
        }
        runCatching {
            recorder?.release()
        }
        recorder = null
        activeFile?.delete()
        activeFile = null
        _recordingState.value = VoiceRecordingState()
    }

    suspend fun saveIncomingVoiceBase64(msgId: String, base64: String): String? = withContext(Dispatchers.IO) {
        if (base64.isBlank()) return@withContext null
        runCatching {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val dir = File(context.filesDir, "voice").apply { mkdirs() }
            val file = File(dir, "rx_${msgId.replace(Regex("[^a-zA-Z0-9_]"), "_")}.m4a")
            file.writeBytes(bytes)
            file.absolutePath
        }.getOrNull()
    }

    fun togglePlay(msgId: String, filePath: String?, fallbackDurationMs: Long) {
        if (filePath.isNullOrBlank()) return
        val file = File(filePath)
        if (!file.exists()) return

        val current = _playbackState.value
        if (current.playingMsgId == msgId && current.isPlaying) {
            runCatching { player?.pause() }
            _playbackState.value = current.copy(isPlaying = false)
            return
        }
        if (current.playingMsgId == msgId && !current.isPlaying && player != null) {
            runCatching { player?.start() }
            _playbackState.value = current.copy(isPlaying = true)
            startProgressLoop(msgId)
            return
        }

        stopPlayback()
        runCatching {
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    stopPlayback()
                }
                start()
            }
            player = mp
            val total = mp.duration.toLong().takeIf { it > 0 } ?: fallbackDurationMs
            _playbackState.value = VoicePlaybackState(
                playingMsgId = msgId,
                isPlaying = true,
                progress = 0f,
                currentPositionMs = 0L,
                durationMs = total
            )
            startProgressLoop(msgId)
        }.onFailure {
            stopPlayback()
        }
    }

    private fun startProgressLoop(msgId: String) {
        playbackJob?.cancel()
        playbackJob = scope.launch(Dispatchers.Main) {
            while (isActive && _playbackState.value.playingMsgId == msgId && _playbackState.value.isPlaying) {
                val mp = player ?: break
                val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(0L)
                val dur = runCatching { mp.duration.toLong() }.getOrDefault(1L).coerceAtLeast(1L)
                _playbackState.value = _playbackState.value.copy(
                    currentPositionMs = pos,
                    durationMs = dur,
                    progress = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                )
                delay(60)
            }
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        runCatching {
            player?.stop()
        }
        runCatching {
            player?.release()
        }
        player = null
        _playbackState.value = VoicePlaybackState()
    }
}
