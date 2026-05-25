package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.IptvChannel
import com.example.data.model.Playlist
import com.example.ui.theme.NetflixDarkGrey
import com.example.ui.theme.NetflixLightGrey
import com.example.ui.theme.NetflixRed
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    show: Boolean,
    playlist: Playlist,
    channels: List<IptvChannel>,
    isTvMode: Boolean,
    onDismiss: () -> Unit,
    onResetMode: () -> Unit,
    onRefreshPlaylist: () -> Unit,
    onLaunchSpeedTest: () -> Unit
) {
    if (!show) return

    val context = LocalContext.current
    val sharedPrefs = remember(context) {
        context.getSharedPreferences("zula_iptv_prefs", android.content.Context.MODE_PRIVATE)
    }

    // Dynamic states synced with Player preferences
    var zapLockCategory by remember {
        mutableStateOf(sharedPrefs.getBoolean("zap_lock_category", false))
    }
    var lowLatencyMode by remember {
        mutableStateOf(sharedPrefs.getBoolean("low_latency_mode", true))
    }

    var activeTab by remember { mutableStateOf(0) } // 0: Subscription, 1: Player Settings, 2: Maintenance, 3: System Specs
    val dismissFocusRequester = remember { FocusRequester() }

    LaunchedEffect(show) {
        if (show) {
            dismissFocusRequester.requestFocus()
        }
    }

    // Parse domain or host from playlist URL
    val serverHost = remember(playlist.url) {
        try {
            val urlObj = java.net.URL(playlist.url)
            val portText = if (urlObj.port != -1 && urlObj.port != 80 && urlObj.port != 443) ":${urlObj.port}" else ""
            "${urlObj.host}$portText"
        } catch (e: Exception) {
            if (playlist.url.startsWith("http")) playlist.url.substringBefore("/get.php").substringAfter("://") else "Yerel Playlist"
        }
    }

    // Calculate elegant dates based on database entry
    val creationTimeFormatted = remember(playlist.createdAt) {
        val date = Date(playlist.createdAt)
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr"))
        sdf.format(date)
    }

    // Subscription expirations usually simulated to 1 year for IPTV links from creation
    val expirationTimeFormatted = remember(playlist.createdAt) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = playlist.createdAt
        calendar.add(Calendar.YEAR, 1)
        val date = calendar.time
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr"))
        sdf.format(date)
    }

    val daysRemaining = remember(playlist.createdAt) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = playlist.createdAt
        calendar.add(Calendar.YEAR, 1)
        val expMs = calendar.timeInMillis
        val diffMs = expMs - System.currentTimeMillis()
        val days = (diffMs / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
        days
    }

    // Dynamic Playlist metrics
    val liveCount = remember(channels) { channels.count { it.type == "LIVE" } }
    val movieCount = remember(channels) { channels.count { it.type == "MOVIE" } }
    val seriesCount = remember(channels) { channels.count { it.type == "SERIES" } }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
            border = BorderStroke(2.dp, NetflixRed.copy(alpha = 0.85f)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(if (isTvMode) 0.82f else 0.95f)
                .fillMaxHeight(if (isTvMode) 0.85f else 0.90f)
                .padding(8.dp)
                .testTag("app_settings_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Settings Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = NetflixRed.copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = NetflixRed,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Uygulama & Yayın Ayarları",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Aktif Paket: ${playlist.name}",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Top Dismiss Trigger
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .focusRequester(dismissFocusRequester)
                            .testTag("action_dismiss_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = Color.LightGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Layout divided into left tabs sidebar/panel and right side layout details on TV Mode
                if (isTvMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Left Tab list for TV screen
                        Column(
                            modifier = Modifier
                                .width(220.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF141414), RoundedCornerShape(12.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "KATEGORİLER",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                            )

                            TvSettingsTabButton(
                                label = "Yayın Aboneliği",
                                icon = Icons.Default.CardMembership,
                                isSelected = activeTab == 0,
                                isTvMode = isTvMode,
                                onClick = { activeTab = 0 }
                            )

                            TvSettingsTabButton(
                                label = "Oynatma Ayarları",
                                icon = Icons.Default.Tune,
                                isSelected = activeTab == 1,
                                isTvMode = isTvMode,
                                onClick = { activeTab = 1 }
                            )

                            TvSettingsTabButton(
                                label = "Bakım & Bellek",
                                icon = Icons.Default.CleaningServices,
                                isSelected = activeTab == 2,
                                isTvMode = isTvMode,
                                onClick = { activeTab = 2 }
                            )

                            TvSettingsTabButton(
                                label = "Sistem & Cihaz",
                                icon = Icons.Default.Info,
                                isSelected = activeTab == 3,
                                isTvMode = isTvMode,
                                onClick = { activeTab = 3 }
                            )
                        }

                        // Right panel layout details
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Color(0xFF111111), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            SettingsPanelContent(
                                tab = activeTab,
                                playlist = playlist,
                                serverHost = serverHost,
                                creationFormatted = creationTimeFormatted,
                                expirationFormatted = expirationTimeFormatted,
                                daysRemaining = daysRemaining,
                                liveCount = liveCount,
                                movieCount = movieCount,
                                seriesCount = seriesCount,
                                zapLockCategory = zapLockCategory,
                                lowLatencyMode = lowLatencyMode,
                                isTvMode = isTvMode,
                                onZapLockChanged = {
                                    zapLockCategory = it
                                    sharedPrefs.edit().putBoolean("zap_lock_category", it).apply()
                                },
                                onLowLatencyChanged = {
                                    lowLatencyMode = it
                                    sharedPrefs.edit().putBoolean("low_latency_mode", it).apply()
                                },
                                onResetMode = onResetMode,
                                onRefreshPlaylist = onRefreshPlaylist,
                                onLaunchSpeedTest = onLaunchSpeedTest
                            )
                        }
                    }
                } else {
                    // Mobile Layout: Top dynamic horizontal Scroll tabs, and direct panel below
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MobileSettingsTabButton(
                                label = "Abonelik",
                                icon = Icons.Default.CardMembership,
                                isSelected = activeTab == 0,
                                onClick = { activeTab = 0 }
                            )
                            MobileSettingsTabButton(
                                label = "Oynatma",
                                icon = Icons.Default.Tune,
                                isSelected = activeTab == 1,
                                onClick = { activeTab = 1 }
                            )
                            MobileSettingsTabButton(
                                label = "Önbellek",
                                icon = Icons.Default.CleaningServices,
                                isSelected = activeTab == 2,
                                onClick = { activeTab = 2 }
                            )
                            MobileSettingsTabButton(
                                label = "Sistem/Cihaz",
                                icon = Icons.Default.Info,
                                isSelected = activeTab == 3,
                                onClick = { activeTab = 3 }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color(0xFF141414), RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            SettingsPanelContent(
                                tab = activeTab,
                                playlist = playlist,
                                serverHost = serverHost,
                                creationFormatted = creationTimeFormatted,
                                expirationFormatted = expirationTimeFormatted,
                                daysRemaining = daysRemaining,
                                liveCount = liveCount,
                                movieCount = movieCount,
                                seriesCount = seriesCount,
                                zapLockCategory = zapLockCategory,
                                lowLatencyMode = lowLatencyMode,
                                isTvMode = isTvMode,
                                onZapLockChanged = {
                                    zapLockCategory = it
                                    sharedPrefs.edit().putBoolean("zap_lock_category", it).apply()
                                },
                                onLowLatencyChanged = {
                                    lowLatencyMode = it
                                    sharedPrefs.edit().putBoolean("low_latency_mode", it).apply()
                                },
                                onResetMode = onResetMode,
                                onRefreshPlaylist = onRefreshPlaylist,
                                onLaunchSpeedTest = onLaunchSpeedTest
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsPanelContent(
    tab: Int,
    playlist: Playlist,
    serverHost: String,
    creationFormatted: String,
    expirationFormatted: String,
    daysRemaining: Long,
    liveCount: Int,
    movieCount: Int,
    seriesCount: Int,
    zapLockCategory: Boolean,
    lowLatencyMode: Boolean,
    isTvMode: Boolean,
    onZapLockChanged: (Boolean) -> Unit,
    onLowLatencyChanged: (Boolean) -> Unit,
    onResetMode: () -> Unit,
    onRefreshPlaylist: () -> Unit,
    onLaunchSpeedTest: () -> Unit
) {
    val context = LocalContext.current

    when (tab) {
        0 -> {
            // SUB INFO PANEL
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Subscription active card header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ABONELİK DURUMU: AKTİF",
                        color = Color(0xFF4CAF50),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "$daysRemaining Gün Kaldı",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Split metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBox(weight = 1f, title = "Canlı TV", count = "$liveCount", icon = Icons.Default.Tv, tint = Color(0xFF2196F3))
                    MetricBox(weight = 1f, title = "Filmler", count = "$movieCount", icon = Icons.Default.Movie, tint = Color(0xFFE91E63))
                    MetricBox(weight = 1f, title = "Diziler", count = "$seriesCount", icon = Icons.Default.VideoLibrary, tint = Color(0xFFFF9800))
                }

                // Info entries
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141414), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InfoRow("Paket İsmi", playlist.name)
                    InfoRow("Bağlantı Türü", playlist.type)
                    if (playlist.type == "XTREAM") {
                        InfoRow("Kullanıcı Adı", playlist.username ?: "Bilinmiyor")
                    }
                    InfoRow("Sunucu Hostu", serverHost)
                    InfoRow("Oluşturulma Tarihi", creationFormatted)
                    InfoRow("Bitiş/Yenileme Tarihi", expirationFormatted)
                }
            }
        }
        1 -> {
            // PLAYER & ZAPPING SETTINGS PANEL
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Oynatıcı ve Zap-Lock Gelişmiş Ayarlar",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                // Low Latency Mode Row
                SettingsToggleRow(
                    title = "ExoPlayer Düşük Gecikme Modu",
                    subtitle = "Canlı (Live TV) yayınlarında ağ gecikmesini optimize ederek en yakın canlı yayını yakalar.",
                    checked = lowLatencyMode,
                    isTvMode = isTvMode,
                    onCheckedChange = onLowLatencyChanged
                )

                // Zap Category lock Row
                SettingsToggleRow(
                    title = "Akıllı Kategori İçi Zap Kilidi",
                    subtitle = "D-Pad Yukarı/Aşağı tuşları ile kanal değiştirirken (Zapping) mevcut kategori sınırları dışına çıkmayı engeller.",
                    checked = zapLockCategory,
                    isTvMode = isTvMode,
                    onCheckedChange = onZapLockChanged
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Mode reset action trigger (TV Mode <-> Mobile Mode switcher UI)
                val isSwitchedToTv = !isTvMode
                val switchInteraction = remember { MutableInteractionSource() }
                Button(
                    onClick = onResetMode,
                    colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                    shape = RoundedCornerShape(8.dp),
                    interactionSource = switchInteraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusable(switchInteraction)
                        .tvFocusBorder(isTvMode = isTvMode, interactionSource = switchInteraction)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isTvMode) Icons.Default.PhoneAndroid else Icons.Default.Tv,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isTvMode) "Dokunmatik Mobil Moduna Geç" else "Televizyon (TV Box D-Pad) Moduna Geç",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        2 -> {
            // MAINTENANCE & MAINTENANCE PANEL
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Önbellek Temizleme ve IPTV Veritabanı Güncelleme",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Aşağıdaki araçları kullanarak yayınları yeniden yükleyebilir, izleme geçmişini temizleyebilir ve uygulama performansını optimize edebilirsiniz.",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                // Clear continue watching history
                val clearRecentInteraction = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141414), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Kaldığın Yerden Devam Et Verilerini Sıfırla",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Yarıda bıraktığınız dizi bölümleri ve filmlerin izleme sürelerini temizler.",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                    Button(
                        onClick = {
                            val prefs = context.getSharedPreferences("zula_iptv_prefs", android.content.Context.MODE_PRIVATE)
                            val editor = prefs.edit()
                            prefs.all.keys.filter { it.startsWith("resume_pos_") || it.startsWith("series_") }.forEach {
                                editor.remove(it)
                            }
                            editor.apply()
                            Toast.makeText(context, "Kaldığın yerden devam et geçmişi sıfırlandı!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(6.dp),
                        interactionSource = clearRecentInteraction,
                        modifier = Modifier
                            .height(36.dp)
                            .focusable(clearRecentInteraction)
                            .tvFocusBorder(isTvMode = isTvMode, interactionSource = clearRecentInteraction)
                    ) {
                        Text("Temizle", color = Color.White, fontSize = 11.sp)
                    }
                }

                // Force refresh Playlist details from IPTV api
                val forceRefreshInteraction = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141414), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "IPTV Listesini Çevrimiçi Yeniden Çek",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Yayın listenizde eksik kanallar varsa, IPTV listesini sunucudan sıfırdan indirir.",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                    Button(
                        onClick = {
                            onRefreshPlaylist()
                            Toast.makeText(context, "IPTV oynatma listesi güncellemesi tetiklendi!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(6.dp),
                        interactionSource = forceRefreshInteraction,
                        modifier = Modifier
                            .height(36.dp)
                            .focusable(forceRefreshInteraction)
                            .tvFocusBorder(isTvMode = isTvMode, interactionSource = forceRefreshInteraction)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Şimdi Güncelle", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        3 -> {
            // DIAGNOSTICS & SYSTEM INFO PANEL
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Teşhis Bilgileri ve Hız Testi Aracı",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                // Launch speed test block
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NetflixRed.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        .border(1.dp, NetflixRed.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Gelişmiş İnternet Hız Ölçeri",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Mevcut bant genişliğinizi ölçüp hangi IPTV yayın kalitesini desteklediğinizi analiz edin.",
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            lineHeight = 13.sp
                        )
                    }

                    val runSpeedInteraction = remember { MutableInteractionSource() }
                    Button(
                        onClick = onLaunchSpeedTest,
                        colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                        shape = RoundedCornerShape(6.dp),
                        interactionSource = runSpeedInteraction,
                        modifier = Modifier
                            .height(36.dp)
                            .focusable(runSpeedInteraction)
                            .tvFocusBorder(isTvMode = isTvMode, interactionSource = runSpeedInteraction)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hız Ölçer", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Specs list
                val buildModel = android.os.Build.MODEL
                val buildVersion = android.os.Build.VERSION.RELEASE
                val buildSdk = android.os.Build.VERSION.SDK_INT

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141414), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InfoRow("Cihaz Modeli", buildModel)
                    InfoRow("Android Versiyonu", "Android $buildVersion (API $buildSdk)")
                    InfoRow("Uygulama Sürümü", "v2.8.5 Premium TV")
                    InfoRow("Yürütme Altyapısı", "Google ExoPlayer Media3")
                    InfoRow("Sürüm Derlemesi", "3ea53d0-2659-4394")
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
fun MetricBox(
    weight: Float,
    title: String,
    count: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF141414),
        modifier = Modifier.weight(weight)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = count,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = title,
                color = Color.Gray,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    isTvMode: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141414))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = { onCheckedChange(!checked) }
            )
            .focusable(interactionSource)
            .tvFocusBorder(isTvMode = isTvMode, interactionSource = interactionSource)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 10.sp,
                lineHeight = 13.sp
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NetflixRed,
                uncheckedThumbColor = Color.LightGray,
                uncheckedTrackColor = Color.DarkGray
            ),
            modifier = Modifier.scale(0.85f)
        )
    }
}

// Simple modifier helper extension for scale factor safely
fun Modifier.scale(scale: Float): Modifier = this.then(
    androidx.compose.ui.draw.scale(scale)
)

@Composable
fun TvSettingsTabButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    isTvMode: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) NetflixRed else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .focusable(interactionSource)
            .tvFocusBorder(isTvMode = isTvMode, interactionSource = interactionSource)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else Color.LightGray,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                color = if (isSelected) Color.White else Color.LightGray,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun MobileSettingsTabButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) NetflixRed else Color(0xFF1E1E1E),
        modifier = Modifier
            .height(36.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else Color.Gray,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (isSelected) Color.White else Color.LightGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
