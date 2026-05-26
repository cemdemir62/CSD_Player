package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserProfile
import com.example.ui.theme.NetflixRed

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileSelectionScreen(
    profiles: List<UserProfile>,
    onProfileSelected: (UserProfile) -> Unit,
    onAddProfile: (String, String, String, Boolean) -> Unit,
    onDeleteProfile: (UserProfile) -> Unit,
    isTvMode: Boolean
) {
    var isManageMode by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Dialog form states
    var newProfileName by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("🍿") }
    var selectedColorCode by remember { mutableStateOf("#E50914") } // Netflix Red
    var isKidsMode by remember { mutableStateOf(false) }

    val presetColors = listOf(
        "#E50914" to "Kırmızı",
        "#8E24AA" to "Mor",
        "#00897B" to "Teal",
        "#FFB300" to "Altın",
        "#1E88E5" to "Mavi"
    )

    val presetEmojis = listOf("🍿", "👑", "🎮", "📺", "😊", "⭐", "🦖", "🦄")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF141416),
                        Color(0xFF080809)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Ambient background subtle glow
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(450.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            NetflixRed.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(24.dp)
        ) {
            // Header Title
            Text(
                text = if (isManageMode) "Profil Yönetimi" else "Kim İzliyor?",
                color = Color.White,
                fontSize = if (isTvMode) 32.sp else 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Text(
                text = if (isManageMode) "Düzenlemek veya silmek istediğiniz profili seçin." else "Kişiselleştirilmiş yayınlar ve kaldığım yerden devam et özelliği için profilini seç.",
                color = Color.Gray,
                fontSize = if (isTvMode) 13.sp else 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Grid of Profiles
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .padding(bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(if (isTvMode) 24.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                profiles.forEach { profile ->
                    ProfileCard(
                        profile = profile,
                        isManageMode = isManageMode,
                        onSelected = {
                            if (isManageMode) {
                                onDeleteProfile(profile)
                                isManageMode = false
                            } else {
                                onProfileSelected(profile)
                            }
                        },
                        isTvMode = isTvMode
                    )
                }

                // Add Profile Tile (If space remains, Netflix maximum is 5)
                if (profiles.size < 5) {
                    AddProfileTile(
                        onClick = { showCreateDialog = true },
                        isTvMode = isTvMode
                    )
                }
            }

            // Controls Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Manage/Exit edit mode button
                Surface(
                    onClick = { isManageMode = !isManageMode },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isManageMode) NetflixRed else Color(0xFF202023),
                    modifier = Modifier
                        .height(48.dp)
                        .tvClickable(isTvMode = isTvMode) { isManageMode = !isManageMode }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isManageMode) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isManageMode) "Tamam" else "Profilleri Yönet",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Create Profile Modal Dialog
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = {
                    Text(
                        text = "Yeni Profil Oluştur",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Profile Name input
                        OutlinedTextField(
                            value = newProfileName,
                            onValueChange = { newProfileName = it },
                            label = { Text("Profil Adı") },
                            placeholder = { Text("Örn: Kerem, Çocuklar vb.") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NetflixRed,
                                focusedLabelColor = NetflixRed,
                                cursorColor = NetflixRed,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF161619),
                                unfocusedContainerColor = Color(0xFF161619)
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Kids mode toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isKidsMode = !isKidsMode }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = isKidsMode,
                                onCheckedChange = { isKidsMode = it },
                                colors = CheckboxDefaults.colors(checkedColor = NetflixRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Çocuk Profili",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Sadece çocuklara özel arayüz ve kategoriler aktif olur.",
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Color Choices
                        Text(
                            text = "Profil Rengi:",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            presetColors.forEach { (hex, name) ->
                                val color = Color(android.graphics.Color.parseColor(hex))
                                val isSelected = selectedColorCode == hex
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isSelected) 3.dp else 0.dp,
                                            color = Color.White,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColorCode = hex },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Emoji Choices
                        Text(
                            text = "Profil Simgesi (Emoji):",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            presetEmojis.forEach { emoji ->
                                val isSelected = selectedEmoji == emoji
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFF2F2F34) else Color(0xFF1E1E22))
                                        .border(
                                            width = if (isSelected) 2.dp else 0.dp,
                                            color = NetflixRed,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedEmoji = emoji },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = emoji, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newProfileName.isNotBlank()) {
                                onAddProfile(
                                    newProfileName.trim(),
                                    selectedColorCode,
                                    selectedEmoji,
                                    isKidsMode
                                )
                                // Clear fields
                                newProfileName = ""
                                selectedEmoji = "🍿"
                                selectedColorCode = "#E50914"
                                isKidsMode = false
                                showCreateDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                        shape = RoundedCornerShape(8.dp),
                        enabled = newProfileName.isNotBlank()
                    ) {
                        Text("Oluştur")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showCreateDialog = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.LightGray)
                    ) {
                        Text("Vazgeç")
                    }
                },
                containerColor = Color(0xFF1B1B1F),
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
fun ProfileCard(
    profile: UserProfile,
    isManageMode: Boolean,
    onSelected: () -> Unit,
    isTvMode: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "profile_scale"
    )

    val parsedColor = remember(profile.avatarColor) {
        try {
            Color(android.graphics.Color.parseColor(profile.avatarColor))
        } catch (e: Exception) {
            NetflixRed
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(if (isTvMode) 120.dp else 95.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelected
            )
            .focusable()
            .testTag("profile_card_${profile.id}")
    ) {
        // Avatar Square
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(parsedColor, parsedColor.copy(alpha = 0.5f))
                    )
                )
                .border(
                    width = if (isFocused) 4.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(18.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Smile/emoji representation
            Text(
                text = profile.avatarEmoji,
                fontSize = if (isTvMode) 44.sp else 36.sp
            )

            // Direct Manage overlay trash icon
            if (isManageMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Sil",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Profile Name (with optional small kids batch)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = profile.name,
                color = if (isFocused) Color.White else Color.LightGray,
                fontSize = if (isTvMode) 13.sp else 11.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (profile.isKids) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFF00897B), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "Çocuk",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun AddProfileTile(
    onClick: () -> Unit,
    isTvMode: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "add_profile_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(if (isTvMode) 120.dp else 95.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable()
            .testTag("add_profile_tile")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1C1C22))
                .border(
                    width = if (isFocused) 3.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(18.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Ekle",
                tint = Color.Gray,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Ekle",
            color = Color.Gray,
            fontSize = if (isTvMode) 13.sp else 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
