package com.example.ui.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
                // Beautifully Adaptive Layout
                if (isTvMode) {
                    // ----------------------------------------------------
                    // PREMIUM TV / LANDSCAPE SPLIT SCREEN LAYOUT
                    // ----------------------------------------------------
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        // Left Pane: Info, poster preview, & season controls
                        Column(
                            modifier = Modifier
                                .weight(0.4f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Video preview / Series Image
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF141416))
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
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Black.copy(alpha = 0.2f),
                                                    Color.Black.copy(alpha = 0.85f)
                                                )
                                            )
                                        )
                                )
                            }

                            // Meta details
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = NetflixRed,
                                modifier = Modifier.wrapContentSize()
                            ) {
                                Text(
                                    text = (series.groupTitle ?: "Diziler").uppercase(),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            Text(
                                text = series.name,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = "${seasons.size} Sezon • Google AI Studio IPTV",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.08f),
                                thickness = 1.dp
                            )

                            Text(
                                text = "SEZONLAR",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Horizontally Scrolling Season Selector
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(seasons) { idx, season ->
                                    val isCurrent = season.seasonNumber == selectedSeasonNum
                                    val selectSeasonClick = remember(season.seasonNumber) { { onSeasonSelected(season.seasonNumber) } }
                                    val itemModifier = Modifier
                                        .tvClickable(isTvMode = true, onClick = selectSeasonClick)

                                    val finalModifier = if (idx == 0) {
                                        itemModifier.focusRequester(firstItemFocusRequester)
                                    } else {
                                        itemModifier
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
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
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Action Button: Close Overlay
                            Button(
                                onClick = onClose,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E22)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .tvClickable(isTvMode = true) { onClose() }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Bilgi Sayfasını Kapat", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Right Pane: Full Screen Height Episode Rows
                        Column(
                            modifier = Modifier
                                .weight(0.6f)
                                .fillMaxHeight()
                        ) {
                            val activeSeasonObj = seasons.find { it.seasonNumber == selectedSeasonNum }
                            Text(
                                text = "${activeSeasonObj?.name ?: "Bölümler"} (Yürütmek İçin Seçin)",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

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
                                    itemsIndexed(activeEpisodes) { idx, episode ->
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
                                            isTvMode = true,
                                            isLastWatched = isLastWatched,
                                            onClick = selectEpisodeClick
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // ----------------------------------------------------
                    // PORTRAIT PORTABLE VERTICAL SENSITIVE LAYOUT
                    // ----------------------------------------------------
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Beautiful widescreen Hero Banner Block at the top
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
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

                            // Layered gradient overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.5f),
                                                Color.Transparent,
                                                Color.Black
                                            )
                                        )
                                    )
                            )

                            // Close button overlay
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), androidx.compose.foundation.shape.CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                            }

                            // Title & info overlay
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = NetflixRed,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                ) {
                                    Text(
                                        text = (series.groupTitle ?: "Diziler").uppercase(),
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Text(
                                    text = series.name,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = "${seasons.size} Sezon • Google AI Studio IPTV",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Lower content: selection & episodes list
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "SEZONLAR",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // Horizontal scroll Season tabs
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                itemsIndexed(seasons) { idx, season ->
                                    val isCurrent = season.seasonNumber == selectedSeasonNum
                                    val selectSeasonClick = remember(season.seasonNumber) { { onSeasonSelected(season.seasonNumber) } }

                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = if (isCurrent) NetflixRed else Color(0xFF1E1E22),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isCurrent) NetflixRed else Color.White.copy(alpha = 0.08f)
                                        ),
                                        modifier = Modifier.clickable(onClick = selectSeasonClick)
                                    ) {
                                        Text(
                                            text = season.name,
                                            color = if (isCurrent) Color.White else Color.LightGray,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.08f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            val activeSeasonObj = seasons.find { it.seasonNumber == selectedSeasonNum }
                            Text(
                                text = "${activeSeasonObj?.name ?: "Bölümler"} (Yürütmek İçin Seçin)",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )

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
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(activeEpisodes) { idx, episode ->
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
                                            isTvMode = false,
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
