package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.ui.theme.NetflixRed

@Composable
fun VoiceSearchDialog(
    isTvMode: Boolean,
    onDismissRequest: () -> Unit,
    onResult: (String) -> Unit
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Mikrofon izni bekleniyor...") }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    // Permission request launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            statusText = "Mikrofon aktif. Hazırlanıyor..."
        } else {
            statusText = "Mikrofon izni reddedildi. Sesli arama devre dışı."
            Toast.makeText(context, "Sesli arama için mikrofon izni gereklidir!", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Speech Recognizer activation wrapper
    var triggerCount by remember { mutableStateOf(1) }
    DisposableEffect(hasPermission, triggerCount) {
        var speechRecognizer: SpeechRecognizer? = null
        if (hasPermission && SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR")
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }

                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            isListening = true
                            statusText = "Sizi dinliyorum, konuşun..."
                        }

                        override fun onBeginningOfSpeech() {
                            statusText = "Ses algılanıyor..."
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            // Can be used for extra fluid dynamic elements
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            statusText = "Ses çözümleniyor..."
                        }

                        override fun onError(error: Int) {
                            val message = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Ses kayıt hatası"
                                SpeechRecognizer.ERROR_CLIENT -> "Sistem servis bağlantı hatası"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon izni yetersiz"
                                SpeechRecognizer.ERROR_NETWORK -> "İnternet bağlantı hatası"
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Bağlantı zaman aşımı"
                                SpeechRecognizer.ERROR_NO_MATCH -> "Ses anlaşılamadı, lütfen tekrar deneyin"
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Ses tanıma sistemi meşgul"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Herhangi bir ses duyulmadı"
                                else -> "Cihaz sesi algılayamadı"
                            }
                            statusText = "$message"
                            isListening = false
                        }

                        override fun onResults(results: Bundle?) {
                            isListening = false
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val text = matches[0]
                                recognizedText = text
                                statusText = "Algılandı: $text"
                                onResult(text)
                                onDismissRequest()
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                recognizedText = matches[0]
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })

                    startListening(intent)
                }
            } catch (e: Exception) {
                statusText = "Ses tanımlama başlatılamadı: ${e.localizedMessage}"
            }
        } else if (hasPermission) {
            statusText = "Cihazınızda Google Ses Servisleri bulunamadı. Lütfen aşağıdaki hazır arama önerilerini kullanın."
        }

        onDispose {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                // Ignore destruction issues safely
            }
        }
    }

    // High fidelity Pulse Animation
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RadarScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RadarAlpha"
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0D0D0F),
            border = BorderStroke(1.dp, NetflixRed.copy(alpha = 0.5f)),
            modifier = Modifier
                .width(if (isTvMode) 580.dp else 360.dp)
                .wrapContentHeight()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.KeyboardVoice,
                            contentDescription = null,
                            tint = NetflixRed,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sesli Arama (Voice Search)",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val closeInteraction = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .size(36.dp)
                            .focusable(interactionSource = closeInteraction)
                            .tvFocusBorder(isTvMode, closeInteraction, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, "Kapat", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Pulsing Mic Radar Circle
                Box(
                    modifier = Modifier.size(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isListening) {
                        // Outer pulse ring
                        Box(
                            modifier = Modifier
                                .size(95.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(NetflixRed.copy(alpha = pulseAlpha))
                        )
                    }

                    // Main Recording Button
                    val triggerInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(NetflixRed, Color(0xFFB1070F))
                                )
                            )
                            .focusable(interactionSource = triggerInteraction)
                            .tvFocusBorder(isTvMode, triggerInteraction, CircleShape)
                            .tvClickable(isTvMode, CircleShape) {
                                triggerCount++
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Dinle",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Live feedback transcript
                if (recognizedText.isNotEmpty()) {
                    Text(
                        text = recognizedText,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Status message
                Text(
                    text = statusText,
                    color = if (isListening) Color(0xFF00E676) else Color.Gray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                Divider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Custom Suggestions Header (TV Ergonomics: select via remote in 1 click!)
                Text(
                    text = "Hazır IPTV Arama Önerileri",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    textAlign = TextAlign.Start
                )

                val suggestions = listOf("SPOR", "SİNEMA", "DİZİ", "HABER", "BELGESEL", "TRT 1", "NOW", "ATV")

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                ) {
                    items(suggestions) { keyword ->
                        val itemInteraction = remember { MutableInteractionSource() }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF141416),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .focusable(interactionSource = itemInteraction)
                                .tvFocusBorder(isTvMode, itemInteraction, RoundedCornerShape(10.dp))
                                .tvClickable(isTvMode, RoundedCornerShape(10.dp)) {
                                    onResult(keyword)
                                    onDismissRequest()
                                }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = keyword,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Action buttons (Retry, Close)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val retryInteraction = remember { MutableInteractionSource() }
                    OutlinedButton(
                        onClick = { triggerCount++ },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.DarkGray),
                        modifier = Modifier
                            .weight(1f)
                            .focusable(interactionSource = retryInteraction)
                            .tvFocusBorder(isTvMode, retryInteraction, RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.Refresh, "Tekrar Dene", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Yeniden Başlat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    val cancelInteraction = remember { MutableInteractionSource() }
                    Button(
                        onClick = onDismissRequest,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                        modifier = Modifier
                            .weight(1f)
                            .focusable(interactionSource = cancelInteraction)
                            .tvFocusBorder(isTvMode, cancelInteraction, RoundedCornerShape(12.dp))
                    ) {
                        Text("Kapat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
