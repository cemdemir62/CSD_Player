package com.example.ui.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import com.example.data.model.IptvChannel
import com.example.data.model.XtreamEpisode
import androidx.compose.foundation.BorderStroke
import com.example.data.model.XtreamSeason
import com.example.ui.theme.NetflixDarkGrey
import com.example.ui.theme.NetflixLightGrey
import com.example.ui.theme.NetflixRed
import kotlinx.coroutines.delay

@Composable
fun SeriesDetailOverlay(
    series: IptvChannel,
    seasons: List<XtreamSeason>,
    episodesBySeason: Map<Int, List<XtreamEpisode>>,
    selectedSeasonNum: Int,
    isLoading: Boolean,
    error: String?,
    isTvMode: Boolean,
    lastWatchedEpisodeId: String?,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeSelected: (XtreamSeason, XtreamEpisode) -> Unit,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        val firstItemFocusRequester = remember { FocusRequester() }
        val errorCloseFocusRequester = remember { FocusRequester() }

        // Request focus on successful load or error
        LaunchedEffect(series, isLoading, seasons, error) {
            if (isTvMode) {
                delay(300) // Allow layout pass to finish on Android TV safely
                try {
                    if (!isLoading) {
                        if (error == null && seasons.isNotEmpty()) {
                            firstItemFocusRequester.requestFocus()
                        } else if (error != null) {
                            errorCloseFocusRequester.requestFocus()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SeriesDetail", "Focus request failed", e)
                }
            }
        }

        // Elegant full screen background overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.98f)),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator(color = NetflixRed)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Dizi bilgileri yükleniyor...",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (error != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text("Hata oluştu", color = NetflixRed, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error, color = Color.LightGray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                        modifier = Modifier
                            .focusRequester(errorCloseFocusRequester)
                            .tvClickable(isTvMode = isTvMode) { onClose() }
                    ) {
                        Text("Kapat", color = Color.White)
                    }
                }
            } else {
                // Main Success layout - beautifully Netflix-inspired!
                Column(modifier = Modifier.fillMaxSize()) {
                    // Cinematic widescreen Hero Banner Block at the very top
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isTvMode) 320.dp else 220.dp)
                            .background(Color.Black)
                    ) {
                        if (!series.logoUrl.isNullOrEmpty()) {
                            val context = LocalContext.current
                            val bgImageRequest = remember(series.logoUrl) {
                                ImageRequest.Builder(context)
                                    .data(series.logoUrl)
                                    .crossfade(true)
                                    .build()
                            }
                            AsyncImage(
                                model = bgImageRequest,
                                contentDescription = series.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Cinematic layered gradient overlays (depth & readability)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.6f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.2f),
                                            Color.Black
                                        )
                                    )
                                )
                        )

                        // Close button overlay top right (accessible and well spaced)
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.65f), androidx.compose.foundation.shape.CircleShape)
                                .tvClickable(isTvMode = isTvMode) { onClose() }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                        }

                        // Bottom meta overlay container
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 24.dp, vertical = 20.dp)
                        ) {
                            // Category Badge
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = NetflixRed,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Text(
                                    text = (series.groupTitle ?: "Diziler").uppercase(),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            // Bold Display Title
                            Text(
                                text = series.name,
                                color = Color.White,
                                fontSize = if (isTvMode) 30.sp else 22.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            // Seasons / EP Count
                            Text(
                                text = "${seasons.size} Sezon • Google AI Studio IPTV",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Lower Content Area (Seasons & Episodes Row/List with clean padding)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "SEZONLAR",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        
                        // Horizontal scrollable Season Tabs
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            itemsIndexed(seasons, key = { _, s -> s.seasonNumber }) { idx, season ->
                                val isCurrent = season.seasonNumber == selectedSeasonNum
                                val selectSeasonClick = remember(season.seasonNumber) { { onSeasonSelected(season.seasonNumber) } }
                                val itemModifier = Modifier
                                    .tvClickable(isTvMode = isTvMode, onClick = selectSeasonClick)
                                
                                val finalModifier = if (idx == 0) {
                                    itemModifier.focusRequester(firstItemFocusRequester)
                                } else {
                                    itemModifier
                                }

                                Surface(
                                    shape = RoundedCornerShape(20.dp), // modern pill
                                    color = if (isCurrent) NetflixRed else Color(0xFF1E1E22),
                                    border = BorderStroke(
                                        width = 1.5.dp,
                                        color = if (isCurrent) NetflixRed else Color.White.copy(alpha = 0.08f)
                                    ),
                                    modifier = finalModifier
                                ) {
                                    Text(
                                        text = season.name,
                                        color = if (isCurrent) Color.White else Color.LightGray,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }

                        // Divider line separating the interactive controls from list
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.08f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Episodes Header
                        val activeSeasonObj = seasons.find { it.seasonNumber == selectedSeasonNum }
                        Text(
                            text = "${activeSeasonObj?.name ?: "Bölümler"} (Yürütmek İçin Seçin)",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Scrollable List of Episodes below the selector
                        val activeEpisodes = episodesBySeason[selectedSeasonNum] ?: emptyList()
                        if (activeEpisodes.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Bu sezona ait bölüm bulunamadı.",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(activeEpisodes, key = { it.id }) { episode ->
                                    val isLastWatched = episode.id == lastWatchedEpisodeId
                                    val selectEpisodeClick = remember(episode.id, activeSeasonObj?.seasonNumber) {
                                        {
                                            val seasonObj = activeSeasonObj ?: XtreamSeason(
                                                "Sezon ${selectedSeasonNum}",
                                                selectedSeasonNum
                                            )
                                            onEpisodeSelected(seasonObj, episode)
                                        }
                                    }
                                    EpisodeRowItem(
                                        episode = episode,
                                        seriesLogoUrl = series.logoUrl,
                                        isTvMode = isTvMode,
                                        isLastWatched = isLastWatched,
                                        onClick = selectEpisodeClick
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
fun EpisodeRowItem(
    episode: XtreamEpisode,
    seriesLogoUrl: String?,
    isTvMode: Boolean,
    isLastWatched: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isLastWatched) Color(0xFF2C1E1E) else Color(0xFF1E1E1E),
        border = if (isLastWatched) BorderStroke(1.dp, NetflixRed) else null,
        modifier = Modifier
            .fillMaxWidth()
            .tvClickable(isTvMode = isTvMode, shape = RoundedCornerShape(8.dp)) { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Visual Episode Thumbnail on the LEFT
            val displayLogo = if (!episode.logoUrl.isNullOrEmpty()) episode.logoUrl else seriesLogoUrl
            Box(
                modifier = Modifier
                    .size(110.dp, 68.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (!displayLogo.isNullOrEmpty()) {
                    val context = LocalContext.current
                    val imageRequest = remember(displayLogo) {
                        ImageRequest.Builder(context)
                            .data(displayLogo)
                            .crossfade(true)
                            .size(110, 68)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Play Icon Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text Details on the RIGHT
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${episode.episodeNum}. Bölüm: ${episode.name}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isLastWatched) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = NetflixRed,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = "KALDIĞIN YER",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isLastWatched) "Son izlenen bölüm - Kaldığın yerden izle" else "Bölümü Oynat",
                    color = if (isLastWatched) NetflixRed else Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = if (isLastWatched) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
