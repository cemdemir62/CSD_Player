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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF08080B)),
            border = BorderStroke(1.5.dp, NetflixRed.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(if (isTvMode) 0.86f else 0.95f)
                .fillMaxHeight(if (isTvMode) 0.88f else 0.92f)
                .padding(6.dp)
                .testTag("app_settings_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Settings Header Redesigned as a Premium Title & Live Status Area
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = NetflixRed.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, NetflixRed.copy(alpha = 0.35f)),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = NetflixRed,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "ZULA KONTROL MERKEZİ",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                // Elegant Badge
                                Surface(
                                    color = Color(0xFFE50914).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE50914).copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "PANEL",
                                        color = Color(0xFFFF5252),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Mevcut Paket: ${playlist.name}",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Top Dismiss Trigger
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF18181F), CircleShape)
                            .border(1.dp, Color(0xFF2D2D35), CircleShape)
                            .focusRequester(dismissFocusRequester)
                            .testTag("action_dismiss_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Layout divided into left tabs sidebar/panel and right side layout details on TV Mode
                if (isTvMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Left Tab list for TV screen
                        Column(
                            modifier = Modifier
                                .width(230.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF101015), RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFF1E1E26), RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "KATEGORİLER",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.5.sp,
                                modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp)
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
                                .background(Color(0xFF101015), RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFF1E1E26), RoundedCornerShape(16.dp))
                                .padding(20.dp)
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
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                                label = "Sistem & Cihaz",
                                icon = Icons.Default.Info,
                                isSelected = activeTab == 3,
                                onClick = { activeTab = 3 }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color(0xFF101015), RoundedCornerShape(20.dp))
                                .border(1.dp, Color(0xFF1E1E26), RoundedCornerShape(20.dp))
                                .padding(18.dp)
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Subscription active gradient card header
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .background(Brush.horizontalGradient(listOf(Color(0xFF0F2617), Color(0xFF0C1D12))))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFF4CAF50), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "ABONELİK DURUMU: AKTİF",
                            color = Color(0xFF81C784),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "$daysRemaining Gün Kaldı",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Split metrics redesigned as Beautiful Grid-breaking Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        title = "Canlı TV",
                        count = "$liveCount",
                        icon = Icons.Default.Tv,
                        tint = Color(0xFF2196F3)
                    )
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        title = "Filmler",
                        count = "$movieCount",
                        icon = Icons.Default.Movie,
                        tint = Color(0xFFE91E63)
                    )
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        title = "Diziler",
                        count = "$seriesCount",
                        icon = Icons.Default.VideoLibrary,
                        tint = Color(0xFFFF9800)
                    )
                }

                // Info entries structured within an advanced Glass Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF13131A)),
                    border = BorderStroke(1.dp, Color(0xFF1E1E26)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "BAĞLANTI VE SUNUCU DETAYLARI",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        InfoRow("Paket İsmi", playlist.name)
                        HorizontalDivider(color = Color(0xFF1E1E26), thickness = 1.dp)
                        InfoRow("Bağlantı Türü", playlist.type)
                        if (playlist.type == "XTREAM") {
                            HorizontalDivider(color = Color(0xFF1E1E26), thickness = 1.dp)
                            InfoRow("Kullanıcı Adı", playlist.username ?: "Bilinmiyor")
                        }
                        HorizontalDivider(color = Color(0xFF1E1E26), thickness = 1.dp)
                        InfoRow("Sunucu Hostu", serverHost)
                        HorizontalDivider(color = Color(0xFF1E1E26), thickness = 1.dp)
                        InfoRow("Oluşturulma Tarihi", creationFormatted)
                        HorizontalDivider(color = Color(0xFF1E1E26), thickness = 1.dp)
                        InfoRow("Bitiş/Yenileme Tarihi", expirationFormatted)
                    }
                }
            }
        }
        1 -> {
            // PLAYER & ZAPPING SETTINGS PANEL
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "YAYIN MOTORU YAPILANDIRMASI",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                // Low Latency Mode Row
                SettingsToggleRow(
                    title = "ExoPlayer Düşük Gecikme Modu",
                    subtitle = "Canlı yayınlarda ağ tampon süresini optimize ederek en yakın akışı yakalar.",
                    checked = lowLatencyMode,
                    isTvMode = isTvMode,
                    onCheckedChange = onLowLatencyChanged
                )

                // Zap Category lock Row
                SettingsToggleRow(
                    title = "Kategori İçi Zap Kilidi (D-Pad)",
                    subtitle = "Kanal zapping işlemi sırasında yanlışlıkla farklı kategoriye geçmeyi engeller.",
                    checked = zapLockCategory,
                    isTvMode = isTvMode,
                    onCheckedChange = onZapLockChanged
                )

                // Split Mode Switcher in dynamic Container card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF13131A)),
                    border = BorderStroke(1.dp, Color(0xFF1E1E26)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "VARSAYILAN KONTROL MODU",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Arayüz şemasını cihazınıza göre ayarlayın. D-Pad TV modu uzaktan kumandayla yön tuşlarını odaklar, Mobil mod dokunma hareketlerine duyarlıdır.",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        val switchInteraction = remember { MutableInteractionSource() }
                        Button(
                            onClick = onResetMode,
                            colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                            shape = RoundedCornerShape(10.dp),
                            interactionSource = switchInteraction,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .focusable(interactionSource = switchInteraction)
                                .tvFocusBorder(isTvMode = isTvMode, interactionSource = switchInteraction, shape = RoundedCornerShape(10.dp))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isTvMode) Icons.Default.PhoneAndroid else Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                val labelText = if (isTvMode) "Mobil Dokunmatik Moduna Geç" else "Televizyon (D-Pad) Moduna Geç"
                                Text(
                                    text = labelText,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
        2 -> {
            // MAINTENANCE PANEL
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "SİSTEM VE BELLEK OPTİMİZASYONU",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                // Clear watch history Card Container
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF13131A)),
                    border = BorderStroke(1.dp, Color(0xFF1E1E26)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Kaldığın Yerden Devam Et Geçmişi",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Yarıda bıraktığınız dizi ve film izleme kayıtlarını cihazınızdan tamamen siler.",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        val clearRecentInteraction = remember { MutableInteractionSource() }
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F27)),
                            border = BorderStroke(1.dp, Color(0xFF32323D)),
                            shape = RoundedCornerShape(8.dp),
                            interactionSource = clearRecentInteraction,
                            modifier = Modifier
                                .focusable(interactionSource = clearRecentInteraction)
                                .tvFocusBorder(isTvMode = isTvMode, interactionSource = clearRecentInteraction, shape = RoundedCornerShape(8.dp))
                        ) {
                            Text("Temizle", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Force refresh Playlist details from IPTV Server
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF13131A)),
                    border = BorderStroke(1.dp, Color(0xFF1E1E26)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Oynatma Listesini Güncelle",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Eksik kanalları gidermek amacıyla sunucu veritabanını sıfırdan indirir.",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))

                        val forceRefreshInteraction = remember { MutableInteractionSource() }
                        Button(
                            onClick = {
                                onRefreshPlaylist()
                                Toast.makeText(context, "IPTV oynatma listesi güncellemesi tetiklendi!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                            shape = RoundedCornerShape(8.dp),
                            interactionSource = forceRefreshInteraction,
                            modifier = Modifier
                                .focusable(interactionSource = forceRefreshInteraction)
                                .tvFocusBorder(isTvMode = isTvMode, interactionSource = forceRefreshInteraction, shape = RoundedCornerShape(8.dp))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Yenile", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        3 -> {
            // SYSTEM INFO & SPEED TEST PANEL
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "SİSTEM TEŞHİSİ VE ANALİZİ",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                // Launch speed test block redesigned with beautiful gradient overlay
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, NetflixRed.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .background(Brush.horizontalGradient(colors = listOf(Color(0xFF220C0E), Color(0xFF0C0C10))))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = NetflixRed.copy(alpha = 0.15f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = NetflixRed,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Gelişmiş Ağ Hız Ölçer",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Yayın akışı öncesinde donmaları engellemek amacıyla bant genişliğini ve pingi eşzamanlı test eder.",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))

                        val runSpeedInteraction = remember { MutableInteractionSource() }
                        Button(
                            onClick = onLaunchSpeedTest,
                            colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                            shape = RoundedCornerShape(8.dp),
                            interactionSource = runSpeedInteraction,
                            modifier = Modifier
                                .focusable(interactionSource = runSpeedInteraction)
                                .tvFocusBorder(isTvMode = isTvMode, interactionSource = runSpeedInteraction, shape = RoundedCornerShape(8.dp))
                        ) {
                            Text("Hız Testi", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Specs list restructured
                val buildModel = android.os.Build.MODEL
                val buildVersion = android.os.Build.VERSION.RELEASE
                val buildSdk = android.os.Build.VERSION.SDK_INT

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF13131A)),
                    border = BorderStroke(1.dp, Color(0xFF1E1E26)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "DONANIM VE LİSANS BİLGİLERİ",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        InfoRow("Cihaz Modeli", buildModel)
                        HorizontalDivider(color = Color(0xFF1E1E26), thickness = 1.dp)
                        InfoRow("Android Versiyonu", "Android $buildVersion (API $buildSdk)")
                        HorizontalDivider(color = Color(0xFF1E1E26), thickness = 1.dp)
                        InfoRow("Uygulama Sürümü", "v2.8.5 Premium TV")
                        HorizontalDivider(color = Color(0xFF1E1E26), thickness = 1.dp)
                        InfoRow("Yürütme Altyapısı", "Google ExoPlayer Media3")
                        HorizontalDivider(color = Color(0xFF1E1E26), thickness = 1.dp)
                        InfoRow("Sürüm Derlemesi", "3ea53d0-2659-4394")
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
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
    modifier: Modifier = Modifier,
    title: String,
    count: String,
    icon: ImageVector,
    tint: Color
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF13131A),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = tint.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = count,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = title,
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
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
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF13131A))
            .border(1.dp, Color(0xFF1E1E26), RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = { onCheckedChange(!checked) }
            )
            .focusable(interactionSource = interactionSource)
            .tvFocusBorder(isTvMode = isTvMode, interactionSource = interactionSource, shape = RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 11.sp,
                lineHeight = 15.sp
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

@Composable
fun TvSettingsTabButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    isTvMode: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) NetflixRed else Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .tvFocusBorder(isTvMode = isTvMode, interactionSource = interactionSource, shape = RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(vertical = 11.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else Color.LightGray,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                color = if (isSelected) Color.White else Color.LightGray,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun MobileSettingsTabButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) NetflixRed else Color(0xFF13131A),
        border = BorderStroke(1.dp, if (isSelected) NetflixRed.copy(alpha = 0.5f) else Color(0xFF1E1E26)),
        modifier = Modifier
            .height(38.dp)
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
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = if (isSelected) Color.White else Color.LightGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
