package com.example.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Playlist
import com.example.ui.theme.NetflixDarkGrey
import com.example.ui.theme.NetflixLightGrey
import com.example.ui.theme.NetflixRed
import com.example.ui.viewmodel.RefreshState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    playlists: List<Playlist>,
    refreshState: RefreshState,
    isTvMode: Boolean,
    onPlaylistSelected: (Playlist) -> Unit,
    onAddPlaylist: (String, String, String, String?, String?) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onClearState: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("M3U") } // "M3U" or "XTREAM"
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useWideLayout = isTvMode || isLandscape

    // Monitor refreshState for errors
    LaunchedEffect(refreshState) {
        if (refreshState is RefreshState.Error) {
            errorMessage = refreshState.message
            showErrorDialog = true
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(NetflixRed, Color(0xFFFF3B44))
                                    ), 
                                    RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "CSD",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "CSD PLAYER",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            letterSpacing = (-1).sp
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF0D0D10)
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF151518),
                            Color(0xFF080809)
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            // Elegant top-right subtle background light
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(350.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                NetflixRed.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
            )

            if (useWideLayout) {
                // TV MODE / LANDSCAPE TAB / WIDE SCREEN: Elegant Row side-by-side layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Left Column: Saved Playlists
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = "Kayıtlı Çalma Listeleriniz",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        if (playlists.isEmpty()) {
                            SavedPlaylistsPlaceholder()
                        } else {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                playlists.forEach { playlist ->
                                    PlaylistCard(
                                        playlist = playlist,
                                        isTvMode = isTvMode,
                                        onPlaylistSelected = onPlaylistSelected,
                                        onDeletePlaylist = onDeletePlaylist
                                    )
                                }
                            }
                        }
                    }

                    // Right Column: Add New Playlist Form
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Yeni Liste Ekle",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        AddPlaylistForm(
                            name = name,
                            onNameChange = { name = it },
                            selectedType = selectedType,
                            onTypeChange = { selectedType = it },
                            url = url,
                            onUrlChange = { url = it },
                            username = username,
                            onUsernameChange = { username = it },
                            password = password,
                            onPasswordChange = { password = it },
                            isTvMode = isTvMode,
                            refreshState = refreshState,
                            onAddPlaylist = onAddPlaylist
                        )
                    }
                }
            } else {
                // MOBILE PORTRAIT / COMPACT LAYOUT: Elegant Vertical Stack with responsive scrolling
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    if (playlists.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Kayıtlı Çalma Listeleriniz",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            playlists.forEach { playlist ->
                                PlaylistCard(
                                    playlist = playlist,
                                    isTvMode = isTvMode,
                                    onPlaylistSelected = onPlaylistSelected,
                                    onDeletePlaylist = onDeletePlaylist
                                )
                            }
                        }

                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.08f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Yeni Liste Ekle",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        AddPlaylistForm(
                            name = name,
                            onNameChange = { name = it },
                            selectedType = selectedType,
                            onTypeChange = { selectedType = it },
                            url = url,
                            onUrlChange = { url = it },
                            username = username,
                            onUsernameChange = { username = it },
                            password = password,
                            onPasswordChange = { password = it },
                            isTvMode = isTvMode,
                            refreshState = refreshState,
                            onAddPlaylist = onAddPlaylist
                        )
                    }
                }
            }

            // Loading state dialog overlay
            if (refreshState is RefreshState.Loading) {
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CircularProgressIndicator(color = NetflixRed)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = refreshState.message,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Kanallar yükleniyor, liste büyüklüğüne göre bir iki dakika sürebilir...",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // Error alert dialog
            if (showErrorDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showErrorDialog = false
                        onClearState()
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showErrorDialog = false
                                onClearState()
                            }
                        ) {
                            Text("Tamam", color = NetflixRed, fontWeight = FontWeight.Bold)
                        }
                    },
                    title = { Text("Bağlantı Hatası", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = { Text(errorMessage, color = Color.LightGray) },
                    containerColor = NetflixDarkGrey,
                    icon = { Icon(Icons.Default.Error, null, tint = NetflixRed, modifier = Modifier.size(36.dp)) }
                )
            }
        }
    }
}

@Composable
fun SavedPlaylistsPlaceholder() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131316)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(26.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = NetflixRed.copy(alpha = 0.8f),
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Henüz Oynatma Listesi Yok",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "M3U Linki veya Xtream bilgilerinizi sağ taraftaki formdan girerek canlı / video kütüphanenizi hemen yükleyebilirsiniz.",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.85f)
                )
            }
        }
    }
}

@Composable
fun PlaylistCard(
    playlist: Playlist,
    isTvMode: Boolean,
    onPlaylistSelected: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1D1D21)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .tvClickable(isTvMode = isTvMode, shape = RoundedCornerShape(12.dp)) {
                onPlaylistSelected(playlist)
            }
            .testTag("playlist_item_${playlist.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(NetflixRed.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playlist.type == "XTREAM") Icons.Default.AccountCircle else Icons.Default.Link,
                        contentDescription = null,
                        tint = NetflixRed,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = playlist.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (playlist.type == "XTREAM") "Xtream Codes API • Premium Bulut" else "M3U Linki • Ağ Kanal Listesi",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
            IconButton(
                onClick = { onDeletePlaylist(playlist) },
                modifier = Modifier.testTag("delete_playlist_${playlist.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Sil",
                    tint = Color.Gray.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun AddPlaylistForm(
    name: String,
    onNameChange: (String) -> Unit,
    selectedType: String,
    onTypeChange: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isTvMode: Boolean,
    refreshState: RefreshState,
    onAddPlaylist: (String, String, String, String?, String?) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1D21)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Name Field
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Oynatma Listesi Adı") },
                leadingIcon = { Icon(Icons.Default.Label, null, tint = Color.Gray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NetflixRed,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("playlist_name_input")
            )

            // Playlist Type Selector Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val m3uInteractionSource = remember { MutableInteractionSource() }
                Button(
                    onClick = { onTypeChange("M3U") },
                    interactionSource = m3uInteractionSource,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedType == "M3U") NetflixRed else NetflixLightGrey
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp) // Accessibility standard >= 48dp target via vertical margin or paddings
                        .tvFocusBorder(
                            isTvMode = isTvMode,
                            interactionSource = m3uInteractionSource,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    Text("M3U Link", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                val xtreamInteractionSource = remember { MutableInteractionSource() }
                Button(
                    onClick = { onTypeChange("XTREAM") },
                    interactionSource = xtreamInteractionSource,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedType == "XTREAM") NetflixRed else NetflixLightGrey
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .tvFocusBorder(
                            isTvMode = isTvMode,
                            interactionSource = xtreamInteractionSource,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    Text("Xtream API", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            // Server URL Field
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = { Text(if (selectedType == "XTREAM") "Sunucu URL (örn: http://sunucu.com:8080)" else "M3U Playlist URL") },
                leadingIcon = { Icon(Icons.Default.Dns, null, tint = Color.Gray) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NetflixRed,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("playlist_url_input")
            )

            // Xtream-specific Fields
            AnimatedVisibility(visible = selectedType == "XTREAM") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        label = { Text("Kullanıcı Adı") },
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NetflixRed,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("playlist_user_input")
                    )

                    var passwordVisible by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text("Şifre") },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = Color.Gray) },
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            val description = if (passwordVisible) "Şifreyi gizle" else "Şifreyi göster"
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = description, tint = Color.Gray)
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NetflixRed,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("playlist_pass_input")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Save Submissions Button
            val saveInteractionSource = remember { MutableInteractionSource() }
            val isFormValid = name.isNotBlank() && url.isNotBlank() && 
                    (selectedType != "XTREAM" || (username.isNotBlank() && password.isNotBlank()))

            Button(
                onClick = {
                    if (isFormValid) {
                        onAddPlaylist(name, selectedType, url, username.takeIf { selectedType == "XTREAM" }, password.takeIf { selectedType == "XTREAM" })
                    }
                },
                interactionSource = saveInteractionSource,
                enabled = isFormValid && refreshState !is RefreshState.Loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NetflixRed,
                    disabledContainerColor = Color(0xFF28282B),
                    contentColor = Color.White,
                    disabledContentColor = Color.Gray.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .tvFocusBorder(
                        isTvMode = isTvMode,
                        interactionSource = saveInteractionSource,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .testTag("save_playlist_button")
            ) {
                Text("KAYDET VE SENKRONİZE ET", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            }
        }
    }
}
