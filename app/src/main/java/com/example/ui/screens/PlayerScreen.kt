package com.example.ui.screens

import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.IptvChannel
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.NetflixLightGrey
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay

import androidx.media3.common.Tracks
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class PlayerTrack(
    val group: androidx.media3.common.TrackGroup,
    val index: Int,
    val name: String,
    val language: String?,
    val isSelected: Boolean
)

@OptIn(ExperimentalAnimationApi::class, androidx.media3.common.util.UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    channel: IptvChannel,
    isTvMode: Boolean,
    channels: List<IptvChannel>,
    onChannelSelected: (IptvChannel) -> Unit,
    onToggleFavorite: (IptvChannel) -> Unit,
    onBack: () -> Unit,
    onNextChannel: () -> Unit,
    onPrevChannel: () -> Unit,
    onNextEpisode: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Ekranın kapanmasını ve ekran koruyucunun devreye girmesini engelle (Keep screen on / awake)
    val activity = context as? android.app.Activity
    DisposableEffect(activity) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ExoPlayer Örneğini Yönetelim
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Ses ve Altyazı Seçim Durumları
    var audioTracks by remember { mutableStateOf<List<PlayerTrack>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<PlayerTrack>>(emptyList()) }
    var subtitlesDisabled by remember { mutableStateOf(false) }
    var showTrackSelection by remember { mutableStateOf(false) }

    // Oynatma Hızı Durumları
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var showSpeedSelection by remember { mutableStateOf(false) }

    // Sol Kanal Listesi Çekmecesi Görünürlüğü
    var showChannelsSidebar by remember { mutableStateOf(false) }

    // Hızlı Kanal Geçiş (Quick Zap) Çekmecesi Görünürlüğü
    var showQuickZapPanel by remember { mutableStateOf(false) }

    // Öneri 3: EPG / Yayın Akışı Bilgi Paneli Görünürlüğü
    var showEpgPanel by remember { mutableStateOf(false) }

    // Öneri 5: Uyku Zamanlayıcısı (Sleep Timer) Durumları
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var sleepTimerMinutes by remember { mutableStateOf(0) } // 0: Kapalı
    var sleepTimerRemainingSec by remember { mutableStateOf(0L) }

    // Otomatik sonraki bölüme geçme durumları
    var showNextEpisodePrompt by remember { mutableStateOf(false) }
    var nextEpisodeCountdown by remember { mutableStateOf(10) }

    // Akıllı Kategori İçi Zapping (Zap-lock within Category) Durumu ve Yardımcı Fonksiyonu
    val sharedPrefs = remember(context) { context.getSharedPreferences("zula_iptv_prefs", android.content.Context.MODE_PRIVATE) }
    var zapLockCategory by remember {
        mutableStateOf(sharedPrefs.getBoolean("zap_lock_category", false))
    }
    var lowLatencyMode by remember {
        mutableStateOf(sharedPrefs.getBoolean("low_latency_mode", true))
    }

    val errorRetryFocusRequester = remember { FocusRequester() }

    androidx.activity.compose.BackHandler(enabled = hasError) {
        onBack()
    }

    LaunchedEffect(hasError) {
        if (hasError) {
            try {
                errorRetryFocusRequester.requestFocus()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    val performZapping: (Boolean) -> Unit = { isNext ->
        if (zapLockCategory) {
            val currentGroup = channel.groupTitle
            val categoryChannels = channels.filter { it.groupTitle == currentGroup }
            if (categoryChannels.isNotEmpty()) {
                val currentIndex = categoryChannels.indexOfFirst { it.uniqueId == channel.uniqueId }
                val targetChannel = if (isNext) {
                    if (currentIndex != -1 && currentIndex < categoryChannels.lastIndex) {
                        categoryChannels[currentIndex + 1]
                    } else {
                        categoryChannels.first()
                    }
                } else {
                    if (currentIndex > 0) {
                        categoryChannels[currentIndex - 1]
                    } else {
                        categoryChannels.last()
                    }
                }
                onChannelSelected(targetChannel)
            } else {
                if (isNext) onNextChannel() else onPrevChannel()
            }
        } else {
            if (isNext) onNextChannel() else onPrevChannel()
        }
    }

    // VOD Pozisyon ve Süre Takip Durumları
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }

    val seekForwardCustom = {
        val dur = exoPlayer.duration
        if (dur > 0) {
            val newPos = (exoPlayer.currentPosition + 15000L).coerceAtMost(dur)
            exoPlayer.seekTo(newPos)
            position = newPos
        }
    }

    val seekBackCustom = {
        val newPos = (exoPlayer.currentPosition - 15000L).coerceAtLeast(0L)
        exoPlayer.seekTo(newPos)
        position = newPos
    }

    // En Boy Oranı (Aspect Ratio Mode)
    // 0: FIT, 1: STRETCH, 2: ZOOM
    var resizeMode by remember { mutableStateOf(0) }

    // HUD / Kontroller Görünürlüğü
    var showControls by remember { mutableStateOf(true) }

    // PlayerView referansı
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    // Kumanda D-Pad Dinleme Alanı (Görsel odaksız klavye dinleme)
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    val updateTracksState = {
        val currentTracks = exoPlayer.currentTracks
        val audios = mutableListOf<PlayerTrack>()
        val subtitles = mutableListOf<PlayerTrack>()
        subtitlesDisabled = exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)

        for (group in currentTracks.groups) {
            val trackType = group.type
            val mediaTrackGroup = group.mediaTrackGroup
            for (i in 0 until group.length) {
                if (group.isTrackSupported(i)) {
                    val format = group.getTrackFormat(i)
                    val isSelected = group.isTrackSelected(i)
                    val language = format.language ?: ""
                    
                    val formatLabel = format.label
                    val label = when {
                        !formatLabel.isNullOrBlank() -> formatLabel
                        language.equals("tur", ignoreCase = true) || language.equals("tr", ignoreCase = true) -> "Türkçe"
                        language.equals("eng", ignoreCase = true) || language.equals("en", ignoreCase = true) -> "İngilizce"
                        language.equals("fra", ignoreCase = true) || language.equals("fr", ignoreCase = true) -> "Fransızca"
                        language.equals("deu", ignoreCase = true) || language.equals("de", ignoreCase = true) -> "Almanca"
                        language.equals("ita", ignoreCase = true) || language.equals("it", ignoreCase = true) -> "İtalyanca"
                        language.equals("spa", ignoreCase = true) || language.equals("es", ignoreCase = true) -> "İspanyolca"
                        language.equals("rus", ignoreCase = true) || language.equals("ru", ignoreCase = true) -> "Rusça"
                        language.equals("ara", ignoreCase = true) || language.equals("ar", ignoreCase = true) -> "Arapça"
                        language.isNotEmpty() -> language.uppercase()
                        else -> "Ses/Parça ${i + 1}"
                    }
                    
                    val tr = PlayerTrack(
                        group = mediaTrackGroup,
                        index = i,
                        name = label,
                        language = language,
                        isSelected = isSelected
                    )
                    
                    if (trackType == C.TRACK_TYPE_AUDIO) {
                        audios.add(tr)
                    } else if (trackType == C.TRACK_TYPE_TEXT) {
                        subtitles.add(tr)
                    }
                }
            }
        }
        audioTracks = audios
        subtitleTracks = subtitles
    }

    // Veri Akışı / Kanal Değiştiğinde Player'ı güncelle
    LaunchedEffect(channel, lowLatencyMode) {
        isLoading = true
        hasError = false
        errorMessage = ""
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        
        val mediaItem = if (channel.type == "LIVE" && lowLatencyMode) {
            MediaItem.Builder()
                .setUri(channel.streamUrl)
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(3000)     // Target 3 seconds offset from live edge
                        .setMinPlaybackSpeed(0.95f)   // Slow down slightly if buffer is low
                        .setMaxPlaybackSpeed(1.05f)   // Catch up to live edge if ahead
                        .build()
                )
                .build()
        } else {
            MediaItem.fromUri(channel.streamUrl)
        }
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        
        // Kaldığı yerden devam etme (Resume position for VOD: MOVIE or SERIES)
        if (channel.type != "LIVE") {
            val savedPosKey = "resume_pos_${channel.uniqueId}"
            val savedPos = sharedPrefs.getLong(savedPosKey, 0L)
            if (savedPos > 0L) {
                exoPlayer.seekTo(savedPos)
                position = savedPos
            }
        }
        
        exoPlayer.play()
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    // Oynatma Hızı değiştiğinde ExoPlayer'a bildir
    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    // VOD yayınlar için ilerleme saniye takibi ve kaldığı yeri kaydetme
    LaunchedEffect(isPlaying, channel) {
        if (channel.type != "LIVE") {
            try {
                var tickCount = 0
                while (true) {
                    val pos = exoPlayer.currentPosition
                    val dur = exoPlayer.duration.coerceAtLeast(0L)
                    position = pos
                    duration = dur
                    
                    tickCount++
                    if (pos > 0L && tickCount % 10 == 0) { // Sadece 10 saniyede bir diske yaz (SharedPrefs disk yükünü %90 azaltır)
                        sharedPrefs.edit().putLong("resume_pos_${channel.uniqueId}", pos).apply()
                    }
                    delay(1000)
                }
            } finally {
                // Ekrana veda ederken veya kanal değişirken son pozisyonu hızlıca kaydet
                try {
                    val pos = exoPlayer.currentPosition
                    if (pos > 0L) {
                        sharedPrefs.edit().putLong("resume_pos_${channel.uniqueId}", pos).apply()
                    }
                } catch (e: Exception) {}
            }
        }
    }

    // Öneri 5: Uyku Zamanlayıcısı (Sleep Timer) Sayacı
    LaunchedEffect(sleepTimerMinutes) {
        if (sleepTimerMinutes > 0) {
            sleepTimerRemainingSec = sleepTimerMinutes * 60L
            while (sleepTimerRemainingSec > 0) {
                delay(1000)
                // Use a local copy to avoid immediate multiple recompositions or track accurately
                sleepTimerRemainingSec--
            }
            // Süre dolduğunda yayını duraklat ve çıkış yap
            try {
                exoPlayer.pause()
            } catch (e: Exception) {}
            onBack()
        } else {
            sleepTimerRemainingSec = 0L
        }
    }

    // Otomatik sonraki bölüme geçiş sayacı döngüsü
    LaunchedEffect(showNextEpisodePrompt) {
        if (showNextEpisodePrompt) {
            nextEpisodeCountdown = 10
            while (nextEpisodeCountdown > 0) {
                delay(1000)
                nextEpisodeCountdown--
            }
            showNextEpisodePrompt = false
            onNextEpisode?.invoke()
        }
    }

    // ExoPlayer Event Listener'ı Kaydedelim
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingParam: Boolean) {
                isPlaying = isPlayingParam
            }

            override fun onTracksChanged(tracks: Tracks) {
                updateTracksState()
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        isLoading = true
                    }
                    Player.STATE_READY -> {
                        isLoading = false
                        hasError = false
                        updateTracksState()
                    }
                    Player.STATE_ENDED -> {
                        isLoading = false
                        if (channel.type == "SERIES" && onNextEpisode != null) {
                            showNextEpisodePrompt = true
                        }
                    }
                    Player.STATE_IDLE -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isLoading = false
                hasError = true
                errorMessage = "Grup/Yayın akışı çözülemedi (Hata kodu: ${error.errorCodeName})"
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    val playPauseFocusRequester = remember { FocusRequester() }

    // Auto-hide controls timer
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(5000)
            showControls = false
        }
    }

    // Dynamic TV Focus Steering only on Mobile/Tablet
    LaunchedEffect(showControls) {
        if (!isTvMode && showControls) {
            delay(150)
            try {
                playPauseFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Focus request failed
            }
        }
    }

    // Direct TV remote focus to ExoPlayer's native PlayerView, or fallback to custom on Mobile
    LaunchedEffect(playerViewRef) {
        if (isTvMode) {
            delay(400)
            playerViewRef?.let {
                it.isFocusable = true
                it.isFocusableInTouchMode = true
                it.requestFocus()
            }
        } else {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (!isTvMode) {
                    Modifier
                        .focusRequester(focusRequester)
                        .focusable()
                } else {
                    Modifier
                }
            )
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    if (isTvMode) {
                        if (keyEvent.key == Key.Back || keyEvent.key == Key.Escape) {
                            val pView = playerViewRef
                            if (pView != null && pView.isControllerFullyVisible) {
                                pView.hideController()
                                true
                            } else {
                                scope.launch { onBack() }
                                true
                            }
                        } else {
                            // Let native PlayerView process all DPAD and media remote keys natively
                            false
                        }
                    } else {
                        when (keyEvent.key) {
                            Key.MediaPlay -> {
                                exoPlayer.play()
                                true
                            }
                            Key.MediaPause -> {
                                exoPlayer.pause()
                                true
                            }
                            Key.MediaPlayPause -> {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                true
                            }
                            Key.MediaFastForward -> {
                                if (channel.type != "LIVE") {
                                    seekForwardCustom()
                                    showControls = true
                                }
                                true
                            }
                            Key.MediaRewind -> {
                                if (channel.type != "LIVE") {
                                    seekBackCustom()
                                    showControls = true
                                }
                                true
                            }
                            Key.MediaNext -> {
                                performZapping(true)
                                showControls = true
                                true
                            }
                            Key.MediaPrevious -> {
                                performZapping(false)
                                showControls = true
                                true
                            }
                            else -> {
                                if (showControls) {
                                    // When controls are visible, we allow focus navigation to find and highlight target buttons.
                                    // However, we intercept ESC/BACK to close/hide the controls overlay safely.
                                    if (keyEvent.key == Key.Escape || keyEvent.key == Key.Back) {
                                        showControls = false
                                        focusRequester.requestFocus()
                                        true
                                    } else {
                                        false
                                    }
                                } else {
                                    // When controls are hidden, pressing directional keys executes simple quick actions.
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> {
                                            performZapping(false)
                                            showControls = true
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            performZapping(true)
                                            showControls = true
                                            true
                                        }
                                        Key.DirectionLeft -> {
                                            if (channel.type != "LIVE") {
                                                seekBackCustom()
                                                showControls = true
                                            } else {
                                                showControls = true
                                            }
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            if (channel.type != "LIVE") {
                                                seekForwardCustom()
                                                showControls = true
                                            } else {
                                                showControls = true
                                            }
                                            true
                                        }
                                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                            showControls = true
                                            true
                                        }
                                        Key.Escape, Key.Back -> {
                                            scope.launch { onBack() }
                                            true
                                        }
                                        else -> false
                                    }
                                }
                            }
                        }
                    }
                } else {
                    false
                }
            }
            .clickable { if (!isTvMode) showControls = !showControls }
            .testTag("player_screen_root")
    ) {
        // NATIVE ANDROIDX MEDIA3 PLAYER
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = isTvMode // TRUE for TV mode (enables Google's native TV controller and scrubbing)
                    keepScreenOn = true // Ekran kapanmasını önle
                    setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    playerViewRef = this
                    if (isTvMode) {
                        isFocusable = true
                        isFocusableInTouchMode = true
                        
                        // Controller kapandığında odağı (focus) tekrar PlayerView'a geri al
                        setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                            if (visibility == android.view.View.GONE || visibility == android.view.View.INVISIBLE) {
                                isFocusable = true
                                isFocusableInTouchMode = true
                                requestFocus()
                            }
                        })

                        setOnKeyListener { _, keyCode, event ->
                            val isDown = event.action == android.view.KeyEvent.ACTION_DOWN
                            val isUp = event.action == android.view.KeyEvent.ACTION_UP
                            if (isDown || isUp) {
                                when (keyCode) {
                                    android.view.KeyEvent.KEYCODE_MENU,
                                    android.view.KeyEvent.KEYCODE_CAPTIONS,
                                    android.view.KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK,
                                    android.view.KeyEvent.KEYCODE_PROG_YELLOW,
                                    android.view.KeyEvent.KEYCODE_PROG_GREEN,
                                    android.view.KeyEvent.KEYCODE_I,
                                    android.view.KeyEvent.KEYCODE_S -> {
                                        if (isDown) {
                                            showTrackSelection = true
                                        }
                                        true
                                    }
                                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                                    android.view.KeyEvent.KEYCODE_ENTER,
                                    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                        if (!isControllerFullyVisible) {
                                            if (isDown) {
                                                showController()
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    android.view.KeyEvent.KEYCODE_BACK,
                                    android.view.KeyEvent.KEYCODE_ESCAPE -> {
                                        if (isControllerFullyVisible) {
                                            if (isDown) {
                                                hideController()
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                        if (!isControllerFullyVisible) {
                                            if (isDown) {
                                                if (channel.type == "SERIES" || channel.type == "VOD" || channel.type == "MOVIE") {
                                                    showTrackSelection = true
                                                } else {
                                                    performZapping(false)
                                                }
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        if (!isControllerFullyVisible) {
                                            if (isDown) {
                                                if (channel.type == "SERIES" || channel.type == "VOD" || channel.type == "MOVIE") {
                                                    showTrackSelection = true
                                                } else {
                                                    performZapping(true)
                                                }
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        requestFocus()
                    }
                }
            },
            update = { playerView ->
                // Ekran En Boy Boyutu Ayarları
                playerView.resizeMode = when (resizeMode) {
                    0 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                playerViewRef = playerView
                playerView.useController = isTvMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // LOADING SPINNER
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = NetflixRed, modifier = Modifier.size(52.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Yayın Akışı Yükleniyor...",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Kanal: ${channel.name}",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // HATA EKRANI
        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NewReleases,
                        contentDescription = null,
                        tint = NetflixRed,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Yayın Yüklenemedi",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = Color.Gray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        val retryInteractionSource = remember { MutableInteractionSource() }
                        val exitInteractionSource = remember { MutableInteractionSource() }

                        Button(
                            onClick = {
                                hasError = false
                                isLoading = true
                                exoPlayer.prepare()
                                exoPlayer.play()
                            },
                            interactionSource = retryInteractionSource,
                            colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .focusRequester(errorRetryFocusRequester)
                                .focusable(interactionSource = retryInteractionSource)
                                .tvFocusBorder(isTvMode = isTvMode, interactionSource = retryInteractionSource)
                                .testTag("player_retry_button")
                        ) {
                            Text("Yeniden Dene", color = Color.White)
                        }
                        Button(
                            onClick = onBack,
                            interactionSource = exitInteractionSource,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .focusable(interactionSource = exitInteractionSource)
                                .tvFocusBorder(isTvMode = isTvMode, interactionSource = exitInteractionSource)
                                .testTag("player_exit_button")
                        ) {
                            Text("Çıkış Yap", color = Color.White)
                        }
                    }
                }
            }
        }

        // COMPOSE PLAYER HUD (KONTROLLER)
        AnimatedVisibility(
            visible = showControls && !isTvMode,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // ÜST PANEL: Kanal Adı, Sık Kullanılanlar ve Geri Butonu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val backInteractionSource = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = onBack,
                        interactionSource = backInteractionSource,
                        modifier = Modifier
                            .testTag("player_back_button")
                            .focusable(interactionSource = backInteractionSource)
                            .tvFocusBorder(isTvMode = isTvMode, interactionSource = backInteractionSource)
                    ) {
                        Icon(Icons.Default.ArrowBack, "Geri Dön", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = channel.name,
                            color = Color.White,
                            fontSize = if (isTvMode) 18.sp else 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = channel.groupTitle ?: "CSD Premium",
                            color = NetflixRed,
                            fontSize = if (isTvMode) 11.sp else 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // SIK KULLANILANLARA EKLE/ÇIKAR (FAVORITE ICON BUTTON)
                    val starInteractionSource = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = { onToggleFavorite(channel) },
                        interactionSource = starInteractionSource,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .focusable(interactionSource = starInteractionSource)
                            .tvFocusBorder(isTvMode = isTvMode, interactionSource = starInteractionSource, shape = RoundedCornerShape(8.dp))
                            .testTag("player_favorite_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (channel.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorilere Ekle / Çıkar",
                            tint = if (channel.isFavorite) Color.Yellow else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Tip (LIVE vs VOD)
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (channel.type == "LIVE") NetflixRed else Color.DarkGray
                    ) {
                        Text(
                            text = if (channel.type == "LIVE") "CANLI TV" else "SİNEMA / VOD",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // ORTA PANEL: Oynat, Durdur, Zapping ve İleri Geri Butonları
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.extraLarge)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (channel.type != "LIVE") {
                        // VOD (SİNEMA & FİLM) OYNATICI REHBERİ
                        // 1. Önceki Kanal/Video (Skip Previous)
                        val prevInteractionSource = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = { performZapping(false) },
                            interactionSource = prevInteractionSource,
                            modifier = Modifier
                                .focusable(interactionSource = prevInteractionSource)
                                .tvFocusBorder(isTvMode = isTvMode, interactionSource = prevInteractionSource)
                        ) {
                            Icon(Icons.Default.SkipPrevious, "Önceki", tint = Color.White, modifier = Modifier.size(36.dp))
                        }

                        // 2. Geri Sar (Fast Rewind 15sn)
                        val rewindInteractionSource = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = seekBackCustom,
                            interactionSource = rewindInteractionSource,
                            modifier = Modifier
                                .focusable(interactionSource = rewindInteractionSource)
                                .tvFocusBorder(isTvMode = isTvMode, interactionSource = rewindInteractionSource)
                        ) {
                            Icon(Icons.Default.FastRewind, "Geri Sar (15s)", tint = Color.White, modifier = Modifier.size(36.dp))
                        }

                        // 3. Oynat / Duraklat
                        val playInteractionSource = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            },
                            interactionSource = playInteractionSource,
                            modifier = Modifier
                                .focusRequester(playPauseFocusRequester)
                                .focusable(interactionSource = playInteractionSource)
                                .tvFocusBorder(isTvMode = isTvMode, interactionSource = playInteractionSource)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                                contentDescription = "Oynatma Butonu",
                                tint = NetflixRed,
                                modifier = Modifier.size(56.dp)
                            )
                        }

                        // 4. İleri Sar (Fast Forward 15sn)
                        val ffInteractionSource = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = seekForwardCustom,
                            interactionSource = ffInteractionSource,
                            modifier = Modifier
                                .focusable(interactionSource = ffInteractionSource)
                                .tvFocusBorder(isTvMode = isTvMode, interactionSource = ffInteractionSource)
                        ) {
                            Icon(Icons.Default.FastForward, "İleri Sar (15s)", tint = Color.White, modifier = Modifier.size(36.dp))
                        }

                        // 5. Sonraki Kanal/Video (Skip Next)
                        val nextInteractionSource = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = { performZapping(true) },
                            interactionSource = nextInteractionSource,
                            modifier = Modifier
                                .focusable(interactionSource = nextInteractionSource)
                                .tvFocusBorder(isTvMode = isTvMode, interactionSource = nextInteractionSource)
                        ) {
                            Icon(Icons.Default.SkipNext, "Sonraki", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    } else {
                        // LIVE TV (CANLI YAYIN) OYNATICI REHBERİ
                        // 1. Önceki Kanal (Zapping)
                        val prevInteractionSource = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = { performZapping(false) },
                            interactionSource = prevInteractionSource,
                            modifier = Modifier
                                .focusable(interactionSource = prevInteractionSource)
                                .tvFocusBorder(isTvMode = isTvMode, interactionSource = prevInteractionSource)
                        ) {
                            Icon(Icons.Default.SkipPrevious, "Önceki Kanal", tint = Color.White, modifier = Modifier.size(36.dp))
                        }

                        // 2. Oynat / Duraklat
                        val playInteractionSource = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            },
                            interactionSource = playInteractionSource,
                            modifier = Modifier
                                .focusRequester(playPauseFocusRequester)
                                .focusable(interactionSource = playInteractionSource)
                                .tvFocusBorder(isTvMode = isTvMode, interactionSource = playInteractionSource)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                                contentDescription = "Oynatma Butonu",
                                tint = NetflixRed,
                                modifier = Modifier.size(56.dp)
                            )
                        }

                        // 3. Sonraki Kanal (Zapping)
                        val nextInteractionSource = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = { performZapping(true) },
                            interactionSource = nextInteractionSource,
                            modifier = Modifier
                                .focusable(interactionSource = nextInteractionSource)
                                .tvFocusBorder(isTvMode = isTvMode, interactionSource = nextInteractionSource)
                        ) {
                            Icon(Icons.Default.SkipNext, "Sonraki Kanal", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }
                }

                // ALT PANEL: En-boy oranı, Progress/Seekbar Slider, Ses & Altyazı Seçici ve Kumanda İpucu
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.82f))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    // VOD için Sürüklenebilir İlerleme Çubuğu (SeekBar) veya Doğrusal İlerleme Barı (TV)
                    if (channel.type != "LIVE") {
                        val pos = position
                        val dur = duration
                        val progress = if (dur > 0) pos.toFloat() / dur.toFloat() else 0f
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = formatTime(pos),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            if (isTvMode) {
                                // TV için yüksek görünürlüklü, bükülmeyen ve kumanda ile zenginleşen doğrusal ilerleme barı
                                LinearProgressIndicator(
                                    progress = progress,
                                    color = NetflixRed,
                                    trackColor = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .testTag("player_vod_seekbar_tv")
                                )
                            } else {
                                Slider(
                                    value = progress,
                                    onValueChange = { newVal ->
                                        val targetPos = (newVal * dur).toLong()
                                        exoPlayer.seekTo(targetPos)
                                        position = targetPos
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = NetflixRed,
                                        activeTrackColor = NetflixRed,
                                        inactiveTrackColor = Color.DarkGray
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(16.dp)
                                        .testTag("player_vod_seekbar")
                                )
                            }

                            Text(
                                text = formatTime(dur),
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // CANLI YAYIN Göstergeci
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(NetflixRed, androidx.compose.foundation.shape.CircleShape)
                            )
                            Text(
                                text = "CANLI YAYIN - GERÇEK ZAMANLI AKIŞ",
                                color = Color.LightGray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (isTvMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Kumanda Kılavuzu (Compact & Clean)
                            Column(
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                Text(
                                    text = "KUMANDA KILAVUZU",
                                    color = NetflixRed,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "▲/▼ Zapping • OK Oynat/Duraklat\n◀/▶ Geri/İleri Sar (Sinema)",
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Reorganized Settings Rows (2 neat rows of buttons so they never squeeze or distort)
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                // Row 1: Drawer Sidebars & Key dialogues
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Hızlı Kanal Listesi (Sidebar) Butonu
                                    val sidebarInteractionSource = remember { MutableInteractionSource() }
                                    Button(
                                        onClick = { showChannelsSidebar = true },
                                        interactionSource = sidebarInteractionSource,
                                        colors = ButtonDefaults.buttonColors(containerColor = NetflixLightGrey),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier
                                            .tvFocusBorder(isTvMode = isTvMode, interactionSource = sidebarInteractionSource, shape = MaterialTheme.shapes.small)
                                            .testTag("player_sidebar_button")
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Menu, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Kanal Listesi", color = Color.White, fontSize = 11.sp)
                                        }
                                    }

                                    // Öneri 1: Hızlı Kanal Geçiş (Quick Zap) Butonu
                                    val quickZapInteractionSource = remember { MutableInteractionSource() }
                                    Button(
                                        onClick = { showQuickZapPanel = true },
                                        interactionSource = quickZapInteractionSource,
                                        colors = ButtonDefaults.buttonColors(containerColor = NetflixLightGrey),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier
                                            .tvFocusBorder(isTvMode = isTvMode, interactionSource = quickZapInteractionSource, shape = MaterialTheme.shapes.small)
                                            .testTag("player_quick_zap_button")
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Bolt, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Hızlı Geçiş", color = Color.White, fontSize = 11.sp)
                                        }
                                    }

                                    // Öneri 3: Yayın Akışı (EPG)
                                    val epgInteractionSource = remember { MutableInteractionSource() }
                                    Button(
                                        onClick = { showEpgPanel = true },
                                        interactionSource = epgInteractionSource,
                                        colors = ButtonDefaults.buttonColors(containerColor = NetflixLightGrey),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier
                                            .tvFocusBorder(isTvMode = isTvMode, interactionSource = epgInteractionSource, shape = MaterialTheme.shapes.small)
                                            .testTag("player_epg_button")
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Schedule, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("EPG / Yayın Akışı", color = Color.White, fontSize = 11.sp)
                                        }
                                    }

                                    // Ses ve Altyazı Seçim Butonu (Netflix tarzı)
                                    val trackInteractionSource = remember { MutableInteractionSource() }
                                    Button(
                                        onClick = { showTrackSelection = true },
                                        interactionSource = trackInteractionSource,
                                        colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier
                                            .tvFocusBorder(isTvMode = isTvMode, interactionSource = trackInteractionSource, shape = MaterialTheme.shapes.small)
                                            .testTag("player_tracks_button")
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Subtitles, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Ses & Altyazı", color = Color.White, fontSize = 11.sp)
                                        }
                                    }

                                    // Öneri/Tasarım Konsepti: Akıllı Kategori İçi Zapping (Zap-lock) Butonu
                                    val zapLockInteractionSource = remember { MutableInteractionSource() }
                                    Button(
                                        onClick = {
                                            zapLockCategory = !zapLockCategory
                                            sharedPrefs.edit().putBoolean("zap_lock_category", zapLockCategory).apply()
                                        },
                                        interactionSource = zapLockInteractionSource,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (zapLockCategory) NetflixRed else NetflixLightGrey
                                        ),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier
                                            .tvFocusBorder(isTvMode = isTvMode, interactionSource = zapLockInteractionSource, shape = MaterialTheme.shapes.small)
                                            .testTag("player_zap_lock_button")
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (zapLockCategory) Icons.Default.Lock else Icons.Default.LockOpen,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (zapLockCategory) "Grup Kilitli" else "Grup Açık",
                                                color = Color.White,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    // Düşük Gecikme Modu (Low Latency / Hızlı Yayın) Butonu
                                    if (channel.type == "LIVE") {
                                        val lowLatencyInteractionSource = remember { MutableInteractionSource() }
                                        Button(
                                            onClick = {
                                                lowLatencyMode = !lowLatencyMode
                                                sharedPrefs.edit().putBoolean("low_latency_mode", lowLatencyMode).apply()
                                            },
                                            interactionSource = lowLatencyInteractionSource,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (lowLatencyMode) NetflixRed else NetflixLightGrey
                                            ),
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier
                                                .tvFocusBorder(isTvMode = isTvMode, interactionSource = lowLatencyInteractionSource, shape = MaterialTheme.shapes.small)
                                                .testTag("player_low_latency_button")
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Bolt,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (lowLatencyMode) "Düşük Gecikme: Açık" else "Düşük Gecikme: Kapalı",
                                                    color = Color.White,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                // Row 2: Secondary customization & auxiliary tools
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // En Boy Değiştirici
                                    val aspectInteractionSource = remember { MutableInteractionSource() }
                                    Button(
                                        onClick = { resizeMode = (resizeMode + 1) % 3 },
                                        interactionSource = aspectInteractionSource,
                                        colors = ButtonDefaults.buttonColors(containerColor = NetflixLightGrey),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier
                                            .tvFocusBorder(isTvMode = isTvMode, interactionSource = aspectInteractionSource, shape = MaterialTheme.shapes.small)
                                            .testTag("player_aspect_ratio_button")
                                    ) {
                                        val resizeLabel = when (resizeMode) {
                                            0 -> "En-Boy: Orijinal"
                                            1 -> "En-Boy: Ekrana Yay"
                                            2 -> "En-Boy: Akıllı Yakınlaştır"
                                            else -> "En-Boy"
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.AspectRatio, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(resizeLabel, color = Color.White, fontSize = 11.sp)
                                        }
                                    }

                                    // Oynatma Hızı Değiştirici
                                    val speedInteractionSource = remember { MutableInteractionSource() }
                                    Button(
                                        onClick = { showSpeedSelection = true },
                                        interactionSource = speedInteractionSource,
                                        colors = ButtonDefaults.buttonColors(containerColor = NetflixLightGrey),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier
                                            .tvFocusBorder(isTvMode = isTvMode, interactionSource = speedInteractionSource, shape = MaterialTheme.shapes.small)
                                            .testTag("player_speed_button")
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Speed, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (playbackSpeed == 1.0f) "Hız: 1.0x" else "Hız: ${playbackSpeed}x", color = Color.White, fontSize = 11.sp)
                                        }
                                    }

                                    // Öneri 5: Uyku Zamanlayıcısı (Sleep Timer)
                                    val sleepInteractionSource = remember { MutableInteractionSource() }
                                    Button(
                                        onClick = { showSleepTimerDialog = true },
                                        interactionSource = sleepInteractionSource,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (sleepTimerMinutes > 0) NetflixRed else NetflixLightGrey
                                        ),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier
                                            .tvFocusBorder(isTvMode = isTvMode, interactionSource = sleepInteractionSource, shape = MaterialTheme.shapes.small)
                                            .testTag("player_sleep_timer_button")
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Timer, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            val label = if (sleepTimerMinutes > 0 && sleepTimerRemainingSec > 0) {
                                                val m = (sleepTimerRemainingSec / 60)
                                                val s = (sleepTimerRemainingSec % 60)
                                                "Uyku: ${m}:${s.toString().padStart(2, '0')}"
                                            } else {
                                                "Uyku Zamanlayıcı"
                                            }
                                            Text(label, color = Color.White, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // MOBİL MODU İÇİN OPTİMİZE EDİLMİŞ DOKUNMATİK PANEL (ELİT VE KULLANIŞLI TASARIM)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Horizontally Scrollable Action List for Mobile Options
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Hızlı Kanal Listesi (Sidebar) Butonu
                                Button(
                                    onClick = { showChannelsSidebar = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = NetflixLightGrey),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .height(38.dp)
                                        .testTag("player_sidebar_button")
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Menu, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Kanal Listesi", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Hızlı Geçiş (Quick Zap) Butonu
                                Button(
                                    onClick = { showQuickZapPanel = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = NetflixLightGrey),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .height(38.dp)
                                        .testTag("player_quick_zap_button")
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Bolt, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Hızlı Geçiş", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // 2. Ses ve Altyazı Seçim Butonu (Netflix tarzı)
                                Button(
                                    onClick = { showTrackSelection = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .height(38.dp)
                                        .testTag("player_tracks_button")
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Subtitles, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Ses & Altyazı", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // 3. En Boy Değiştirici
                                Button(
                                    onClick = { resizeMode = (resizeMode + 1) % 3 },
                                    colors = ButtonDefaults.buttonColors(containerColor = NetflixLightGrey),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .height(38.dp)
                                        .testTag("player_aspect_ratio_button")
                                ) {
                                    val resizeLabel = when (resizeMode) {
                                        0 -> "Orijinal Sığdır"
                                        1 -> "Ekrana Yay"
                                        2 -> "Yakınlaştır"
                                        else -> "Uyarla"
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AspectRatio, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(resizeLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // 4. Oynatma Hızı Değiştirici
                                Button(
                                    onClick = { showSpeedSelection = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = NetflixLightGrey),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .height(38.dp)
                                        .testTag("player_speed_button")
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Speed, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (playbackSpeed == 1.0f) "Oynatma Hızı" else "Hız: ${playbackSpeed}x", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // 5. Yayın Akışı (EPG)
                                Button(
                                    onClick = { showEpgPanel = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = NetflixLightGrey),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .height(38.dp)
                                        .testTag("player_epg_button")
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Schedule, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Rehber (EPG)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // 6. Uyku Zamanlayıcısı (Sleep Timer)
                                Button(
                                    onClick = { showSleepTimerDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (sleepTimerMinutes > 0) NetflixRed else NetflixLightGrey
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .height(38.dp)
                                        .testTag("player_sleep_timer_button")
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Timer, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        val label = if (sleepTimerMinutes > 0 && sleepTimerRemainingSec > 0) {
                                            val m = (sleepTimerRemainingSec / 60)
                                            val s = (sleepTimerRemainingSec % 60)
                                            "Zamanlayıcı: ${m}:${s.toString().padStart(2, '0')}"
                                        } else {
                                            "Uyku Zamanlayıcı"
                                        }
                                        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // 7. Grup Kilitli / Açık
                                Button(
                                    onClick = {
                                        zapLockCategory = !zapLockCategory
                                        sharedPrefs.edit().putBoolean("zap_lock_category", zapLockCategory).apply()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (zapLockCategory) NetflixRed else NetflixLightGrey
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .height(38.dp)
                                        .testTag("player_zap_lock_button")
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (zapLockCategory) Icons.Default.Lock else Icons.Default.LockOpen,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (zapLockCategory) "Grup Kilitli" else "Kategori Kilidi Açık",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // 8. Düşük Gecikme Modu
                                if (channel.type == "LIVE") {
                                    Button(
                                        onClick = {
                                            lowLatencyMode = !lowLatencyMode
                                            sharedPrefs.edit().putBoolean("low_latency_mode", lowLatencyMode).apply()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (lowLatencyMode) NetflixRed else NetflixLightGrey
                                        ),
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .height(38.dp)
                                            .testTag("player_low_latency_button")
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Bolt, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (lowLatencyMode) "Düşük Gecikme: Açık" else "Düşük Gecikme: Kapalı",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // Centered, aesthetic mobile hint label
                            Text(
                                text = "Mobil İpucu: Ekran dokununca kontroller açılır",
                                color = Color.LightGray.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Ses ve Altyazı Menüsü Diyaloğu
        TrackSelectionDialog(
            show = showTrackSelection,
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
            subtitlesDisabled = subtitlesDisabled,
            isTvMode = isTvMode,
            onAudioSelected = { track ->
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(TrackSelectionOverride(track.group, track.index))
                    .build()
                updateTracksState()
            },
            onSubtitleSelected = { track ->
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(track.group, track.index))
                    .build()
                updateTracksState()
            },
            onSubtitleDisabled = {
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                updateTracksState()
            },
            onDismiss = {
                showTrackSelection = false
            }
        )

        // Oynatma Hızı Ayar Menüsü Diyaloğu
        PlaybackSpeedDialog(
            show = showSpeedSelection,
            currentSpeed = playbackSpeed,
            isTvMode = isTvMode,
            onSpeedSelected = { speed ->
                playbackSpeed = speed
            },
            onDismiss = {
                showSpeedSelection = false
            }
        )

        // Uyku Zamanlayıcı Seçim Diyaloğu
        SleepTimerDialog(
            show = showSleepTimerDialog,
            currentMinutes = sleepTimerMinutes,
            isTvMode = isTvMode,
            onMinutesSelected = { mins ->
                sleepTimerMinutes = mins
            },
            onDismiss = {
                showSleepTimerDialog = false
            }
        )

        // Otomatik Sonraki Bölüm Geçiş Diyaloğu
        NextEpisodePromptDialog(
            show = showNextEpisodePrompt,
            countdown = nextEpisodeCountdown,
            isTvMode = isTvMode,
            onPlayNow = {
                showNextEpisodePrompt = false
                onNextEpisode?.invoke()
            },
            onCancel = {
                showNextEpisodePrompt = false
            }
        )

        // SOL KANAL LİSTESİ ÇEKMECESİ (Sidebar Drawer Overlay)
        androidx.compose.animation.AnimatedVisibility(
            visible = showChannelsSidebar,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier
                .fillMaxHeight()
                .width(if (isTvMode) 320.dp else 280.dp)
                .align(Alignment.CenterStart)
                .background(Color(0xFF0F0F0F).copy(alpha = 0.95f))
        ) {
            val sidebarCloseFocusRequester = remember { FocusRequester() }
            var sidebarSearchQuery by remember { mutableStateOf("") }
            var showVoiceSearchInSidebar by remember { mutableStateOf(false) }
            
            // Filter list based on search inside side menu
            val filteredChannels = remember(channels, sidebarSearchQuery) {
                if (sidebarSearchQuery.isEmpty()) {
                    channels
                } else {
                    val result = ArrayList<IptvChannel>()
                    val query = sidebarSearchQuery
                    for (chan in channels) {
                        if (chan.name.contains(query, ignoreCase = true)) {
                            result.add(chan)
                            if (result.size >= 100) break
                        }
                    }
                    result
                }
            }

            LaunchedEffect(showChannelsSidebar) {
                if (showChannelsSidebar && isTvMode) {
                    delay(300)
                    try {
                        sidebarCloseFocusRequester.requestFocus()
                    } catch (e: Exception) {}
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // Sidebar Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hızlı Kanal Listesi",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { showChannelsSidebar = false },
                        modifier = Modifier
                            .focusRequester(sidebarCloseFocusRequester)
                            .tvClickable(isTvMode = isTvMode) { showChannelsSidebar = false }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Sidebar Kapat", tint = Color.White)
                    }
                }

                // Search field inside sidebar
                OutlinedTextField(
                    value = sidebarSearchQuery,
                    onValueChange = { sidebarSearchQuery = it },
                    placeholder = { Text("Kanal ara...", color = Color.Gray, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF1F1F1F),
                        unfocusedContainerColor = Color(0xFF1A1A1A),
                        focusedBorderColor = NetflixRed,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (sidebarSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { sidebarSearchQuery = "" }) {
                                    Icon(Icons.Default.Clear, null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                                }
                            }
                            IconButton(onClick = { showVoiceSearchInSidebar = true }) {
                                Icon(Icons.Default.Mic, "Sesli Arama", tint = NetflixRed, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )

                if (showVoiceSearchInSidebar) {
                    VoiceSearchDialog(
                        isTvMode = isTvMode,
                        onDismissRequest = { showVoiceSearchInSidebar = false },
                        onResult = { sidebarSearchQuery = it }
                    )
                }

                // Channel lists matching currently selected categories
                if (filteredChannels.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Kanal bulunamadı", color = Color.Gray, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredChannels, key = { it.uniqueId }) { chan ->
                            val isCurrentPlaying = chan.uniqueId == channel.uniqueId
                            val selectChannelClick = remember(chan.uniqueId) {
                                {
                                    onChannelSelected(chan)
                                    showChannelsSidebar = false // Close after choosing
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCurrentPlaying) NetflixRed.copy(alpha = 0.9f) else Color(0xFF1E1E1E),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvClickable(isTvMode = isTvMode, onClick = selectChannelClick)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (chan.isFavorite) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Favori",
                                            tint = Color.Yellow,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    
                                    Text(
                                        text = chan.name,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    
                                    if (isCurrentPlaying) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "İzleniyor",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SAĞ YAYIN AKIŞI (EPG) VE BİLGİ SEÇENEKLERİ ÇEKMECESİ (Right Sidebar Drawer Overlay - Öneri 3)
        androidx.compose.animation.AnimatedVisibility(
            visible = showEpgPanel,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(), // Sağdan kayarak girer
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier
                .fillMaxHeight()
                .width(if (isTvMode) 360.dp else 290.dp)
                .align(Alignment.CenterEnd) // Sağ kenarda konumlanır
                .background(Color(0xFF0F0F0F).copy(alpha = 0.95f))
        ) {
            val epgCloseFocusRequester = remember { FocusRequester() }
            val simulatedEpg = remember(channel.name) { getSimulatedEpg(channel.name) }

            LaunchedEffect(showEpgPanel) {
                if (showEpgPanel && isTvMode) {
                    delay(300)
                    try {
                        epgCloseFocusRequester.requestFocus()
                    } catch (e: Exception) {}
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // Header / Başlık
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Yayın Akışı & Detaylar",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { showEpgPanel = false },
                        modifier = Modifier
                            .focusRequester(epgCloseFocusRequester)
                            .tvClickable(isTvMode = isTvMode) { showEpgPanel = false }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "EPG Kapat", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Aktif Yayın Detayları (Teknik Yayın Bilgileri paneli)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "TEKNİK YAYIN BİLGİSİ",
                            color = NetflixRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val videoWidth = exoPlayer.videoSize.width
                        val videoHeight = exoPlayer.videoSize.height
                        val videoResLabel = if (videoWidth > 0 && videoHeight > 0) {
                            "$videoWidth x $videoHeight"
                        } else {
                            "1920x1080 (HD 1080p)"
                        }

                        val soundTrackLabel = if (audioTracks.isNotEmpty()) {
                            audioTracks.firstOrNull { it.isSelected }?.name ?: "Stereo (AAC)"
                        } else {
                            "Türkçe (Stereo)"
                        }

                        Text("Çözünürlük: $videoResLabel", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Yenileme Hızı: 60 FPS", color = Color.LightGray, fontSize = 11.sp)
                        Text("Sıkıştırma: H.264 / AVC Codec", color = Color.LightGray, fontSize = 11.sp)
                        Text("Ses Türü: $soundTrackLabel", color = Color.LightGray, fontSize = 11.sp)
                        Text("Playlist Grubu: ${channel.groupTitle ?: "Genel"}", color = Color.LightGray, fontSize = 11.sp)
                    }
                }

                // Haftalık/Günlük Yayın Rehberi Başlığı
                Text(
                    text = "GÜNLÜK YAYIN AKIŞ REHBERİ",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(simulatedEpg) { prog ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (prog.isCurrent) NetflixRed.copy(alpha = 0.15f) else Color(0xFF161616),
                            border = if (prog.isCurrent) BorderStroke(1.dp, NetflixRed.copy(alpha = 0.5f)) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${prog.startTime} - ${prog.endTime}",
                                        color = if (prog.isCurrent) NetflixRed else Color.LightGray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (prog.isCurrent) {
                                        Surface(
                                            color = NetflixRed,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "CANLI",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = prog.title,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = prog.description,
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    maxLines = 2
                                )

                                if (prog.isCurrent) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = prog.progress,
                                        color = NetflixRed,
                                        trackColor = Color.White.copy(alpha = 0.2f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val percent = (prog.progress * 100).toInt()
                                    Text(
                                        text = "Yayın Tamamlanma Oranı: %$percent",
                                        color = Color.LightGray,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // HIZLI KANAL GEÇİŞ (QUICK ZAP) PANELİ (Bottom Carousel Overlay - Öneri 1)
        androidx.compose.animation.AnimatedVisibility(
            visible = showQuickZapPanel,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(), // Aşağıdan yukarı kayarak girer
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isTvMode) 230.dp else 190.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.82f),
                            Color.Black.copy(alpha = 0.98f)
                        )
                    )
                )
        ) {
            val quickZapCloseFocusRequester = remember { FocusRequester() }
            val currentGroup = channel.groupTitle
            // Sadece mevcut kategorideki kanalları listele (veya boşsa ilk 40'ı göster)
            val filteredGroup = remember(channels, currentGroup) {
                val groupList = if (currentGroup != null) channels.filter { it.groupTitle == currentGroup } else channels
                if (groupList.size > 50) groupList.take(50) else groupList
            }

            LaunchedEffect(showQuickZapPanel) {
                if (showQuickZapPanel && isTvMode) {
                    delay(300)
                    try {
                        quickZapCloseFocusRequester.requestFocus()
                    } catch (e: Exception) {}
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "HIZLI KANAL GEÇİŞİ (QUICK ZAP)",
                            color = NetflixRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${currentGroup ?: "Tüm Kanallar"} • ${filteredGroup.size} Yayın",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = { showQuickZapPanel = false },
                        modifier = Modifier
                            .size(36.dp)
                            .focusRequester(quickZapCloseFocusRequester)
                            .tvClickable(isTvMode = isTvMode) { showQuickZapPanel = false }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Zap Kapat", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                // Kanalların horizontal listesi
                LazyRow(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)
                ) {
                    items(filteredGroup, key = { "zap_" + it.uniqueId }) { chan ->
                        val isPlayingChan = chan.uniqueId == channel.uniqueId
                        val interactionSource = remember { MutableInteractionSource() }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isPlayingChan) NetflixRed else Color(0xFF16161C),
                            border = BorderStroke(
                                width = if (isPlayingChan) 2.dp else 1.dp,
                                color = if (isPlayingChan) NetflixRed else Color.White.copy(alpha = 0.08f)
                            ),
                            modifier = Modifier
                                .width(if (isTvMode) 150.dp else 120.dp)
                                .fillMaxHeight()
                                .tvFocusBorder(isTvMode = isTvMode, interactionSource = interactionSource, shape = RoundedCornerShape(10.dp))
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = androidx.compose.foundation.LocalIndication.current
                                ) {
                                    onChannelSelected(chan)
                                }
                        ) {
                            Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                if (!chan.logoUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = chan.logoUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .align(Alignment.TopEnd)
                                            .alpha(0.3f)
                                    )
                                }

                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(3.dp),
                                        color = if (isPlayingChan) Color.White.copy(alpha = 0.25f) else NetflixRed.copy(alpha = 0.15f),
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isPlayingChan) "Şu An Aktif" else "Kanal",
                                            color = if (isPlayingChan) Color.White else NetflixRed,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }

                                    Text(
                                        text = chan.name,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaybackSpeedDialog(
    show: Boolean,
    currentSpeed: Float,
    isTvMode: Boolean,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    val dialogFocusRequester = remember { FocusRequester() }

    LaunchedEffect(show) {
        if (show && isTvMode) {
            delay(300)
            try {
                dialogFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth(if (isTvMode) 0.55f else 0.85f)
                .fillMaxHeight(if (isTvMode) 0.72f else 0.8f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Oynatma Hızı Ayarı",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .focusRequester(dialogFocusRequester)
                            .tvClickable(isTvMode = isTvMode) { onDismiss() }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(speeds) { speed ->
                        val isSelected = speed == currentSpeed
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) NetflixRed else Color(0xFF222222),
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvClickable(isTvMode = isTvMode) {
                                    onSpeedSelected(speed)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (speed == 1.0f) "Normal (1.0x)" else "${speed}x",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val s = totalSeconds % 60
    val m = (totalSeconds / 60) % 60
    val h = totalSeconds / 3600
    return if (h > 0) {
        "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    } else {
        "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    }
}

@Composable
fun TrackSelectionDialog(
    show: Boolean,
    audioTracks: List<PlayerTrack>,
    subtitleTracks: List<PlayerTrack>,
    subtitlesDisabled: Boolean,
    isTvMode: Boolean,
    onAudioSelected: (PlayerTrack) -> Unit,
    onSubtitleSelected: (PlayerTrack) -> Unit,
    onSubtitleDisabled: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return

    val dialogCloseFocusRequester = remember { FocusRequester() }

    LaunchedEffect(show) {
        if (show && isTvMode) {
            delay(300)
            try {
                dialogCloseFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus fail
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header (Başlık)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ses ve Altyazı Ayarları",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .focusRequester(dialogCloseFocusRequester)
                            .tvClickable(isTvMode = isTvMode) { onDismiss() }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Sol Taraf: Ses Dil Seçenekleri (Audio Tracks)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = "Ses Dili",
                            color = NetflixRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        if (audioTracks.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Alternatif ses dili bulunamadı", color = Color.Gray, fontSize = 11.sp)
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(audioTracks) { track ->
                                    val isSelected = track.isSelected
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) NetflixRed else Color(0xFF222222),
                                        border = BorderStroke(1.dp, if (isSelected) NetflixRed else Color.Transparent),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .tvClickable(isTvMode = isTvMode) {
                                                onAudioSelected(track)
                                                onDismiss()
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Default.Check else Icons.Default.VolumeUp,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = track.name,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Ayraç Çizgisi (Divider)
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.1f))
                    )

                    // Sağ Taraf: Altyazı Seçenekleri (Subtitle Tracks)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = "Altyazı",
                            color = NetflixRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // "Altyazı Kapalı" seçeneği
                            item {
                                val isSelected = subtitlesDisabled || subtitleTracks.none { it.isSelected }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) NetflixRed else Color(0xFF222222),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .tvClickable(isTvMode = isTvMode) {
                                            onSubtitleDisabled()
                                            onDismiss()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Subtitles,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Altyazı Kapalı",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            items(subtitleTracks) { track ->
                                val isSelected = !subtitlesDisabled && track.isSelected
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) NetflixRed else Color(0xFF222222),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .tvClickable(isTvMode = isTvMode) {
                                            onSubtitleSelected(track)
                                            onDismiss()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Subtitles,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = track.name,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Öneri 3: EPG Ürün Modelleri ve EPG Veri Generatörü
data class EpgProgram(
    val title: String,
    val startTime: String,
    val endTime: String,
    val description: String,
    val progress: Float,
    val isCurrent: Boolean
)

fun getSimulatedEpg(channelName: String): List<EpgProgram> {
    val calendar = java.util.Calendar.getInstance()
    val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
    val currentMinute = calendar.get(java.util.Calendar.MINUTE)
    val totalMinutesNow = currentHour * 60 + currentMinute

    // Standart gün içi 9 ayrı yayın kuşağı
    val slots = listOf(
        Triple("00:00", "03:00", "Gece Sineması Kuşağı"),
        Triple("03:00", "07:00", "Tekrar Yayınlar & Pop Müzik"),
        Triple("07:00", "09:30", "Sabah Haberleri & Güne Başlarken"),
        Triple("09:30", "12:00", "Yerli Dizi / Sohbet Programı"),
        Triple("12:00", "13:30", "Öğle Bülteni ve Ekonomi Gündemi"),
        Triple("13:30", "16:00", "Yeşilçam Kuşağı / Nostaljik Sinema"),
        Triple("16:00", "19:00", "Sosyal Gündem & Eğlence Yarışması"),
        Triple("19:00", "21:00", "Ana Haber Bülteni (Canlı)"),
        Triple("21:00", "23:59", "Prime Time Canlı Yayın / Gece Kuşağı")
    )

    val nameLower = channelName.lowercase()
    val isSports = nameLower.contains("sport") || nameLower.contains("spor") || nameLower.contains("taraftar") || nameLower.contains("football") || nameLower.contains("mac") || nameLower.contains("beın")
    val isMovies = nameLower.contains("movie") || nameLower.contains("sinema") || nameLower.contains("film") || nameLower.contains("action") || nameLower.contains("dizi") || nameLower.contains("premium")
    val isNews = nameLower.contains("news") || nameLower.contains("haber") || nameLower.contains("trthaber") || nameLower.contains("a haber")

    return slots.map { (startStr, endStr, defaultTitle) ->
        val startParts = startStr.split(":")
        val endParts = endStr.split(":")
        val startMin = startParts[0].toInt() * 60 + startParts[1].toInt()
        val endMin = endParts[0].toInt() * 60 + endParts[1].toInt()

        val title = when {
            isSports -> {
                when (startStr) {
                    "00:00" -> "Unutulmaz Maç Özetleri ve Goller"
                    "03:00" -> "Spor Efsaneleri Belgeseli"
                    "07:00" -> "Sabah Sporu Haberleri (Canlı)"
                    "09:30" -> "Transfer Fısıltıları & Sıcak Gelişmeler"
                    "12:00" -> "Dünyadan Futbol Bülteni"
                    "13:30" -> "Derbi Özel Nostalji Arşivi"
                    "16:00" -> "Teknik Analiz / Taktik Tahtası"
                    "19:00" -> "Süper Lig Öncesi Son Gelişmeler"
                    "21:00" -> "Haftanın Maçı Canlı Analiz ve Yorumlar"
                    else -> defaultTitle
                }
            }
            isMovies -> {
                when (startStr) {
                    "00:00" -> "Korku, Gizem & Gerilim Sineması"
                    "03:00" -> "Yabancı Festival Filmleri Özel Seçkisi"
                    "07:00" -> "Haftalık Sinema Haberleri ve Vizyon"
                    "09:30" -> "Bilim Kurgu Klasikleri Kuşağı"
                    "12:00" -> "Aksiyon & Macera Sineması"
                    "13:30" -> "Tarihi Savaş & Destansı Yapımlar"
                    "16:00" -> "Polisiye / Dedektiflik Filmleri"
                    "19:00" -> "Kırmızı Halı & Oscar Adayı Yıldızlar"
                    "21:00" -> "Prime Time Gişe Rekortmeni Yabancı Film"
                    else -> defaultTitle
                }
            }
            isNews -> {
                when (startStr) {
                    "00:00" -> "Gece Raporu ve Küresel Manşetler"
                    "03:00" -> "Yabancı Basın Analizi & Tekrarlar"
                    "07:00" -> "Sabah Manşetleri & Gazete Oku"
                    "09:30" -> "Canlı Gündem & Sıcak Gelişmeler"
                    "12:00" -> "Gün Ortası Haber Kuşağı"
                    "13:30" -> "Ekonomi Dünyası / Finansal Analiz"
                    "16:00" -> "Ankara Kulisleri & Siyasi Nabız"
                    "19:00" -> "Ana Haber Bülteni (Canlı Yayın)"
                    "21:00" -> "Analitik Gündem Tartışma Programı"
                    else -> defaultTitle
                }
            }
            else -> defaultTitle
        }

        val desc = when {
            isSports -> "Özel stüdyo konukları, takımların en son performans verileri, teknik istatistikler ve çarpıcı maç yorumları bu programda."
            isMovies -> "Uluslararası ödüllere layık görülmüş, sinemaseverlerin kaçırmaması gereken başyapıt düzeyinde bir yapım."
            isNews -> "Yurttan ve dünyadan en sıcak son dakika gelişmeleri, muhabirlerimizin yerinden canlı bağlantıları ve uzman yorumları ile ekranda."
            else -> "Seçkin yapımlar, en güncel içerikler ve kaliteli zaman geçirmek isteyenler için kanalımızın en popüler kuşağı."
        }

        val isCurrent = if (startMin <= endMin) {
            totalMinutesNow in startMin until endMin
        } else {
            // Gece yarısı sınırı aşımı durumu
            totalMinutesNow >= startMin || totalMinutesNow < endMin
        }

        val progress = if (isCurrent) {
            val elapsed = if (startMin <= endMin) {
                totalMinutesNow - startMin
            } else {
                if (totalMinutesNow >= startMin) {
                    totalMinutesNow - startMin
                } else {
                    (24 * 60 - startMin) + totalMinutesNow
                }
            }
            val totalDur = if (startMin <= endMin) endMin - startMin else (24 * 60 - startMin) + endMin
            elapsed.toFloat() / totalDur.toFloat()
        } else {
            0f
        }

        EpgProgram(title, startStr, endStr, desc, progress, isCurrent)
    }
}

// Öneri 5: Otomatik Kapanma Zamanlayıcı Diyaloğu (Sleep Timer Control)
@Composable
fun SleepTimerDialog(
    show: Boolean,
    currentMinutes: Int,
    isTvMode: Boolean,
    onMinutesSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return
    val options = listOf(0, 15, 30, 45, 60, 90, 120)
    val dialogFocusRequester = remember { FocusRequester() }

    LaunchedEffect(show) {
        if (show && isTvMode) {
            delay(300)
            try {
                dialogFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth(if (isTvMode) 0.55f else 0.85f)
                .fillMaxHeight(if (isTvMode) 0.75f else 0.82f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Otomatik Uyku Zamanlayıcı",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .focusRequester(dialogFocusRequester)
                            .tvClickable(isTvMode = isTvMode) { onDismiss() }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Zamanlayıcı Kapat", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                
                Text(
                    text = "Seçilen süre sonlandığında, sistem yayını duraklatacak ve ana ekrana dönecektir.",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(options) { mins ->
                        val isSelected = mins == currentMinutes
                        val label = if (mins == 0) "Zamanlayıcıyı İptal Et (Kapat)" else "$mins Dakika Sonra Kapat"
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) NetflixRed else Color(0xFF222222),
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvClickable(isTvMode = isTvMode) {
                                    onMinutesSelected(mins)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NextEpisodePromptDialog(
    show: Boolean,
    countdown: Int,
    isTvMode: Boolean,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit
) {
    if (!show) return
    val playNowFocusRequester = remember { FocusRequester() }

    LaunchedEffect(show) {
        if (show) {
            delay(200)
            try {
                playNowFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            border = BorderStroke(2.dp, NetflixRed.copy(alpha = 0.8f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .width(if (isTvMode) 440.dp else 320.dp)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sıradaki Bölüm Başlıyor",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(80.dp)
                ) {
                    CircularProgressIndicator(
                        progress = countdown.toFloat() / 10f,
                        color = NetflixRed,
                        strokeWidth = 6.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                    Text(
                        text = "$countdown",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Dizinizin bir sonraki bölümü otomatik olarak başlatılacaktır.",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val playNowInteractionSource = remember { MutableInteractionSource() }
                    val cancelInteractionSource = remember { MutableInteractionSource() }

                    Button(
                        onClick = onPlayNow,
                        interactionSource = playNowInteractionSource,
                        colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(playNowFocusRequester)
                            .focusable(interactionSource = playNowInteractionSource)
                            .tvFocusBorder(isTvMode = isTvMode, interactionSource = playNowInteractionSource)
                    ) {
                        Text("Şimdi Oynat", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onCancel,
                        interactionSource = cancelInteractionSource,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .focusable(interactionSource = cancelInteractionSource)
                            .tvFocusBorder(isTvMode = isTvMode, interactionSource = cancelInteractionSource)
                    ) {
                        Text("İptal", color = Color.White)
                    }
                }
            }
        }
    }
}

