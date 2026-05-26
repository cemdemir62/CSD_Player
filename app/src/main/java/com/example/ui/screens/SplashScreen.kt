package com.example.ui.screens

import android.content.res.Configuration
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    
    // Choose the video based on current orientation automatically
    val videoResId = R.raw.splash_landscape

    var isFinishedTriggered by remember { mutableStateOf(false) }

    val exoPlayer = remember(videoResId) {
        ExoPlayer.Builder(context).build().apply {
            val videoUri = Uri.parse("android.resource://${context.packageName}/$videoResId")
            setMediaItem(MediaItem.fromUri(videoUri))
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (!isFinishedTriggered) {
                        isFinishedTriggered = true
                        onSplashFinished()
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (!isFinishedTriggered) {
                    isFinishedTriggered = true
                    onSplashFinished()
                }
            }
        }
        
        exoPlayer.addListener(listener)
        
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable {
                // Tap anywhere to skip immediately for friendly user interaction
                if (!isFinishedTriggered) {
                    isFinishedTriggered = true
                    onSplashFinished()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Full screen video splash
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Subtle skip prompt in bottom corner or top corner for premium accessibility
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 24.dp)
                .background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.CircleShape)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable {
                    if (!isFinishedTriggered) {
                        isFinishedTriggered = true
                        onSplashFinished()
                    }
                }
        ) {
            Text(
                text = "Geç",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

