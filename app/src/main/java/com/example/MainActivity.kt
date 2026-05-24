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
import com.example.ui.screens.WelcomeScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.SeriesDetailOverlay
import com.example.data.model.IptvChannel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.IptvViewModel

import android.content.pm.ActivityInfo

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: IptvViewModel = viewModel()
                
                // Gözlemlenen reactive durumlar (flows)
                val appMode by viewModel.appMode.collectAsState()
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

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Android sistem çubuğu (Edge-to-Edge) insets padding değerlerini alarak,
                    // video oynatmaya özel tam ekran (bleed-out) modunu koruyoruz.
                    val isTv = appMode == "TV"

                    when {
                        isSplashActive -> {
                            SplashScreen(
                                onSplashFinished = { isSplashActive = false }
                            )
                        }

                        // 1. Durum: Cihaz Modu Seçim Ekranı (İlk Açılış)
                        appMode == null -> {
                            WelcomeScreen(
                                onModeSelected = { mode ->
                                    viewModel.setAppMode(mode)
                                }
                            )
                        }

                        // 2. Durum: Özel Medya Oynatıcı Ekranı (ExoPlayer Stream HUD)
                        activeChannel != null -> {
                            val channel = activeChannel!!
                            BackHandler {
                                viewModel.selectChannel(null)
                            }
                            PlayerScreen(
                                channel = channel,
                                isTvMode = isTv,
                                channels = channels,
                                onChannelSelected = { chan -> viewModel.selectChannel(chan) },
                                onToggleFavorite = { chan -> viewModel.toggleFavorite(chan) },
                                onBack = { viewModel.selectChannel(null) },
                                onNextChannel = { viewModel.zapNext() },
                                onPrevChannel = { viewModel.zapPrev() }
                            )
                        }

                        // 3. Durum: Playlist Seçim ve Ekleme Ekranı (Login)
                        selectedPlaylist == null -> {
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
                        else -> {
                            val activeSeriesChannel by viewModel.activeSeriesChannel.collectAsState()

                            BackHandler {
                                if (activeSeriesChannel != null) {
                                    viewModel.selectSeries(null)
                                } else {
                                    viewModel.selectPlaylist(null)
                                }
                            }

                            Box(modifier = Modifier.fillMaxSize()) {
                                DashboardScreen(
                                    playlist = selectedPlaylist!!,
                                    channels = channels,
                                    groups = groups,
                                    selectedType = selectedType,
                                    selectedGroup = selectedGroup,
                                    searchQuery = searchQuery,
                                    isTvMode = isTv,
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
                                        if (channel.type == "SERIES" && selectedPlaylist!!.type == "XTREAM") {
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

                                    val seasons by viewModel.seriesSeasons.collectAsState()
                                    val episodes by viewModel.seriesEpisodes.collectAsState()
                                    val selectedSeasonNum by viewModel.selectedSeasonNum.collectAsState()
                                    val isFetchingSeries by viewModel.isFetchingSeriesInfo.collectAsState()
                                    val seriesFetchError by viewModel.seriesFetchError.collectAsState()

                                    SeriesDetailOverlay(
                                        series = series,
                                        seasons = seasons,
                                        episodesBySeason = episodes,
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
                                                uniqueId = "${selectedPlaylist!!.id}_episode_${episode.id}",
                                                playlistId = selectedPlaylist!!.id,
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
