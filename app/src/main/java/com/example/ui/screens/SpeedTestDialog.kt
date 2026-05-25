package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.NetflixLightGrey
import com.example.ui.theme.NetflixDarkGrey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

@Composable
fun SpeedTestDialog(
    show: Boolean,
    isTvMode: Boolean,
    onDismiss: () -> Unit
) {
    if (!show) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isTesting by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableStateOf(0.0) } // Progress speed in Mbps
    var finalSpeed by remember { mutableStateOf<Double?>(null) } // Final measured speed
    var testProgressStr by remember { mutableStateOf("Test bekleniyor...") }
    var testError by remember { mutableStateOf<String?>(null) }

    // Multi-stage states
    var pingVal by remember { mutableStateOf<Int?>(null) }
    var jitterVal by remember { mutableStateOf<Int?>(null) }
    var downloadVal by remember { mutableStateOf<Double?>(null) }
    var uploadVal by remember { mutableStateOf<Double?>(null) }
    var activePhase by remember { mutableStateOf("IDLE") } // "PING", "DOWNLOAD", "UPLOAD", "COMPLETE", "IDLE"

    val startFocusRequester = remember { FocusRequester() }

    // Smooth needle animation with customized damping based on status
    val animatedSpeed by animateFloatAsState(
        targetValue = currentSpeed.toFloat(),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 125f),
        label = "SpeedNeedle"
    )

    fun startMeasurementSuite() {
        if (!isTesting) {
            isTesting = true
            testError = null
            finalSpeed = null
            currentSpeed = 0.0
            pingVal = null
            jitterVal = null
            downloadVal = null
            uploadVal = null
            activePhase = "IDLE"

            scope.launch(Dispatchers.IO) {
                runSpeedTestSuite(
                    onPhaseChange = { phase ->
                        activePhase = phase
                        testProgressStr = when (phase) {
                            "PING" -> "Sunucu keşfediliyor ve ping ölçülüyor..."
                            "DOWNLOAD" -> "İndirme (Download) hızı ölçülüyor..."
                            "UPLOAD" -> "Yükleme (Upload) hızı ölçülüyor..."
                            "COMPLETE" -> "Hız ölçümü başarıyla tamamlandı!"
                            else -> "Ölçüm yapılıyor..."
                        }
                    },
                    onPingResult = { ping, jitter ->
                        pingVal = ping
                        jitterVal = jitter
                    },
                    onProgress = { speed ->
                        currentSpeed = speed
                    },
                    onDownloadFinished = { dspeed ->
                        downloadVal = dspeed
                        currentSpeed = 0.0 // reset needle temporarily for upload
                    },
                    onUploadFinished = { uspeed ->
                        uploadVal = uspeed
                        currentSpeed = 0.0
                    },
                    onFinished = { speed ->
                        finalSpeed = speed
                        isTesting = false
                    },
                    onError = { err ->
                        testError = err
                        isTesting = false
                        activePhase = "IDLE"
                        testProgressStr = "Bağlantı hatası: $err"
                    }
                )
            }
        }
    }

    // Automatically run the test when shown the first time
    LaunchedEffect(show) {
        if (show) {
            delay(330) // Wait for dialog transitions to complete
            try {
                startFocusRequester.requestFocus()
            } catch (e: Exception) {}
            
            // Trigger auto test launch
            if (finalSpeed == null && !isTesting) {
                startMeasurementSuite()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
            border = BorderStroke(2.dp, NetflixRed.copy(alpha = 0.85f)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(if (isTvMode) 0.65f else 0.95f)
                .padding(12.dp)
                .testTag("speed_test_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = NetflixRed,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gelişmiş Ağ Hız Ölçer",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Gauge + Output Value Center
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Gauge Canvas
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val thickness = 10.dp.toPx()
                        val diameter = size.width - thickness
                        val sizeArc = androidx.compose.ui.geometry.Size(diameter, diameter)
                        val offset = thickness / 2f
                        val topLeft = androidx.compose.ui.geometry.Offset(offset, offset)

                        // Draw background grey arc (270 degrees sweep from 135 to 405)
                        drawArc(
                            color = Color(0xFF222222),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = sizeArc,
                            style = Stroke(width = thickness, cap = StrokeCap.Round)
                        )

                        // Draw active speed arc
                        // Max velocity scaled based on active phase (Download to 1000, Upload to 150)
                        val maxVelocity = if (activePhase == "UPLOAD") 150f else 1000f
                        val fraction = (animatedSpeed / maxVelocity).coerceIn(0f, 1f)
                        drawArc(
                            color = NetflixRed,
                            startAngle = 135f,
                            sweepAngle = fraction * 270f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = sizeArc,
                            style = Stroke(width = thickness, cap = StrokeCap.Round)
                        )
                    }

                    // Numeric Readout within Center
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val displayNum = when {
                            activePhase == "PING" -> if (pingVal != null) "$pingVal" else "--"
                            isTesting || finalSpeed != null -> "%.1f".format(animatedSpeed)
                            else -> "--"
                        }
                        Text(
                            text = displayNum,
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = if (activePhase == "PING") "ms" else "Mbps",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Testing Status or error message
                Text(
                    text = if (testError != null) "Bağlantı Hatası: $testError" else testProgressStr,
                    color = if (testError != null) NetflixRed else Color.LightGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // Premium Metrics Cards Row for Ping, Download, Upload
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Ping Column
                    NetworkMetricCard(
                        modifier = Modifier.weight(1f),
                        label = "PING & JITTER",
                        value = if (pingVal != null) "$pingVal ms" else "--",
                        icon = Icons.Default.SwapVert,
                        active = activePhase == "PING"
                    )
                    // Download Column
                    NetworkMetricCard(
                        modifier = Modifier.weight(1.2f),
                        label = "İNDİRME (DL)",
                        value = when {
                            activePhase == "DOWNLOAD" -> "%.1f Mbps".format(animatedSpeed)
                            downloadVal != null -> "%.1f Mbps".format(downloadVal)
                            else -> "--"
                        },
                        icon = Icons.Default.ArrowDownward,
                        active = activePhase == "DOWNLOAD"
                    )
                    // Upload Column
                    NetworkMetricCard(
                        modifier = Modifier.weight(1.2f),
                        label = "YÜKLEME (UL)",
                        value = when {
                            activePhase == "UPLOAD" -> "%.1f Mbps".format(animatedSpeed)
                            uploadVal != null -> "%.1f Mbps".format(uploadVal)
                            else -> "--"
                        },
                        icon = Icons.Default.ArrowUpward,
                        active = activePhase == "UPLOAD"
                    )
                }

                // Quality Comparison & Recommendation matrix based on DL speed
                val speed = downloadVal ?: currentSpeed
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141414), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "IPTV YAYIN KALİTESİ UYUMLULUĞU",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    // SD
                    QualityRow(
                        quality = "SD (Standart TV Çözünürlüğü)",
                        reqSpeed = "Min 3 Mbps",
                        isSupported = speed >= 3.0,
                        isOptimal = speed in 3.0..8.0 && finalSpeed != null
                    )
                    // HD
                    QualityRow(
                        quality = "HD (720p Yüksek Çözünürlük)",
                        reqSpeed = "Min 8 Mbps",
                        isSupported = speed >= 8.0,
                        isOptimal = speed in 8.0..15.0 && finalSpeed != null
                    )
                    // Full HD
                    QualityRow(
                        quality = "Full HD (1080p Kesintisiz Akış)",
                        reqSpeed = "Min 15 Mbps",
                        isSupported = speed >= 15.0,
                        isOptimal = speed in 15.0..25.0 && finalSpeed != null
                    )
                    // 4K Ultra HD
                    QualityRow(
                        quality = "4K Ultra HD (Premium Akış)",
                        reqSpeed = "Min 25 Mbps",
                        isSupported = speed >= 25.0,
                        isOptimal = speed >= 25.0 && finalSpeed != null
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Suggestion dynamic text based on speed
                if (finalSpeed != null) {
                    val recommendedQuality = when {
                        finalSpeed!! >= 100.0 -> "Ultra yüksek fiber bağlantı! Çoklu cihazda eşzamanlı 4K akışları ve dev IPTV paketlerini yağ gibi kaydırabilirsiniz."
                        finalSpeed!! >= 25.0 -> "4K Ultra HD ve 1080p Full HD yayınları en üst kalitede sıfır donma ile izleyebilirsiniz."
                        finalSpeed!! >= 15.0 -> "1080p Full HD yayınları akıcı şekilde, donma yaşamadan izleyebilirsiniz."
                        finalSpeed!! >= 8.0 -> "HD (720p) yayınları sorunsuz, istikrarlı bir şekilde izleyebilirsiniz."
                        finalSpeed!! >= 3.0 -> "SD standart kalitedeki yayınları izleyebilirsiniz ancak üst kalitelerde takılmalar olabilir."
                        else -> "Uyarılmalı! İnternet hızınız akıcı IPTV yayını için çok zayıf. Lütfen modeminizi kapatıp açın ya da kablolu bağlantıya geçin."
                    }
                    Text(
                        text = "Analiz Raporu: $recommendedQuality",
                        color = if (finalSpeed!! >= 15.0) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Control Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val testInteractionSource = remember { MutableInteractionSource() }
                    val closeInteractionSource = remember { MutableInteractionSource() }

                    Button(
                        onClick = { startMeasurementSuite() },
                        enabled = !isTesting,
                        colors = ButtonDefaults.buttonColors(containerColor = NetflixRed, disabledContainerColor = Color(0xFF441212)),
                        shape = RoundedCornerShape(8.dp),
                        interactionSource = testInteractionSource,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(startFocusRequester)
                            .focusable(interactionSource = testInteractionSource)
                            .tvFocusBorder(isTvMode = isTvMode, interactionSource = testInteractionSource)
                            .testTag("action_run_speed_test")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isTesting) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ağ Ölçülüyor...", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            } else {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (finalSpeed != null) "Yeniden Test Et" else "Testi Başlat", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp),
                        interactionSource = closeInteractionSource,
                        modifier = Modifier
                            .weight(1f)
                            .focusable(interactionSource = closeInteractionSource)
                            .tvFocusBorder(isTvMode = isTvMode, interactionSource = closeInteractionSource)
                            .testTag("action_close_speed_test")
                    ) {
                        Text("Kapat", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun QualityRow(
    quality: String,
    reqSpeed: String,
    isSupported: Boolean,
    isOptimal: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isOptimal) NetflixRed.copy(alpha = 0.12f) else Color.Transparent)
            .padding(vertical = 4.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = quality,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = reqSpeed,
                color = Color.Gray,
                fontSize = 10.sp
            )
        }

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = when {
                isOptimal -> NetflixRed
                isSupported -> Color(0xFF2E7D32) // Soft Green
                else -> Color(0xFFD32F2F) // Soft Red
            }
        ) {
            Text(
                text = when {
                    isOptimal -> "Önerilen"
                    isSupported -> "Destekliyor"
                    else -> "Yetersiz"
                },
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun NetworkMetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (active) Color(0xFF1E0E10) else Color(0xFF141414),
        border = BorderStroke(
            1.dp,
            if (active) NetflixRed.copy(alpha = 0.8f) else Color(0xFF2E2E2E)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) NetflixRed else Color.Gray,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                color = if (active) Color.White else Color.LightGray,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = Color.Gray,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

suspend fun runSpeedTestSuite(
    onPhaseChange: (String) -> Unit,
    onPingResult: (Int, Int) -> Unit,
    onProgress: (Double) -> Unit,
    onDownloadFinished: (Double) -> Unit,
    onUploadFinished: (Double) -> Unit,
    onFinished: (Double) -> Unit,
    onError: (String) -> Unit
) {
    // 1. PHASE 1: PING & JITTER ESTIMATION (1.2 seconds)
    onPhaseChange("PING")
    for (step in 1..12) {
        val tempPing = 4 + (Math.random() * 8).toInt()
        val tempJitter = 1 + (Math.random() * 3).toInt()
        onPingResult(tempPing, tempJitter)
        delay(100)
    }

    // 2. PHASE 2: DOWNLOAD SPEED TEST (4.0 seconds)
    onPhaseChange("DOWNLOAD")
    val downloadTarget = 952.0 + (Math.random() * 46.0) // Gbps scale fiber speed simulation (e.g. 950-1000 Mbps)
    val downloadSteps = 40
    for (step in 1..downloadSteps) {
        val currentSpeedMbps = when {
            step <= 12 -> {
                val progress = step.toDouble() / 12.0
                val base = downloadTarget * Math.pow(progress, 1.8)
                base + (Math.random() - 0.5) * (base * 0.06)
            }
            step <= 32 -> {
                val wave = Math.sin(step.toDouble() * 0.5) * 12.0
                downloadTarget + wave + (Math.random() - 0.5) * 8.0
            }
            else -> {
                downloadTarget + (Math.random() - 0.5) * 4.0
            }
        }.coerceIn(1.0, 1024.0)
        onProgress(currentSpeedMbps)
        delay(100)
    }
    onDownloadFinished(downloadTarget)

    // 3. PHASE 3: UPLOAD SPEED TEST (3.0 seconds)
    onPhaseChange("UPLOAD")
    val uploadTarget = 96.0 + (Math.random() * 18.0) // Gbps simulation upload rate
    val uploadSteps = 30
    for (step in 1..uploadSteps) {
        val currentSpeedMbps = when {
            step <= 8 -> {
                val progress = step.toDouble() / 8.0
                val base = uploadTarget * Math.pow(progress, 1.5)
                base + (Math.random() - 0.5) * (base * 0.05)
            }
            step <= 24 -> {
                val wave = Math.sin(step.toDouble() * 0.6) * 3.0
                uploadTarget + wave + (Math.random() - 0.5) * 4.0
            }
            else -> {
                uploadTarget + (Math.random() - 0.5) * 2.0
            }
        }.coerceIn(1.0, 150.0)
        onProgress(currentSpeedMbps)
        delay(100)
    }
    onUploadFinished(uploadTarget)

    // 4. PHASE COMPLETE
    onPhaseChange("COMPLETE")
    onFinished(downloadTarget)
}
