package com.example.ui.components

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.ui.theme.ChatBackgroundTheme
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LoopingVideoBox(
    videoPath: String?,
    muted: Boolean = true,
    modifier: Modifier = Modifier
) {
    val currentMuted by rememberUpdatedState(muted)
    val file = videoPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0L }

    if (file == null) {
        Box(modifier = modifier.background(Color.Black.copy(alpha = 0.3f)))
        return
    }

    var mediaPlayerRef: MediaPlayer? = androidx.compose.runtime.remember(file.absolutePath) { null }

    DisposableEffect(file.absolutePath, muted) {
        mediaPlayerRef?.let { mp ->
            runCatching {
                val vol = if (muted) 0f else 1f
                mp.setVolume(vol, vol)
            }
        }
        onDispose { }
    }

    DisposableEffect(file.absolutePath) {
        onDispose {
            runCatching {
                mediaPlayerRef?.stop()
                mediaPlayerRef?.release()
            }
            mediaPlayerRef = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = false
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    private var surface: Surface? = null
                    private var mp: MediaPlayer? = null

                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                        val s = Surface(st)
                        surface = s
                        runCatching {
                            val player = MediaPlayer().apply {
                                setDataSource(file.absolutePath)
                                setSurface(s)
                                isLooping = true
                                setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                                setOnPreparedListener { prepared ->
                                    val vol = if (currentMuted) 0f else 1f
                                    prepared.setVolume(vol, vol)
                                    prepared.start()
                                }
                                prepareAsync()
                            }
                            mp = player
                            mediaPlayerRef = player
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = Unit

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        runCatching {
                            mp?.stop()
                            mp?.release()
                        }
                        mp = null
                        mediaPlayerRef = null
                        surface?.release()
                        surface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        }
    )
}

@Composable
fun ThemedChatBackground(
    theme: ChatBackgroundTheme,
    customImagePath: String? = null,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "bg3d")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(theme.bgTop, theme.bgBottom)))
    ) {
        val customFile = customImagePath?.let { File(it) }?.takeIf { it.exists() }
        if (theme == ChatBackgroundTheme.CUSTOM_GALLERY && customFile != null) {
            AsyncImage(
                model = customFile,
                contentDescription = "Custom chat background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.42f),
                                Color.Black.copy(alpha = 0.58f)
                            )
                        )
                    )
            )
        }

        // 3D Perspective Animated Layer for all themes (plus subtle floating particles on gallery)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            // Ambient radial 3D glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        theme.patternColor.copy(alpha = 0.16f * pulse),
                        theme.secondaryGlow.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(cx + cos(phase) * w * 0.12f, cy * 0.6f + sin(phase) * h * 0.08f),
                    radius = w * 0.85f
                ),
                center = Offset(cx + cos(phase) * w * 0.12f, cy * 0.6f + sin(phase) * h * 0.08f),
                radius = w * 0.85f
            )

            // 3D Perspective Grid Floor (gives true 3D depth across all themes)
            val horizonY = h * 0.48f
            val vanishX = cx + sin(phase) * w * 0.08f
            val gridColor = theme.patternColor.copy(alpha = if (theme == ChatBackgroundTheme.CUSTOM_GALLERY) 0.08f else 0.15f)
            for (i in -6..6) {
                val bottomX = cx + i * (w * 0.24f)
                drawLine(
                    color = gridColor,
                    start = Offset(vanishX, horizonY),
                    end = Offset(bottomX, h),
                    strokeWidth = 1.1f
                )
            }
            for (row in 1..8) {
                val t = ((row / 8f) + (phase / (2f * PI.toFloat())) * 0.25f) % 1f
                val depthY = horizonY + (h - horizonY) * (t * t)
                drawLine(
                    color = gridColor.copy(alpha = gridColor.alpha * t),
                    start = Offset(0f, depthY),
                    end = Offset(w, depthY),
                    strokeWidth = 1f
                )
            }

            when (theme) {
                ChatBackgroundTheme.SPIDY -> {
                    // 3D Rotating Spider-Web Rings & Radial Threads
                    val webX = cx + cos(phase * 0.5f) * 24f
                    val webY = cy * 0.72f + sin(phase * 0.5f) * 18f
                    val spokes = 12
                    val maxR = w * 0.58f
                    for (s in 0 until spokes) {
                        val angle = phase * 0.2f + (s * 2f * PI.toFloat() / spokes)
                        val ex = webX + cos(angle) * maxR
                        val ey = webY + sin(angle) * maxR * 0.85f
                        drawLine(
                            color = theme.patternColor.copy(alpha = 0.22f),
                            start = Offset(webX, webY),
                            end = Offset(ex, ey),
                            strokeWidth = 1.5f
                        )
                    }
                    for (ring in 1..5) {
                        val r = maxR * (ring / 5f) * (0.92f + 0.08f * sin(phase + ring))
                        val path = Path()
                        for (s in 0..spokes) {
                            val angle = phase * 0.2f + (s * 2f * PI.toFloat() / spokes)
                            val px = webX + cos(angle) * r
                            val py = webY + sin(angle) * r * 0.85f
                            if (s == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        drawPath(
                            path = path,
                            color = if (ring % 2 == 0) theme.secondaryGlow.copy(alpha = 0.2f) else theme.patternColor.copy(alpha = 0.22f),
                            style = Stroke(width = 1.3f)
                        )
                    }
                }

                ChatBackgroundTheme.GOJO -> {
                    // 3D Limitless Infinity Hollow Purple / Cyan Orbits
                    for (orb in 0..3) {
                        val orbitA = phase + orb * 1.57f
                        val ox = cx + cos(orbitA) * w * 0.26f
                        val oy = cy * 0.7f + sin(orbitA * 1.3f) * h * 0.14f
                        val radius = (32f + orb * 14f) * pulse
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(
                                    theme.patternColor.copy(alpha = 0.35f),
                                    theme.secondaryGlow.copy(alpha = 0.18f),
                                    Color.Transparent
                                ),
                                center = Offset(ox, oy),
                                radius = radius * 2.2f
                            ),
                            center = Offset(ox, oy),
                            radius = radius * 2.2f
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.25f),
                            radius = radius * 0.55f,
                            center = Offset(ox, oy),
                            style = Stroke(width = 1.6f)
                        )
                    }
                }

                ChatBackgroundTheme.SUKUNA -> {
                    // 3D Malevolent Shrine Crimson Slashes & Ring
                    for (i in 0..10) {
                        val t = ((i * 0.17f) + phase / (2f * PI.toFloat())) % 1f
                        val x1 = (w * ((i * 37 % 100) / 100f)) - 40f
                        val y1 = h * t
                        val angle = if (i % 2 == 0) 0.55f else -0.65f
                        val len = 130f + (i % 4) * 45f
                        drawLine(
                            color = if (i % 2 == 0) theme.patternColor.copy(alpha = 0.35f) else theme.secondaryGlow.copy(alpha = 0.28f),
                            start = Offset(x1, y1),
                            end = Offset(x1 + cos(angle) * len, y1 + sin(angle) * len),
                            strokeWidth = 2.6f
                        )
                    }
                    drawCircle(
                        color = theme.patternColor.copy(alpha = 0.24f * pulse),
                        radius = w * 0.32f,
                        center = Offset(cx, cy * 0.65f),
                        style = Stroke(width = 2.5f)
                    )
                }

                ChatBackgroundTheme.HACKER -> {
                    // 3D Falling Cyber Matrix Columns & Wireframe Cubes
                    val cols = 14
                    for (c in 0 until cols) {
                        val x = (c + 0.5f) * (w / cols)
                        val speed = 1f + (c % 3) * 0.5f
                        val yHead = ((phase * speed * 120f + c * 95f) % (h + 220f)) - 110f
                        drawLine(
                            brush = Brush.verticalGradient(
                                listOf(Color.Transparent, theme.patternColor.copy(alpha = 0.4f), Color.White.copy(alpha = 0.5f)),
                                startY = yHead - 140f,
                                endY = yHead
                            ),
                            start = Offset(x, yHead - 140f),
                            end = Offset(x, yHead),
                            strokeWidth = 2.2f
                        )
                    }
                }

                ChatBackgroundTheme.MAKIMA -> {
                    // 3D Rotating Golden Control Chains & Halo Rings
                    val haloY = cy * 0.58f
                    for (r in 1..4) {
                        val radius = (55f * r) * (0.94f + 0.06f * sin(phase + r))
                        drawCircle(
                            color = theme.patternColor.copy(alpha = 0.26f / r * 2f),
                            radius = radius,
                            center = Offset(cx, haloY),
                            style = Stroke(width = 2f)
                        )
                    }
                    for (i in 0..7) {
                        val a = phase * 0.4f + i * (PI.toFloat() / 4f)
                        drawLine(
                            color = theme.secondaryGlow.copy(alpha = 0.22f),
                            start = Offset(cx + cos(a) * 45f, haloY + sin(a) * 45f),
                            end = Offset(cx + cos(a) * w * 0.7f, haloY + sin(a) * h * 0.5f),
                            strokeWidth = 2f
                        )
                    }
                }

                ChatBackgroundTheme.YUTA -> {
                    // 3D Cursed Energy Waves & Twin Orbiting Rings
                    val cyPos = cy * 0.65f
                    for (k in 0..14) {
                        val angle = phase + k * (2f * PI.toFloat() / 15f)
                        val rx = cx + cos(angle) * w * 0.34f
                        val ry = cyPos + sin(angle * 2f) * h * 0.14f
                        drawCircle(
                            color = if (k % 2 == 0) theme.patternColor.copy(alpha = 0.38f) else theme.secondaryGlow.copy(alpha = 0.34f),
                            radius = 6f + 4f * sin(phase + k),
                            center = Offset(rx, ry)
                        )
                    }
                }

                ChatBackgroundTheme.CUSTOM_GALLERY -> {
                    // Subtle ambient 3D particles over user's Gallery photo
                    for (p in 0..16) {
                        val angle = phase * 0.5f + p * 0.8f
                        val px = ((p * 79f + cos(angle) * 40f) % w + w) % w
                        val py = ((p * 113f + sin(angle) * 50f) % h + h) % h
                        drawCircle(
                            color = Color.White.copy(alpha = 0.14f),
                            radius = 3.5f,
                            center = Offset(px, py)
                        )
                    }
                }
            }
        }
    }
}
