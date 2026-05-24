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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.IptvChannel
import com.example.data.model.XtreamEpisode
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
                .background(Color.Black.copy(alpha = 0.96f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
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
                // Main Success layout - beautifully adaptive!
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header: Series Title and Close button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = series.name,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = series.groupTitle ?: "Diziler",
                                color = NetflixRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.tvClickable(isTvMode = isTvMode) { onClose() }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                        }
                    }

                    // Seasons and Episodes main section
                    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        val useLandscape = maxWidth > 600.dp

                        if (useLandscape) {
                            // Landscape/Wide View: Side-by-side layout
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                // Left Column: Poster / Seasons lists
                                Column(
                                    modifier = Modifier
                                        .weight(0.35f)
                                        .fillMaxHeight()
                                ) {
                                    if (!series.logoUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = series.logoUrl,
                                            contentDescription = series.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(200.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }

                                    Text(
                                        text = "Sezonlar",
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )

                                    LazyColumn(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        itemsIndexed(seasons) { idx, season ->
                                            val isCurrent = season.seasonNumber == selectedSeasonNum
                                            val itemModifier = Modifier
                                                .fillMaxWidth()
                                                .tvClickable(isTvMode = isTvMode) {
                                                    onSeasonSelected(season.seasonNumber)
                                                }
                                            val finalModifier = if (idx == 0) {
                                                itemModifier.focusRequester(firstItemFocusRequester)
                                            } else {
                                                itemModifier
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isCurrent) NetflixRed else NetflixDarkGrey,
                                                modifier = finalModifier
                                            ) {
                                                Text(
                                                    text = season.name,
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Right Column: Scrollable List of Episodes
                                Column(
                                    modifier = Modifier
                                        .weight(0.65f)
                                        .fillMaxHeight()
                                        .background(NetflixDarkGrey, RoundedCornerShape(8.dp))
                                        .padding(16.dp)
                                ) {
                                    val activeSeason = seasons.find { it.seasonNumber == selectedSeasonNum }
                                    Text(
                                        text = "${activeSeason?.name ?: "Bölümler"}",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    val activeEpisodes = episodesBySeason[selectedSeasonNum] ?: emptyList()
                                    if (activeEpisodes.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
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
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(activeEpisodes) { episode ->
                                                EpisodeRowItem(
                                                    episode = episode,
                                                    seriesLogoUrl = series.logoUrl,
                                                    isTvMode = isTvMode,
                                                    onClick = {
                                                        val seasonObj = activeSeason ?: XtreamSeason(
                                                            "Sezon ${selectedSeasonNum}",
                                                            selectedSeasonNum
                                                        )
                                                        onEpisodeSelected(seasonObj, episode)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Portrait/Compact View: Top-to-bottom layout
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Top Row: Seasons horizontal bar
                                Text(
                                    text = "Sezonlar",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(seasons) { idx, season ->
                                        val isCurrent = season.seasonNumber == selectedSeasonNum
                                        val itemModifier = Modifier
                                            .tvClickable(isTvMode = isTvMode) {
                                                onSeasonSelected(season.seasonNumber)
                                            }
                                        val finalModifier = if (idx == 0) {
                                            itemModifier.focusRequester(firstItemFocusRequester)
                                        } else {
                                            itemModifier
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (isCurrent) NetflixRed else NetflixDarkGrey,
                                            modifier = finalModifier
                                        ) {
                                            Text(
                                                text = season.name,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }

                                // Bottom List: Episodes list taking up the remaining space
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .background(NetflixDarkGrey, RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                ) {
                                    val activeSeason = seasons.find { it.seasonNumber == selectedSeasonNum }
                                    val activeEpisodes = episodesBySeason[selectedSeasonNum] ?: emptyList()
                                    if (activeEpisodes.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
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
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(activeEpisodes) { episode ->
                                                EpisodeRowItem(
                                                    episode = episode,
                                                    seriesLogoUrl = series.logoUrl,
                                                    isTvMode = isTvMode,
                                                    onClick = {
                                                        val seasonObj = activeSeason ?: XtreamSeason(
                                                            "Sezon ${selectedSeasonNum}",
                                                            selectedSeasonNum
                                                        )
                                                        onEpisodeSelected(seasonObj, episode)
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
}

@Composable
fun EpisodeRowItem(
    episode: XtreamEpisode,
    seriesLogoUrl: String?,
    isTvMode: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1E1E1E),
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
                    AsyncImage(
                        model = displayLogo,
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
                Text(
                    text = "${episode.episodeNum}. Bölüm: ${episode.name}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Bölümü Oynat",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
        }
    }
}
