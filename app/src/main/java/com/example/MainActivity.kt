package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.PlayerScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import com.example.ui.screens.WelcomeScreen
import com.example.ui.screens.ProfileSelectionScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.SeriesDetailOverlay
import com.example.data.model.IptvChannel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.IptvViewModel
import android.content.pm.ActivityInfo

enum class MainScreenState {
    SPLASH, WELCOME, PROFILE, PLAYER, LOGIN, DASHBOARD
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: IptvViewModel = viewModel()
                
                // Gözlemlenen reactive durumlar (flows)
                val appMode by viewModel.appMode.collectAsState()
                val selectedProfile by viewModel.selectedProfile.collectAsState()
                var isSplashActive by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }

                // Dynamic Orientation Management:
                // "TV" forces landscape orientation, whereas "MOBILE" (or unselected null) allows both portrait and landscape orientation freely.
                androidx.compose.runtime.LaunchedEffect(appMode) {
                    this@MainActivity.requestedOrientation = if (appMode == "TV") {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
                val playlists by viewModel.playlists.collectAsState()
                val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
                val selectedType by viewModel.selectedType.collectAsState()
                val selectedGroup by viewModel.selectedGroup.collectAsState()
                val searchQuery by viewModel.searchQuery.collectAsState()
                val refreshState by viewModel.refreshState.collectAsState()
                val activeChannel by viewModel.activeChannel.collectAsState()
                val groups by viewModel.groupsByType.collectAsState()
                val channels by viewModel.channels.collectAsState()
                val activeSeriesChannel by viewModel.activeSeriesChannel.collectAsState()
                val seriesSeasons by viewModel.seriesSeasons.collectAsState()
                val seriesEpisodes by viewModel.seriesEpisodes.collectAsState()
                val continueWatchingList by viewModel.continueWatchingChannels.collectAsState()
                val continueWatchingData by viewModel.continueWatchingData.collectAsState()
                val syncState by viewModel.syncState.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Android sistem çubuğu (Edge-to-Edge) insets padding değerlerini alarak,
                    // video oynatmaya özel tam ekran (bleed-out) modunu koruyoruz.
                    val isTv = appMode == "TV"

                    val screenState = when {
                        isSplashActive -> MainScreenState.SPLASH
                        appMode == null -> MainScreenState.WELCOME
                        selectedProfile == null -> MainScreenState.PROFILE
                        activeChannel != null -> MainScreenState.PLAYER
                        selectedPlaylist == null -> MainScreenState.LOGIN
                        else -> MainScreenState.DASHBOARD
                    }

                    Crossfade(
                        targetState = screenState,
                        animationSpec = tween(durationMillis = 500)
                    ) { state ->
                        when (state) {
                            MainScreenState.SPLASH -> {
                                SplashScreen(
                                    onSplashFinished = { isSplashActive = false }
                                )
                            }

                            // 1. Durum: Cihaz Modu Seçim Ekranı (İlk Açılış)
                            MainScreenState.WELCOME -> {
                                WelcomeScreen(
                                    onModeSelected = { mode ->
                                        viewModel.setAppMode(mode)
                                    }
                                )
                            }

                            // 1.1 Durum: Netflix Tarzı Çoklu Profil Seçim Ekranı
                            MainScreenState.PROFILE -> {
                                val profiles by viewModel.profiles.collectAsState()
                                ProfileSelectionScreen(
                                    profiles = profiles,
                                    onProfileSelected = { p -> viewModel.selectProfile(p) },
                                    onAddProfile = { name, color, emoji, isKids ->
                                        viewModel.addProfile(name, color, emoji, isKids)
                                    },
                                    onDeleteProfile = { p -> viewModel.deleteProfile(p) },
                                    isTvMode = isTv
                                )
                            }

                            // 2. Durum: Özel Medya Oynatıcı Ekranı (ExoPlayer Stream HUD)
                            MainScreenState.PLAYER -> {
                                val channel = activeChannel!!
                                BackHandler {
                                    viewModel.selectChannel(null)
                                }
                                val context = LocalContext.current
                                val sharedPrefs = remember(context) { context.getSharedPreferences("zula_iptv_prefs", android.content.Context.MODE_PRIVATE) }
                                
                                var onNextEp: (() -> Unit)? = null
                                val currentPlaylistLocal = selectedPlaylist
                                if (channel.type == "SERIES" && activeSeriesChannel != null && currentPlaylistLocal != null) {
                                    var currentSeasonNum: Int? = null
                                    var currentEpIndex: Int? = null
                                    
                                    for ((sNum, epList) in seriesEpisodes) {
                                        val index = epList.indexOfFirst { it.id == channel.channelId }
                                        if (index != -1) {
                                            currentSeasonNum = sNum
                                            currentEpIndex = index
                                            break
                                        }
                                    }
                                    
                                    if (currentSeasonNum != null && currentEpIndex != null) {
                                        val currentSeasonEpisodes = seriesEpisodes[currentSeasonNum] ?: emptyList()
                                        var nextEpisode: Pair<com.example.data.model.XtreamSeason, com.example.data.model.XtreamEpisode>? = null
                                        
                                        if (currentEpIndex + 1 < currentSeasonEpisodes.size) {
                                            val nextEp = currentSeasonEpisodes[currentEpIndex + 1]
                                            val seasonObj = seriesSeasons.find { it.seasonNumber == currentSeasonNum }
                                            if (seasonObj != null) {
                                                nextEpisode = seasonObj to nextEp
                                            }
                                        } else {
                                            val sortedSeasons = seriesSeasons.sortedBy { it.seasonNumber }
                                            val currentSeasonIdx = sortedSeasons.indexOfFirst { it.seasonNumber == currentSeasonNum }
                                            if (currentSeasonIdx != -1 && currentSeasonIdx + 1 < sortedSeasons.size) {
                                                val nextSeason = sortedSeasons[currentSeasonIdx + 1]
                                                val nextSeasonEpisodes = seriesEpisodes[nextSeason.seasonNumber] ?: emptyList()
                                                if (nextSeasonEpisodes.isNotEmpty()) {
                                                    nextEpisode = nextSeason to nextSeasonEpisodes.first()
                                                }
                                            }
                                        }
                                        
                                        if (nextEpisode != null) {
                                            onNextEp = {
                                                val (nextSeasonObj, nextEp) = nextEpisode!!
                                                sharedPrefs.edit()
                                                    .putInt("series_${activeSeriesChannel!!.uniqueId}_last_season", nextSeasonObj.seasonNumber)
                                                    .putString("series_${activeSeriesChannel!!.uniqueId}_last_episode_id", nextEp.id)
                                                    .apply()
                                                
                                                val nextTempChannel = IptvChannel(
                                                    uniqueId = "${currentPlaylistLocal.id}_episode_${nextEp.id}",
                                                    playlistId = currentPlaylistLocal.id,
                                                    channelId = nextEp.id,
                                                    name = "${activeSeriesChannel!!.name} - ${nextSeasonObj.name} Bölüm ${nextEp.episodeNum} : ${nextEp.name}",
                                                    streamUrl = nextEp.streamUrl,
                                                    logoUrl = activeSeriesChannel!!.logoUrl ?: nextEp.logoUrl,
                                                    groupTitle = activeSeriesChannel!!.groupTitle,
                                                    type = "SERIES"
                                                )
                                                viewModel.selectChannel(nextTempChannel)
                                            }
                                        }
                                    }
                                }

                                PlayerScreen(
                                    channel = channel,
                                    isTvMode = isTv,
                                    channels = channels,
                                    onChannelSelected = { chan -> viewModel.selectChannel(chan) },
                                    onToggleFavorite = { chan -> viewModel.toggleFavorite(chan) },
                                    onBack = { viewModel.selectChannel(null) },
                                    onNextChannel = { viewModel.zapNext() },
                                    onPrevChannel = { viewModel.zapPrev() },
                                    onNextEpisode = onNextEp
                                )
                            }

                            // 3. Durum: Playlist Seçim ve Ekleme Ekranı (Login)
                            MainScreenState.LOGIN -> {
                                LoginScreen(
                                    playlists = playlists,
                                    refreshState = refreshState,
                                    isTvMode = isTv,
                                    onPlaylistSelected = { pList ->
                                        viewModel.selectPlaylist(pList)
                                    },
                                    onAddPlaylist = { name, type, url, user, pass ->
                                        viewModel.addPlaylist(name, type, url, user, pass)
                                    },
                                    onDeletePlaylist = { pList ->
                                        viewModel.deletePlaylist(pList)
                                    },
                                    onClearState = {
                                        viewModel.clearRefreshState()
                                    }
                                )
                            }

                            // 4. Durum: Netflix Tarzı Kanallar & Kategoriler Paneli (Dashboard)
                            MainScreenState.DASHBOARD -> {
                                val activeSeriesChannel by viewModel.activeSeriesChannel.collectAsState()

                                BackHandler {
                                    if (activeSeriesChannel != null) {
                                        viewModel.selectSeries(null)
                                    } else {
                                        viewModel.selectPlaylist(null)
                                    }
                                }

                                val currentPlaylistLocal = selectedPlaylist
                                val currentProfileLocal = selectedProfile

                                if (currentPlaylistLocal != null && currentProfileLocal != null) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        DashboardScreen(
                                            playlist = currentPlaylistLocal,
                                            syncState = syncState,
                                            channels = channels,
                                            groups = groups,
                                            selectedType = selectedType,
                                            selectedGroup = selectedGroup,
                                            searchQuery = searchQuery,
                                            isTvMode = isTv,
                                            selectedProfile = currentProfileLocal,
                                            onSwitchProfile = { viewModel.selectProfile(null) },
                                            continueWatchingList = continueWatchingList,
                                            continueWatchingProgress = continueWatchingData,
                                            onRemoveContinueWatching = { channel ->
                                                viewModel.removeFromContinueWatching(channel.uniqueId)
                                            },
                                            onTypeSelected = { type ->
                                                viewModel.selectType(type)
                                            },
                                            onGroupSelected = { group ->
                                                viewModel.selectGroup(group)
                                            },
                                            onSearchChanged = { query ->
                                                viewModel.setSearchQuery(query)
                                            },
                                            onChannelSelected = { channel ->
                                                if (channel.type == "SERIES" && currentPlaylistLocal.type == "XTREAM") {
                                                    viewModel.selectSeries(channel)
                                                } else {
                                                    viewModel.selectChannel(channel)
                                                }
                                            },
                                            onToggleFavorite = { channel ->
                                                viewModel.toggleFavorite(channel)
                                            },
                                            onDisconnect = {
                                                viewModel.selectPlaylist(null)
                                            },
                                            onResetMode = {
                                                viewModel.resetAppMode()
                                            },
                                            onRefreshPlaylist = {
                                                viewModel.refreshActivePlaylist()
                                            }
                                        )

                                        // Dizi Detay & Bölüm Seçim Ekranı Overlay'i
                                        activeSeriesChannel?.let { series ->
                                            val context = LocalContext.current
                                            val sharedPrefs = remember(context) { context.getSharedPreferences("zula_iptv_prefs", android.content.Context.MODE_PRIVATE) }
                                            var lastWatchedEpisodeId by remember(series.uniqueId) {
                                                mutableStateOf(sharedPrefs.getString("series_${series.uniqueId}_last_episode_id", null))
                                            }

                                            val selectedSeasonNum by viewModel.selectedSeasonNum.collectAsState()
                                            val isFetchingSeries by viewModel.isFetchingSeriesInfo.collectAsState()
                                            val seriesFetchError by viewModel.seriesFetchError.collectAsState()

                                            SeriesDetailOverlay(
                                                series = series,
                                                seasons = seriesSeasons,
                                                episodesBySeason = seriesEpisodes,
                                                selectedSeasonNum = selectedSeasonNum,
                                                isLoading = isFetchingSeries,
                                                error = seriesFetchError,
                                                isTvMode = isTv,
                                                lastWatchedEpisodeId = lastWatchedEpisodeId,
                                                onSeasonSelected = { seasonNum ->
                                                    viewModel.selectSeasonNum(seasonNum)
                                                },
                                                onEpisodeSelected = { seasonObj, episode ->
                                                    // Kaldığı bölümü ve sezonu kaydet (Resume info)
                                                    sharedPrefs.edit()
                                                        .putInt("series_${series.uniqueId}_last_season", seasonObj.seasonNumber)
                                                        .putString("series_${series.uniqueId}_last_episode_id", episode.id)
                                                        .apply()

                                                    lastWatchedEpisodeId = episode.id

                                                    // Bölüm geçici bir IptvChannel olarak oynatılır
                                                    val tempEpisodeChannel = IptvChannel(
                                                        uniqueId = "${currentPlaylistLocal.id}_episode_${episode.id}",
                                                        playlistId = currentPlaylistLocal.id,
                                                        channelId = episode.id,
                                                        name = "${series.name} - ${seasonObj.name} Bölüm ${episode.episodeNum} : ${episode.name}",
                                                        streamUrl = episode.streamUrl,
                                                        logoUrl = series.logoUrl ?: episode.logoUrl,
                                                        groupTitle = series.groupTitle,
                                                        type = "SERIES"
                                                    )
                                                    viewModel.selectChannel(tempEpisodeChannel)
                                                },
                                                onClose = {
                                                    viewModel.selectSeries(null)
                                                }
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
}
