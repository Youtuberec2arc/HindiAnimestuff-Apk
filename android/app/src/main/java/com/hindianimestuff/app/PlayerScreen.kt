@file:OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.hindianimestuff.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C as MC
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private data class AudioOpt(val label: String, val group: Tracks.Group, val index: Int, val selected: Boolean)

private fun fmt(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
fun PlayerScreen(
    vm: AppViewModel, id: String, startEp: String,
    onBack: () -> Unit, onOpen: (String) -> Unit, onDownloadAll: () -> Unit, onOpenPage: (String) -> Unit
) {
    val items by vm.items.collectAsState()
    val item = remember(items, id) { items.firstOrNull { it.id == id } }
    if (item == null || item.playableEps.isEmpty()) { NotFound(onBack); return }
    PlayerBody(vm, item, startEp, onBack, onOpen, onDownloadAll, onOpenPage)
}

@Composable
private fun PlayerBody(
    vm: AppViewModel, item: Item, startEp: String,
    onBack: () -> Unit, onOpen: (String) -> Unit, onDownloadAll: () -> Unit, onOpenPage: (String) -> Unit
) {
    val ctx = LocalContext.current
    val activity = remember { ctx.findActivity() }
    val view = androidx.compose.ui.platform.LocalView.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("progress", Context.MODE_PRIVATE) }
    val eps = item.playableEps

    fun posKey(i: Int) = "pos:${item.id}:${eps[i].key}"
    fun savedPos(i: Int): Long = prefs.getLong(posKey(i), 0L).let { if (it > 10_000L) it else 0L }

    val lastKey = prefs.getString("last:${item.id}", null)
    val initialIdx = remember {
        val want = startEp.ifBlank { lastKey ?: "" }
        eps.indexOfFirst { it.key == want }.let { if (it < 0) 0 else it }
    }

    var epIndex by remember { mutableStateOf(initialIdx) }
    var quality by remember { mutableStateOf(pickQuality(eps[initialIdx], prefs.getString("quality", null), emptySet())) }
    var pendingMs by remember { mutableStateOf(savedPos(initialIdx)) }
    var loadKey by remember { mutableStateOf(0) }
    val failed = remember { mutableSetOf<String>() }

    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var stopped by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var muted by remember { mutableStateOf(false) }
    var pos by remember { mutableStateOf(0L) }
    var dur by remember { mutableStateOf(0L) }
    var buf by remember { mutableStateOf(0L) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    var controls by remember { mutableStateOf(true) }
    var interaction by remember { mutableStateOf(0) }
    var audioOpts by remember { mutableStateOf<List<AudioOpt>>(emptyList()) }
    var qMenu by remember { mutableStateOf(false) }
    var aMenu by remember { mutableStateOf(false) }
    var fsBtn by remember { mutableStateOf(false) }

    val compact = LocalConfiguration.current.screenWidthDp < 480
    val btnSize = if (compact) 36.dp else 42.dp
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val fullscreen = fsBtn || landscape

    val exo = remember { ExoPlayer.Builder(ctx).build() }

    fun saveProgress() {
        if (!stopped && pos > 5_000 && dur > 0 && pos < dur - 20_000)
            prefs.edit().putLong(posKey(epIndex), pos).apply()
    }

    fun selectEp(i: Int, autoplayFromStart: Boolean = false) {
        if (i !in eps.indices) return
        saveProgress()
        epIndex = i
        failed.clear()
        stopped = false
        errorMsg = null
        quality = pickQuality(eps[i], prefs.getString("quality", null), emptySet())
        pendingMs = if (autoplayFromStart) 0L else savedPos(i)
        prefs.edit().putString("last:${item.id}", eps[i].key).apply()
        loadKey++
    }

    fun seekBy(delta: Long) {
        if (stopped) return
        val target = (exo.currentPosition + delta).coerceAtLeast(0L)
        exo.seekTo(if (exo.duration > 0) target.coerceAtMost(exo.duration - 300) else target)
        interaction++
    }

    fun togglePlay() {
        if (stopped) {
            stopped = false; pendingMs = 0L; errorMsg = null; loadKey++
        } else if (exo.isPlaying || exo.playWhenReady && exo.playbackState == Player.STATE_BUFFERING) {
            exo.pause()
        } else {
            if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0)
            exo.play()
        }
        interaction++
    }

    fun stopPlayback() {
        saveProgress()
        exo.stop()
        exo.clearMediaItems()
        stopped = true
        isPlaying = false
        buffering = false
        pos = 0; buf = 0
        prefs.edit().remove(posKey(epIndex)).apply()
        controls = true
    }

    fun setFullscreen(on: Boolean) {
        fsBtn = on
        activity?.requestedOrientation =
            if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (!on) scope.launch {
            delay(700)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // ---- (re)load media whenever episode / quality / explicit reload changes ----
    LaunchedEffect(epIndex, quality, loadKey) {
        if (stopped || quality.isEmpty()) return@LaunchedEffect
        val url = eps[epIndex].links[quality] ?: return@LaunchedEffect
        errorMsg = null
        buffering = true
        exo.setMediaItem(MediaItem.fromUri(url), pendingMs)
        exo.prepare()
        exo.playWhenReady = true
        pendingMs = 0L
    }

    // ---- player events ----
    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) {
                    prefs.edit().remove(posKey(epIndex)).apply()
                    if (epIndex < eps.size - 1) {
                        Toast.makeText(ctx, "Next: ${eps[epIndex + 1].label}", Toast.LENGTH_SHORT).show()
                        selectEp(epIndex + 1, autoplayFromStart = true)
                    } else {
                        controls = true
                    }
                }
            }
            override fun onTracksChanged(tracks: Tracks) {
                val out = ArrayList<AudioOpt>()
                var n = 1
                for (g in tracks.groups) {
                    if (g.type != MC.TRACK_TYPE_AUDIO) continue
                    for (i in 0 until g.length) {
                        val f = g.getTrackFormat(i)
                        val lang = f.language
                        val name = f.label
                            ?: lang?.let { Locale.forLanguageTag(it).displayLanguage.ifBlank { it } }
                            ?: "Track $n"
                        out.add(AudioOpt(name, g, i, g.isTrackSelected(i)))
                        n++
                    }
                }
                audioOpts = out
            }
            override fun onPlayerError(error: PlaybackException) {
                // 1080p x265 / unsupported file → quietly try the next quality
                failed.add("$epIndex:$quality")
                val remaining = eps[epIndex].links.keys.filter { "$epIndex:$it" !in failed }.toSet()
                val alt = pickQuality(Ep(eps[epIndex].key, eps[epIndex].label, eps[epIndex].links.filterKeys { it in remaining }), null, emptySet())
                if (alt.isNotEmpty()) {
                    Toast.makeText(ctx, "$quality can't play — trying $alt", Toast.LENGTH_SHORT).show()
                    pendingMs = exo.currentPosition
                    quality = alt
                } else {
                    errorMsg = "This file can't be played in the app. You can download it instead."
                    buffering = false
                }
            }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener) }
    }

    // ---- ticker: position + auto-save ----
    LaunchedEffect(exo) {
        var tick = 0
        while (true) {
            if (!stopped) {
                pos = exo.currentPosition
                dur = exo.duration.let { if (it > 0) it else 0L }
                buf = exo.bufferedPosition
                tick++
                if (tick % 10 == 0 && exo.isPlaying) saveProgress()
            }
            delay(500)
        }
    }

    // ---- auto-hide controls ----
    LaunchedEffect(controls, isPlaying, interaction) {
        if (controls && isPlaying) { delay(3200); controls = false }
    }

    // ---- fullscreen / system bars / keep screen on ----
    LaunchedEffect(fullscreen) {
        val w = activity?.window ?: return@LaunchedEffect
        val c = WindowCompat.getInsetsController(w, view)
        if (fullscreen) {
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(isPlaying) { view.keepScreenOn = isPlaying }
    BackHandler(fullscreen) { setFullscreen(false) }

    DisposableEffect(Unit) {
        onDispose {
            if (pos > 5_000 && dur > 0 && pos < dur - 20_000) prefs.edit().putLong(posKey(epIndex), pos).apply()
            exo.release()
            view.keepScreenOn = false
            activity?.let { a ->
                a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(a.window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // =============================== UI ===============================
    val videoBox: @Composable (Modifier) -> Unit = { mod ->
        Box(mod.background(Color.Black)) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        player = exo
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { it.player = exo },
                modifier = Modifier.fillMaxSize()
            )

            // tap layer: tap = show/hide controls, double-tap sides = ±10s, double-tap centre = play/pause
            Box(
                Modifier.fillMaxSize().pointerInput(stopped) {
                    detectTapGestures(
                        onTap = { controls = !controls; interaction++ },
                        onDoubleTap = { off ->
                            val w = size.width
                            when {
                                off.x < w * 0.38f -> { seekBy(-10_000); Toast.makeText(ctx, "−10s", Toast.LENGTH_SHORT).show() }
                                off.x > w * 0.62f -> { seekBy(10_000); Toast.makeText(ctx, "+10s", Toast.LENGTH_SHORT).show() }
                                else -> togglePlay()
                            }
                        }
                    )
                }
            )

            if (buffering && !stopped && errorMsg == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(44.dp), color = C.Gold)
            }

            if (stopped) {
                Box(
                    Modifier.fillMaxSize().background(Color(0xB3000000)).clickable { togglePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(70.dp).clip(RoundedCornerShape(999.dp)).background(C.Accent), contentAlignment = Alignment.Center) {
                        Ic(R.drawable.ic_play, Color.White, 38.dp)
                    }
                }
            }

            if (errorMsg != null) {
                Column(
                    Modifier.fillMaxSize().background(Color(0xD9000000)).padding(20.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Can't play this video here", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(errorMsg ?: "", color = Color(0xBFFFFFFF), fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton("Download", R.drawable.ic_download, C.Accent, Color.White) {
                            startDownload(ctx, item, eps[epIndex], quality.ifEmpty { eps[epIndex].links.keys.first() }, onOpenPage)
                        }
                        BigButton("Retry", R.drawable.ic_play, Color(0x33FFFFFF), Color.White) {
                            failed.clear(); errorMsg = null
                            quality = pickQuality(eps[epIndex], null, emptySet()); loadKey++
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = controls && !stopped && errorMsg == null,
                enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent, Color.Transparent, Color(0xD9000000)))
                    ),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // top bar
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        CtlBtn(R.drawable.ic_arrow_back) { if (fullscreen) setFullscreen(false) else onBack() }
                        Text(
                            item.displayTitle + if (eps.size > 1) " — ${eps[epIndex].label}" else "",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1,
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                        )
                    }

                    // bottom controls
                    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(fmt(scrub?.toLong() ?: pos), color = Color.White, fontSize = 12.sp, modifier = Modifier.width(44.dp))
                            Slider(
                                value = (scrub ?: pos.toFloat()).coerceIn(0f, (if (dur > 0) dur else 1L).toFloat()),
                                onValueChange = { scrub = it; interaction++ },
                                onValueChangeFinished = { scrub?.let { exo.seekTo(it.toLong()) }; scrub = null },
                                valueRange = 0f..(if (dur > 0) dur else 1L).toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = C.Accent, activeTrackColor = C.Accent,
                                    inactiveTrackColor = Color(0x40FFFFFF)
                                ),
                                modifier = Modifier.weight(1f).height(28.dp)
                            )
                            Text(fmt(dur), color = Color.White, fontSize = 12.sp, modifier = Modifier.width(44.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // left group takes only the space that is left, so it can never push the right group off screen
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                CtlBtn(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play, size = btnSize) { togglePlay() }
                                CtlBtn(R.drawable.ic_stop, size = btnSize) { stopPlayback() }
                                CtlBtn(R.drawable.ic_rewind, size = btnSize) { seekBy(-10_000) }
                                CtlBtn(R.drawable.ic_forward, size = btnSize) { seekBy(10_000) }
                                if (eps.size > 1) {
                                    if (!compact) CtlBtn(R.drawable.ic_skip_prev, enabled = epIndex > 0, size = btnSize) { selectEp(epIndex - 1, true) }
                                    CtlBtn(R.drawable.ic_skip_next, enabled = epIndex < eps.size - 1, size = btnSize) { selectEp(epIndex + 1, true) }
                                }
                            }
                            CtlBtn(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_up, size = btnSize) {
                                muted = !muted; exo.volume = if (muted) 0f else 1f; interaction++
                            }
                            if (audioOpts.size > 1) {
                                Box {
                                    CtlBtn(R.drawable.ic_audio, size = btnSize) { aMenu = true; interaction++ }
                                    DropdownMenu(expanded = aMenu, onDismissRequest = { aMenu = false }) {
                                        audioOpts.forEach { a ->
                                            DropdownMenuItem(
                                                text = { Text((if (a.selected) "✓ " else "") + a.label) },
                                                onClick = {
                                                    aMenu = false
                                                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                                                        .setOverrideForType(TrackSelectionOverride(a.group.mediaTrackGroup, a.index))
                                                        .build()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            Box {
                                Text(
                                    quality.ifEmpty { "—" }, color = Color.White, fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp, maxLines = 1, softWrap = false,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { qMenu = true; interaction++ }
                                        .padding(horizontal = 6.dp, vertical = 10.dp)
                                )
                                DropdownMenu(expanded = qMenu, onDismissRequest = { qMenu = false }) {
                                    sortedQualities(eps[epIndex].links).forEach { q ->
                                        DropdownMenuItem(
                                            text = { Text((if (q == quality) "✓ " else "") + q) },
                                            onClick = {
                                                qMenu = false
                                                if (q != quality) {
                                                    prefs.edit().putString("quality", q).apply()
                                                    pendingMs = exo.currentPosition
                                                    failed.clear()
                                                    quality = q
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            CtlBtn(if (fullscreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen, size = btnSize) { setFullscreen(!fullscreen) }
                        }
                    }
                }
            }
        }
    }

    if (fullscreen) {
        videoBox(Modifier.fillMaxSize())
    } else {
        Column(Modifier.fillMaxSize().background(C.Bg)) {
            videoBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            val related = remember(item) { vm.related(item) }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                item {
                    Text(item.title, color = C.Text, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    if (item.season.isNotBlank()) Text(item.season, color = C.Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    if (eps.size > 1) Text(eps[epIndex].label, color = C.Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton("Download", R.drawable.ic_download, C.Accent, Color.White, Modifier.weight(1f)) {
                            startDownload(ctx, item, eps[epIndex], quality.ifEmpty { eps[epIndex].links.keys.first() }, onOpenPage)
                        }
                        BigButton("All Links", R.drawable.ic_menu, C.Surface2, C.Text, Modifier.weight(1f), onClick = onDownloadAll)
                    }
                }
                if (eps.size > 1) {
                    item {
                        SectionTitle(if (item.eps.firstOrNull()?.label?.startsWith("Episode") == true) "Episodes" else "Parts")
                        EpisodeChips(item, eps[epIndex].key) { ep ->
                            eps.indexOfFirst { it.key == ep.key }.takeIf { it >= 0 }?.let { selectEp(it) }
                        }
                    }
                }
                item { SectionTitle("Information"); InfoBlock(item) }
                item { RelatedRow(related, onOpen) }
                item { TelegramCard { openUrl(ctx, Config.TELEGRAM) } }
                item { Spacer(Modifier.height(6.dp)); CommentsSection(item.id); Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun CtlBtn(icon: Int, enabled: Boolean = true, size: androidx.compose.ui.unit.Dp = 42.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(999.dp)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Ic(icon, if (enabled) Color.White else Color(0x55FFFFFF), if (size < 40.dp) 22.dp else 24.dp)
    }
}
