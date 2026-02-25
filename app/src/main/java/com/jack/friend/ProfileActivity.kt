package com.jack.friend

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.jack.friend.ui.chat.MediaViewerItem
import com.jack.friend.ui.chat.MediaViewerScreen
import com.jack.friend.ui.profile.PrivacyPolicyScreen
import com.jack.friend.ui.theme.*

class ProfileActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val uiPrefs = remember { context.getSharedPreferences("ui_prefs", MODE_PRIVATE) }
            val isDarkMode = uiPrefs.getBoolean("dark_mode", false)
            val followSystem = uiPrefs.getBoolean("follow_system", true)

            FriendTheme(isDarkModeOverride = if (followSystem) null else isDarkMode) {
                val viewModel: ChatViewModel = viewModel()
                val colors = LocalChatColors.current
                
                val myName by viewModel.myName.collectAsStateWithLifecycle("")
                val myUsername by viewModel.myUsername.collectAsStateWithLifecycle("")
                val myPhotoUrl by viewModel.myPhotoUrl.collectAsStateWithLifecycle(null)
                val myStatus by viewModel.myStatus.collectAsStateWithLifecycle("")
                val myPresenceStatus by viewModel.myPresenceStatus.collectAsStateWithLifecycle("Online")
                val isHiddenFromSearch by viewModel.isHiddenFromSearch.collectAsStateWithLifecycle(false)

                var nameInput by remember { mutableStateOf("") }
                var statusInput by remember { mutableStateOf("") }
                var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
                var selectedPresence by remember { mutableStateOf(myPresenceStatus) }
                var hideFromSearch by remember { mutableStateOf(isHiddenFromSearch) }
                var isSaving by remember { mutableStateOf(false) }
                var showPresenceMenu by remember { mutableStateOf(false) }
                var showPrivacyPolicy by remember { mutableStateOf(false) }
                var fullScreenPhotoUrl by remember { mutableStateOf<String?>(null) }
                
                var dataLoaded by remember { mutableStateOf(false) }

                LaunchedEffect(myName, myStatus, isHiddenFromSearch) {
                    if (!dataLoaded && (myName.isNotEmpty() || myStatus.isNotEmpty())) {
                        nameInput = myName
                        statusInput = myStatus
                        hideFromSearch = isHiddenFromSearch
                        dataLoaded = true
                    }
                }

                LaunchedEffect(myPresenceStatus) { selectedPresence = myPresenceStatus }

                val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                    selectedImageUri = uri
                }

                BackHandler(enabled = showPrivacyPolicy || fullScreenPhotoUrl != null) {
                    if (fullScreenPhotoUrl != null) fullScreenPhotoUrl = null
                    else if (showPrivacyPolicy) showPrivacyPolicy = false
                }

                if (showPrivacyPolicy) {
                    PrivacyPolicyScreen(onBack = { showPrivacyPolicy = false })
                } else {
                    Scaffold(
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = { Text("Meu Perfil", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                                navigationIcon = {
                                    IconButton(onClick = { finish() }) {
                                        Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, null, tint = MessengerBlue, modifier = Modifier.size(22.dp))
                                    }
                                },
                                actions = {
                                    if (isSaving) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 16.dp), strokeWidth = 2.dp, color = MessengerBlue)
                                    } else {
                                        TextButton(onClick = {
                                            isSaving = true
                                            viewModel.updateProfile(
                                                name = nameInput,
                                                imageUri = selectedImageUri,
                                                status = statusInput,
                                                presenceStatus = selectedPresence,
                                                privacySettings = mapOf("isHiddenFromSearch" to hideFromSearch)
                                            ) { success ->
                                                isSaving = false
                                                if (success) {
                                                    Toast.makeText(context, "Perfil atualizado!", Toast.LENGTH_SHORT).show()
                                                    finish()
                                                } else {
                                                    Toast.makeText(context, "Erro ao salvar", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }) {
                                            Text("Salvar", color = MessengerBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                            )
                        },
                        containerColor = colors.background
                    ) { innerPadding ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = innerPadding.calculateBottomPadding())
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Immersive Header with adjusted height and arrangement to avoid overlap
                                Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                                    AsyncImage(
                                        model = selectedImageUri ?: myPhotoUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().blur(50.dp).clickable { fullScreenPhotoUrl = (selectedImageUri ?: myPhotoUrl)?.toString() },
                                        contentScale = ContentScale.Crop,
                                        alpha = 0.3f
                                    )
                                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, colors.background))))
                                    
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Bottom // Push photo to bottom
                                    ) {
                                        Box(contentAlignment = Alignment.BottomEnd, modifier = Modifier.padding(bottom = 32.dp)) {
                                            Surface(
                                                shape = CircleShape,
                                                border = BorderStroke(3.dp, Color.White),
                                                shadowElevation = 12.dp,
                                                modifier = Modifier.size(140.dp).clickable { photoLauncher.launch("image/*") }
                                            ) {
                                                AsyncImage(
                                                    model = selectedImageUri ?: myPhotoUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                            Surface(
                                                modifier = Modifier.size(38.dp).offset(x = (-4).dp, y = (-4).dp).shadow(4.dp, CircleShape),
                                                shape = CircleShape,
                                                color = MessengerBlue,
                                                onClick = { photoLauncher.launch("image/*") }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Rounded.CameraAlt, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = "@${myUsername.lowercase()}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.textSecondary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )

                                // Settings Groups
                                MetaSettingsGroup(title = "Minha Conta", colors = colors) {
                                    ProfileEditRow(label = "Nome", value = nameInput, onValueChange = { nameInput = it }, icon = Icons.Rounded.Person, colors = colors)
                                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), thickness = 0.5.dp, color = colors.separator.copy(0.4f))
                                    ProfileEditRow(label = "Recado", value = statusInput, onValueChange = { statusInput = it }, icon = Icons.Rounded.ChatBubbleOutline, colors = colors)
                                }

                                Spacer(Modifier.height(24.dp))

                                MetaSettingsGroup(title = "Minha Presença", colors = colors) {
                                    PresenceSelectorRow(selectedPresence, onClick = { showPresenceMenu = true }, colors = colors)
                                    
                                    DropdownMenu(
                                        expanded = showPresenceMenu,
                                        onDismissRequest = { showPresenceMenu = false },
                                        modifier = Modifier.background(colors.secondaryBackground)
                                    ) {
                                        listOf("Online", "Ocupado", "Ausente", "Invisível").forEach { status ->
                                            DropdownMenuItem(
                                                text = { Text(status, color = colors.textPrimary) },
                                                onClick = { selectedPresence = status; showPresenceMenu = false }
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(24.dp))

                                MetaSettingsGroup(title = "Privacidade e Segurança", colors = colors) {
                                    MetaSettingsSwitchItem(
                                        icon = Icons.Rounded.VisibilityOff,
                                        iconColor = Color.Gray,
                                        title = "Modo Fantasma",
                                        checked = hideFromSearch,
                                        onCheckedChange = { hideFromSearch = it }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), thickness = 0.5.dp, color = colors.separator.copy(0.4f))
                                    ActionItemRow(
                                        label = "Política de Privacidade",
                                        icon = Icons.Rounded.Description,
                                        iconColor = Color.Gray,
                                        onClick = { showPrivacyPolicy = true },
                                        colors = colors
                                    )
                                }
                                
                                Text(
                                    "O Modo Fantasma impede que outras pessoas encontrem seu perfil pela busca global.",
                                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(Modifier.height(32.dp))
                                
                                // Logout
                                TextButton(
                                    onClick = { 
                                        viewModel.logout()
                                        val intent = Intent(context, MainActivity::class.java).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                        }
                                        context.startActivity(intent)
                                        finish()
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                ) {
                                    Text("Sair da Conta", color = iOSRed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                                
                                Spacer(Modifier.height(60.dp))
                            }

                            // Photo Viewer
                            if (fullScreenPhotoUrl != null) {
                                MediaViewerScreen(
                                    mediaItem = MediaViewerItem.Image(fullScreenPhotoUrl!!),
                                    onDismiss = { fullScreenPhotoUrl = null }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaSettingsGroup(title: String, colors: ChatColors, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            modifier = Modifier.padding(start = 28.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            fontWeight = FontWeight.Bold
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = colors.secondaryBackground,
            tonalElevation = 1.dp
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun ProfileEditRow(label: String, value: String, onValueChange: (String) -> Unit, icon: ImageVector, colors: ChatColors) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MessengerBlue.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MessengerBlue, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.fillMaxWidth(),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MessengerBlue)
            )
        }
    }
}

@Composable
private fun PresenceSelectorRow(selectedPresence: String, onClick: () -> Unit, colors: ChatColors) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = when(selectedPresence) {
            "Online" -> iOSGreen
            "Ocupado" -> iOSRed
            "Ausente" -> iOSOrange
            else -> Color.Gray
        }
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Status de Presença", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            Text(selectedPresence, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = colors.textSecondary)
    }
}

@Composable
private fun ActionItemRow(label: String, icon: ImageVector, iconColor: Color, onClick: () -> Unit, colors: ChatColors) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(iconColor.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), color = colors.textPrimary)
        Icon(Icons.Rounded.ChevronRight, null, tint = colors.textSecondary)
    }
}
