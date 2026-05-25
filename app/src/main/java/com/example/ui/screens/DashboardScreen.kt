package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import com.example.data.model.IptvChannel
import com.example.data.model.Playlist
import com.example.ui.theme.NetflixDarkGrey
import com.example.ui.theme.NetflixLightGrey
import com.example.ui.theme.NetflixRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    playlist: Playlist,
    channels: List<IptvChannel>,
    groups: List<String>,
    selectedType: String,
    selectedGroup: String?,
    searchQuery: String,
    isTvMode: Boolean,
    onTypeSelected: (String) -> Unit,
    onGroupSelected: (String?) -> Unit,
    onSearchChanged: (String) -> Unit,
    onChannelSelected: (IptvChannel) -> Unit,
    onToggleFavorite: (IptvChannel) -> Unit,
    onDisconnect: () -> Unit,
    onResetMode: () -> Unit,
    onRefreshPlaylist: () -> Unit
) {
    if (isTvMode) {
        TvDashboard(
            playlist = playlist,
            channels = channels,
            groups = groups,
            selectedType = selectedType,
            selectedGroup = selectedGroup,
            searchQuery = searchQuery,
            onTypeSelected = onTypeSelected,
            onGroupSelected = onGroupSelected,
            onSearchChanged = onSearchChanged,
            onChannelSelected = onChannelSelected,
            onToggleFavorite = onToggleFavorite,
            onDisconnect = onDisconnect,
            onResetMode = onResetMode,
            onRefreshPlaylist = onRefreshPlaylist
        )
    } else {
        MobileDashboard(
            playlist = playlist,
            channels = channels,
            groups = groups,
            selectedType = selectedType,
            selectedGroup = selectedGroup,
            searchQuery = searchQuery,
            onTypeSelected = onTypeSelected,
            onGroupSelected = onGroupSelected,
            onSearchChanged = onSearchChanged,
            onChannelSelected = onChannelSelected,
            onToggleFavorite = onToggleFavorite,
            onDisconnect = onDisconnect,
            onResetMode = onResetMode,
            onRefreshPlaylist = onRefreshPlaylist
        )
    }
}

// ==========================================
// MOBİL NETFLIX ARAYÜZÜ (Landscape Touch)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileDashboard(
    playlist: Playlist,
    channels: List<IptvChannel>,
    groups: List<String>,
    selectedType: String,
    selectedGroup: String?,
    searchQuery: String,
    onTypeSelected: (String) -> Unit,
    onGroupSelected: (String?) -> Unit,
    onSearchChanged: (String) -> Unit,
    onChannelSelected: (IptvChannel) -> Unit,
    onToggleFavorite: (IptvChannel) -> Unit,
    onDisconnect: () -> Unit,
    onResetMode: () -> Unit,
    onRefreshPlaylist: () -> Unit
) {
    val visibleLimit = remember(selectedType, selectedGroup, searchQuery) { mutableStateOf(80) }
    val displayedChannels = remember(channels, visibleLimit.value) {
        channels.take(visibleLimit.value)
    }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index >= gridState.layoutInfo.totalItemsCount - 12
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && channels.size > displayedChannels.size) {
            visibleLimit.value += 80
        }
    }
    val channelsByGroup = remember(channels) {
        channels.groupBy { it.groupTitle ?: "" }
    }

    // Mobil için varsayılan öne çıkan (Showcase / Spotlight) video
    val featuredChannel = remember(channels, selectedType) {
        channels.firstOrNull { !it.logoUrl.isNullOrEmpty() } ?: channels.firstOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CSD Player",
                            color = NetflixRed,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.DarkGray,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = playlist.name.take(12),
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    // Verileri Yenile
                    IconButton(
                        onClick = onRefreshPlaylist,
                        modifier = Modifier.testTag("action_refresh_playlist")
                    ) {
                        Icon(Icons.Default.Refresh, "Yenile", tint = Color.White)
                    }
                    
                    // Mod Değiştir
                    IconButton(
                        onClick = onResetMode,
                        modifier = Modifier.testTag("action_reset_mode")
                    ) {
                        Icon(Icons.Default.Tv, "TV Moduna Geç", tint = Color.White)
                    }

                    // Sunucudan Çıkış
                    IconButton(
                        onClick = onDisconnect,
                        modifier = Modifier.testTag("action_disconnect")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Çıkış", tint = Color.LightGray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.85f))
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF060606))
                .padding(innerPadding)
        ) {
            // Elegant Full-Width Search Input (Dramatically better layout for portrait mobile screens)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChanged,
                placeholder = { Text("Kanal, film veya dizi ara...", fontSize = 12.sp, color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChanged("") }) {
                            Icon(Icons.Default.Clear, null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NetflixRed,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF161616),
                    unfocusedContainerColor = Color(0xFF111111)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .height(44.dp)
                    .testTag("dashboard_search_input")
            )

            // ANA TV/FİLM/DİZİ SEKMELERİ - Full Width Horizontal Scroll (Never Squeezed!)
            val types = listOf(
                "LIVE" to "Canlı TV",
                "MOVIE" to "Sinema",
                "SERIES" to "Diziler",
                "FAVORITE" to "Süreklilerim",
                "RECENT" to "Geçmiş"
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 16.dp)
            ) {
                items(types) { (typeKey, label) ->
                    val isSelected = selectedType == typeKey
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) NetflixRed else Color(0xFF1A1A1A),
                        border = BorderStroke(1.dp, if (isSelected) NetflixRed else Color.Transparent),
                        modifier = Modifier
                            .tvClickable(isTvMode = false) {
                                onTypeSelected(typeKey)
                            }
                            .testTag("nav_tab_$typeKey")
                    ) {
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // KATEGORİ SEÇİCİLER (Sadece Arama boşsa ve listeler filtreli değilse)
            if (selectedType != "FAVORITE" && selectedType != "RECENT" && searchQuery.isEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(end = 16.dp)
                ) {
                    item {
                        val isSelected = selectedGroup == "Hepsi" || selectedGroup == null
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) NetflixRed.copy(alpha = 0.15f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (isSelected) NetflixRed else Color.DarkGray),
                            modifier = Modifier
                                .tvClickable(isTvMode = false) {
                                    onGroupSelected("Hepsi")
                                }
                                .testTag("group_chip_all")
                        ) {
                            Text(
                                text = "Tüm Kategoriler",
                                color = if (isSelected) NetflixRed else Color.LightGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    items(groups) { groupName ->
                        val isSelected = selectedGroup == groupName
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) NetflixRed.copy(alpha = 0.15f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (isSelected) NetflixRed else Color.DarkGray),
                            modifier = Modifier
                                .tvClickable(isTvMode = false) {
                                    onGroupSelected(groupName)
                                }
                                .testTag("group_chip_$groupName")
                        ) {
                            Text(
                                text = groupName,
                                color = if (isSelected) NetflixRed else Color.LightGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // ANA İÇERİK PANELİ
            val isSearchingOrFiltered = searchQuery.isNotEmpty() || (selectedGroup != null && selectedGroup != "Hepsi") || selectedType == "FAVORITE" || selectedType == "RECENT"

            if (isSearchingOrFiltered) {
                // Arama ve Filtre Durumunda: Netflix Kart Grid Görünümü
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (channels.isEmpty()) {
                        EmptyState()
                    } else {
                        val isLandscapeCard = selectedType == "LIVE"
                        val dynamicColumns = if (isLandscapeCard) GridCells.Adaptive(130.dp) else GridCells.Adaptive(95.dp)

                        LazyVerticalGrid(
                            columns = dynamicColumns,
                            state = gridState,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(displayedChannels, key = { it.uniqueId }) { channel ->
                                ChannelGridCard(
                                    channel = channel,
                                    isTvMode = false,
                                    isLandscape = isLandscapeCard,
                                    onClick = { onChannelSelected(channel) },
                                    onToggleFav = { onToggleFavorite(channel) }
                                )
                            }

                            if (channels.size > displayedChannels.size) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = NetflixRed, modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Varsayılan Nefes Kesici Netflix Görünümü: Hero Showcase ve Kategorisel Yatay Kaydırma Satırları
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 1. Öne Çıkan Spot Işığı Afiş (Hero Banner)
                    featuredChannel?.let { spotlight ->
                        item {
                            MobileHeroSpotlight(
                                channel = spotlight,
                                typeName = when (selectedType) {
                                    "LIVE" -> "CANLI YAYIN"
                                    "MOVIE" -> "SİNEMA FİLMİ"
                                    "SERIES" -> "ÖDÜLLÜ DİZİ"
                                    else -> "POPÜLER"
                                },
                                onPlay = { onChannelSelected(spotlight) },
                                onToggleFav = { onToggleFavorite(spotlight) }
                            )
                        }
                    }

                    // 2. Netflix Yatay Satırları
                    val categorizedGroups = groups.take(8) // Aşırı yüklenme olmasın diye ilk 8 kategoriyi basıyoruz
                    if (categorizedGroups.isEmpty() && channels.isNotEmpty()) {
                        // Eğer kategori yoksa düz bir yatay satır bas
                        item {
                            NetflixRow(
                                title = "Tüm Yayınlar",
                                channels = channels,
                                isLandscape = (selectedType == "LIVE"),
                                onChannelSelected = onChannelSelected,
                                onToggleFavorite = onToggleFavorite
                              )
                        }
                    } else {
                        items(categorizedGroups) { groupName ->
                            val groupChannels = remember(channelsByGroup, groupName) {
                                (channelsByGroup[groupName] ?: emptyList()).take(15)
                            }
                            if (groupChannels.isNotEmpty()) {
                                NetflixRow(
                                    title = groupName,
                                    channels = groupChannels,
                                    isLandscape = (selectedType == "LIVE"),
                                    onChannelSelected = onChannelSelected,
                                    onToggleFavorite = onToggleFavorite
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// ANDROID TV NETFLIX ARAYÜZÜ (D-Pad Grid)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvDashboard(
    playlist: Playlist,
    channels: List<IptvChannel>,
    groups: List<String>,
    selectedType: String,
    selectedGroup: String?,
    searchQuery: String,
    onTypeSelected: (String) -> Unit,
    onGroupSelected: (String?) -> Unit,
    onSearchChanged: (String) -> Unit,
    onChannelSelected: (IptvChannel) -> Unit,
    onToggleFavorite: (IptvChannel) -> Unit,
    onDisconnect: () -> Unit,
    onResetMode: () -> Unit,
    onRefreshPlaylist: () -> Unit
) {
    val visibleLimit = remember(selectedType, selectedGroup, searchQuery) { mutableStateOf(80) }
    val displayedChannels = remember(channels, visibleLimit.value) {
        channels.take(visibleLimit.value)
    }
    val tvGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val tvShouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = tvGridState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index >= tvGridState.layoutInfo.totalItemsCount - 12
        }
    }
    LaunchedEffect(tvShouldLoadMore) {
        if (tvShouldLoadMore && channels.size > displayedChannels.size) {
            visibleLimit.value += 80
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CSD Player",
                            color = NetflixRed,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = playlist.name,
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                actions = {
                    // Verileri Yenile
                    IconButton(
                        onClick = onRefreshPlaylist,
                        modifier = Modifier.testTag("action_refresh_playlist")
                    ) {
                        Icon(Icons.Default.Refresh, "Yenile", tint = Color.White)
                    }
                    
                    // Mobil Moduna Dön
                    IconButton(
                        onClick = onResetMode,
                        modifier = Modifier.testTag("action_reset_mode")
                    ) {
                        Icon(Icons.Default.PhoneAndroid, "Mobil Moduna Geç", tint = Color.White)
                    }

                    // Sunucudan Çıkış
                    IconButton(
                        onClick = onDisconnect,
                        modifier = Modifier.testTag("action_disconnect")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Çıkış Yap", tint = Color.LightGray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F0F))
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0D0D))
                .padding(innerPadding)
        ) {
            // TV için Gelişmiş Navigasyon Satırı (D-Pad Odaklı)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val types = listOf(
                    "LIVE" to "Canlı TV",
                    "MOVIE" to "Sinema",
                    "SERIES" to "Diziler",
                    "FAVORITE" to "Süreklilerim (Favoriler)",
                    "RECENT" to "İzleme Geçmişi"
                )

                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(types, key = { it.first }) { (typeKey, label) ->
                        val isSelected = selectedType == typeKey
                        val selectTabClick = remember(typeKey) { { onTypeSelected(typeKey) } }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) NetflixRed else Color(0xFF202020),
                            modifier = Modifier
                                .tvClickable(isTvMode = true, onClick = selectTabClick)
                                .testTag("nav_tab_$typeKey")
                        ) {
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // TV Arama Çubuğu
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChanged,
                    placeholder = { Text("Kanal / film / dizi ara...", fontSize = 12.sp, color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NetflixRed,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF222222),
                        unfocusedContainerColor = Color(0xFF1B1B1B)
                    ),
                    modifier = Modifier
                        .width(240.dp)
                        .height(46.dp)
                        .testTag("dashboard_search_input")
                )
            }

            // TV İKİLİ PANEL (Sol Kategori D-Pad Çekmecesi, Sağ Yayın Gridi)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // SOL KATEGORİ PANELİ (Sadece TV modunda ve Favoriler/Geçmiş değilse)
                if (selectedType != "FAVORITE" && selectedType != "RECENT") {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(200.dp)
                            .background(Color(0xFF141414))
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            item {
                                val isSelected = selectedGroup == "Hepsi" || selectedGroup == null
                                val selectAllClick = remember { { onGroupSelected("Hepsi") } }
                                Surface(
                                    color = if (isSelected) NetflixRed.copy(alpha = 0.25f) else Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .tvClickable(isTvMode = true, onClick = selectAllClick)
                                        .testTag("group_item_all")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AllInbox,
                                            contentDescription = null,
                                            tint = if (isSelected) NetflixRed else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Tüm Kategoriler",
                                            color = if (isSelected) Color.White else Color.LightGray,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            items(groups, key = { it }) { groupName ->
                                val isSelected = selectedGroup == groupName
                                val selectGroupClick = remember(groupName) { { onGroupSelected(groupName) } }
                                Surface(
                                    color = if (isSelected) NetflixRed.copy(alpha = 0.25f) else Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .tvClickable(isTvMode = true, onClick = selectGroupClick)
                                        .testTag("group_item_$groupName")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = if (isSelected) NetflixRed else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = groupName,
                                            color = if (isSelected) Color.White else Color.LightGray,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // SAĞ AKIŞ PANELİ (Grid)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp)
                ) {
                    if (channels.isEmpty()) {
                        EmptyState()
                    } else {
                        val isLandscapeCard = selectedType == "LIVE"
                        val dynamicColumns = if (isLandscapeCard) GridCells.Adaptive(160.dp) else GridCells.Adaptive(115.dp)

                        LazyVerticalGrid(
                            columns = dynamicColumns,
                            state = tvGridState,
                            contentPadding = PaddingValues(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(displayedChannels, key = { it.uniqueId }) { channel ->
                                val clickAction = remember(channel.uniqueId) { { onChannelSelected(channel) } }
                                val toggleFavAction = remember(channel.uniqueId) { { onToggleFavorite(channel) } }
                                ChannelGridCard(
                                    channel = channel,
                                    isTvMode = true,
                                    isLandscape = isLandscapeCard,
                                    onClick = clickAction,
                                    onToggleFav = toggleFavAction
                                )
                            }

                            if (channels.size > displayedChannels.size) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = NetflixRed, modifier = Modifier.size(24.dp))
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

// ==========================================
// TASARIMSAL ALT BİLEŞENLER / COMPOSABLES
// ==========================================

@Composable
fun MobileHeroSpotlight(
    channel: IptvChannel,
    typeName: String,
    onPlay: () -> Unit,
    onToggleFav: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(290.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Immersive background poster
            if (!channel.logoUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Dark shading vignette to maximize readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.sweepGradient(
                                colors = listOf(Color(0xFF141414), Color(0xFF281111), Color(0xFF141414))
                            )
                        )
                )
            }
            
            // Vignette gradient that fades out cleanly towards the bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.1f),
                                Color.Black.copy(alpha = 0.45f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
            )

            // Showcase İçerikleri
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = NetflixRed,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = typeName,
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = channel.groupTitle ?: "CSD VIP List",
                    color = Color.LightGray.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                // Eylemler Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPlay,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Seç ve İzle", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Favori Ekle
                    Button(
                        onClick = onToggleFav,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262626)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (channel.isFavorite) NetflixRed else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (channel.isFavorite) "Listede" else "Listeme Ekle",
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

@Composable
fun NetflixRow(
    title: String,
    channels: List<IptvChannel>,
    isLandscape: Boolean,
    onChannelSelected: (IptvChannel) -> Unit,
    onToggleFavorite: (IptvChannel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        // Row Title
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )

        // Row Content Scrolling
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(channels, key = { it.uniqueId }) { channel ->
                Box(
                    modifier = Modifier.width(if (isLandscape) 130.dp else 95.dp)
                ) {
                    ChannelGridCard(
                        channel = channel,
                        isTvMode = false,
                        isLandscape = isLandscape,
                        onClick = { onChannelSelected(channel) },
                        onToggleFav = { onToggleFavorite(channel) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelGridCard(
    channel: IptvChannel,
    isTvMode: Boolean,
    isLandscape: Boolean,
    onClick: () -> Unit,
    onToggleFav: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .tvClickable(isTvMode = isTvMode, shape = RoundedCornerShape(8.dp)) {
                onClick()
            }
            .testTag("channel_card_${channel.uniqueId}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (isLandscape) 1.58f else 0.72f)
                    .background(Color.Black)
            ) {
                // Logo / Afiş Resmi
                if (!channel.logoUrl.isNullOrEmpty()) {
                    val context = LocalContext.current
                    val imageRequest = remember(channel.logoUrl, isLandscape) {
                        ImageRequest.Builder(context)
                            .data(channel.logoUrl)
                            .crossfade(true)
                            .size(if (isLandscape) 280 else 200, if (isLandscape) 180 else 280) // Downsample to typical card size
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (channel.type == "LIVE") Icons.Default.Tv else Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = Color.DarkGray,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = channel.name.take(1).uppercase(),
                                color = Color.Gray,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                // Gradients fallback to maximize visual consistency
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                            )
                        )
                )

                // Premium floating Favorite circle
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .align(Alignment.TopStart)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(13.dp))
                            .clickable { onToggleFav() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favori",
                            tint = if (channel.isFavorite) NetflixRed else Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // Canlı TV simgesi
                if (channel.type == "LIVE") {
                    Surface(
                        shape = RoundedCornerShape(topEnd = 3.dp),
                        color = NetflixRed,
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        Text(
                            text = "CANLI",
                            color = Color.White,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Başlık Detayı
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.FilterList,
            contentDescription = null,
            tint = Color.DarkGray,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Bu filtrede gösterilecek yayın bulunamadı.",
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}
