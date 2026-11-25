package com.shadowbooks.ui.reader

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal const val AN_NUR_AUDIO_ASSET = "books/quran-audio/an-nur-abdul-basit-r1.mp3"
private const val AN_NUR_AUDIO_FILE = "quran-audio/an-nur-abdul-basit-r1.mp3"

private data class QuranReciter(val label: String, val assetPath: String, val cacheFile: String)

private val QURAN_RECITERS = listOf(
    QuranReciter("بصوت عبدالباسط", AN_NUR_AUDIO_ASSET, AN_NUR_AUDIO_FILE),
    QuranReciter("اسلام صبحي", "books/quran-audio/an-nur-islam-sobhi.mp3", "quran-audio/an-nur-islam-sobhi.mp3"),
)

/** Quran-only translucent controls; the MP3 is bundled by the full-build preparation script. */
@Composable
internal fun QuranAudioPlayer(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var prepared by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }
    var playRequest by remember { mutableIntStateOf(0) }
    var seeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }
    var reciterIndex by remember { mutableIntStateOf(0) }
    val reciter = QURAN_RECITERS[reciterIndex]
    val selectReciter: (Int) -> Unit = { index ->
        if (index != reciterIndex) {
            reciterIndex = index
            runCatching { player?.release() }
            player = null
            prepared = false
            playing = false
            loading = false
            failed = false
            durationMs = 0
            positionMs = 0
            playRequest++
        }
    }

    LaunchedEffect(playRequest) {
        if (playRequest == 0) return@LaunchedEffect
        loading = true
        failed = false
        var created: MediaPlayer? = null
        try {
            val audio = copyBundledAudioIfNeeded(context, reciter)
            val media = MediaPlayer()
            created = media
            player = media
            media.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            media.setOnPreparedListener { ready ->
                durationMs = ready.duration.coerceAtLeast(0)
                positionMs = 0
                loading = false
                val started = runCatching { ready.start() }.isSuccess
                prepared = started
                failed = !started
                playing = started
            }
            media.setOnCompletionListener {
                positionMs = durationMs
                playing = false
            }
            media.setOnErrorListener { _, _, _ ->
                loading = false
                failed = true
                prepared = false
                playing = false
                durationMs = 0
                positionMs = 0
                if (player === media) player = null
                true
            }
            media.setDataSource(audio.absolutePath)
            media.prepareAsync()
        } catch (cancelled: CancellationException) {
            runCatching { created?.release() }
            if (player === created) player = null
            throw cancelled
        } catch (_: Exception) {
            runCatching { created?.release() }
            if (player === created) player = null
            loading = false
            failed = true
            prepared = false
            playing = false
        }
    }

    val activePlayer = player
    DisposableEffect(activePlayer) {
        onDispose { runCatching { activePlayer?.release() } }
    }
    DisposableEffect(lifecycle, activePlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (runCatching { activePlayer?.isPlaying == true }.getOrDefault(false)) {
                    runCatching { activePlayer?.pause() }
                }
                playing = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(activePlayer, prepared, seeking) {
        if (activePlayer != null && prepared) {
            while (true) {
                if (!seeking) {
                    val current = runCatching { activePlayer.currentPosition }.getOrNull()
                    if (current != null) positionMs = current.coerceIn(0, durationMs.coerceAtLeast(0))
                }
                delay(250)
            }
        }
    }

    val fraction = if (seeking) seekFraction else if (durationMs > 0) {
        positionMs.toFloat() / durationMs.toFloat()
    } else 0f
    val playPause: () -> Unit = {
        when {
            loading -> Unit
            prepared && activePlayer != null -> {
                if (playing) {
                    playing = runCatching { activePlayer.pause(); false }.getOrDefault(playing)
                } else {
                    if (durationMs > 0 && positionMs >= durationMs) {
                        runCatching { activePlayer.seekTo(0) }
                        positionMs = 0
                    }
                    playing = runCatching { activePlayer.start(); true }.getOrDefault(false)
                }
            }
            else -> playRequest++
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF071326).copy(alpha = 0.38f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QURAN_RECITERS.forEachIndexed { index, item ->
                    val selected = index == reciterIndex
                    Surface(
                        onClick = { selectReciter(index) },
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = if (selected) 0.28f else 0.08f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0.85f else 0.35f)),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            item.label,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        )
                    }
                }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatAudioTime(positionMs),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Slider(
                        value = fraction.coerceIn(0f, 1f),
                        onValueChange = {
                            seeking = true
                            seekFraction = it
                        },
                        onValueChangeFinished = {
                            if (durationMs > 0 && prepared) {
                                val target = (seekFraction * durationMs).toInt().coerceIn(0, durationMs)
                                runCatching { activePlayer?.seekTo(target) }
                                positionMs = target
                            }
                            seeking = false
                        },
                        enabled = prepared && durationMs > 0,
                        modifier = Modifier.weight(1f).height(38.dp).padding(horizontal = 4.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFF22C55E),
                            inactiveTrackColor = Color.White.copy(alpha = 0.42f),
                            disabledThumbColor = Color.White.copy(alpha = 0.55f),
                            disabledActiveTrackColor = Color(0xFF22C55E).copy(alpha = 0.52f),
                            disabledInactiveTrackColor = Color.White.copy(alpha = 0.24f),
                        ),
                    )
                    Text(
                        if (durationMs > 0) formatAudioTime(durationMs) else "--:--",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            if (prepared && durationMs > 0) {
                                val target = (positionMs - 10_000).coerceAtLeast(0)
                                runCatching { activePlayer?.seekTo(target) }
                                positionMs = target
                            }
                        },
                        enabled = prepared,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Filled.Replay10,
                            contentDescription = "إرجاع عشر ثوانٍ",
                            tint = Color.White.copy(alpha = if (prepared) 1f else 0.52f),
                            modifier = Modifier.size(31.dp),
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.78f)),
                        modifier = Modifier.size(58.dp),
                    ) {
                        Box(modifier = Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                            if (loading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(28.dp),
                                )
                            } else {
                                IconButton(onClick = playPause, modifier = Modifier.size(58.dp)) {
                                    Icon(
                                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (playing) "إيقاف مؤقت" else "تشغيل سورة النور ${reciter.label}",
                                        tint = Color.White,
                                        modifier = Modifier.size(33.dp),
                                    )
                                }
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            if (prepared && durationMs > 0) {
                                val target = (positionMs + 10_000).coerceAtMost(durationMs)
                                runCatching { activePlayer?.seekTo(target) }
                                positionMs = target
                            }
                        },
                        enabled = prepared,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Filled.Forward10,
                            contentDescription = "تقديم عشر ثوانٍ",
                            tint = Color.White.copy(alpha = if (prepared) 1f else 0.52f),
                            modifier = Modifier.size(31.dp),
                        )
                    }
                }
            }
            if (loading || failed) {
                Text(
                    text = if (failed) {
                        "تعذّر تجهيز التلاوة. أعد المحاولة من زر التشغيل."
                    } else {
                        "جارٍ تجهيز التلاوة من ملف التطبيق…"
                    },
                    color = Color.White.copy(alpha = 0.94f),
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                )
            }
        }
    }
}

private suspend fun copyBundledAudioIfNeeded(context: Context, reciter: QuranReciter): File = withContext(Dispatchers.IO) {
    val destination = File(context.filesDir, reciter.cacheFile)
    if (destination.isFile && destination.length() > 1_024L && hasMp3Signature(destination)) {
        return@withContext destination
    }

    val parent = checkNotNull(destination.parentFile)
    if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Could not create audio cache")
    val temporary = File(parent, "${destination.name}.part")
    temporary.delete()
    try {
        context.assets.open(reciter.assetPath).use { raw ->
            BufferedInputStream(raw).use { input ->
                BufferedOutputStream(FileOutputStream(temporary)).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
        }
        if (temporary.length() <= 1_024L || !hasMp3Signature(temporary)) {
            throw IOException("Bundled Quran recitation is not a valid MP3")
        }
        if (destination.exists() && !destination.delete()) throw IOException("Could not replace cached audio")
        if (!temporary.renameTo(destination)) throw IOException("Could not save cached audio")
        destination
    } finally {
        temporary.delete()
    }
}

private fun hasMp3Signature(file: File): Boolean = runCatching {
    file.inputStream().buffered().use { input ->
        val first = input.read()
        val second = input.read()
        val third = input.read()
        (first == 0x49 && second == 0x44 && third == 0x33) ||
            (first == 0xFF && second >= 0 && (second and 0xE0) == 0xE0)
    }
}.getOrDefault(false)

private fun formatAudioTime(milliseconds: Int): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000
    val minutes = seconds / 60
    val remainder = seconds % 60
    return String.format(Locale.ROOT, "%02d:%02d", minutes, remainder)
}
