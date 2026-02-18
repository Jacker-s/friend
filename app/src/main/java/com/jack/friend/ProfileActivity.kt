package com.jack.friend

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
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
                
                val myName by viewModel.myName.collectAsStateWithLifecycle("")
                val myUsername by viewModel.myUsername.collectAsStateWithLifecycle("")
                val myPhotoUrl by viewModel.myPhotoUrl.collectAsStateWithLifecycle(null)
                val myStatus by viewModel.myStatus.collectAsStateWithLifecycle("")
                val myPresenceStatus by viewModel.myPresenceStatus.collectAsStateWithLifecycle("Online")

                var nameInput by remember { mutableStateOf("") }
                var statusInput by remember { mutableStateOf("") }
                var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
                var selectedPresence by remember { mutableStateOf(myPresenceStatus) }
                var hideFromSearch by remember { mutableStateOf(false) }
                var isSaving by remember { mutableStateOf(false) }
                var showPresenceMenu by remember { mutableStateOf(false) }
                
                var dataLoaded by remember { mutableStateOf(false) }

                // Initialize inputs once when data is available
                LaunchedEffect(myName, myStatus) {
                    if (!dataLoaded && (myName.isNotEmpty() || myStatus.isNotEmpty())) {
                        nameInput = myName
                        statusInput = myStatus
                        dataLoaded = true
                    }
                }

                LaunchedEffect(myPresenceStatus) {
                    selectedPresence = myPresenceStatus
                }

                // Carregar o estado inicial de isHiddenFromSearch do Firebase
                LaunchedEffect(myUsername) {
                    if (myUsername.isNotEmpty()) {
                        com.google.firebase.database.FirebaseDatabase.getInstance().reference
                            .child("users").child(myUsername)
                            .child("isHiddenFromSearch").get().addOnSuccessListener {
                                hideFromSearch = it.getValue(Boolean::class.java) ?: false
                            }
                    }
                }

                val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                    selectedImageUri = uri
                }

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("Editar Perfil", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
                                }
                            },
                            actions = {
                                if (isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 16.dp), strokeWidth = 2.dp)
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
                                                Toast.makeText(context, "Perfil atualizado", Toast.LENGTH_SHORT).show()
                                                finish()
                                            } else {
                                                Toast.makeText(context, "Erro ao atualizar", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }) {
                                        Text("Salvar", color = WarmPrimary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalChatColors.current.topBar)
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(Modifier.height(24.dp))
                        
                        // Profile Photo
                        Box(modifier = Modifier.size(120.dp).align(Alignment.CenterHorizontally)) {
                            AsyncImage(
                                model = selectedImageUri ?: myPhotoUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape).background(LocalChatColors.current.separator),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { photoLauncher.launch("image/*") },
                                modifier = Modifier.align(Alignment.BottomEnd).size(36.dp).background(WarmPrimary, CircleShape).padding(4.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                        
                        Spacer(Modifier.height(32.dp))

                        Text("INFORMAÇÕES PÚBLICAS", modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall, color = WarmPrimary, fontWeight = FontWeight.Bold)
                        MetaSettingsSection {
                            MetaSettingsTextField(label = "Nome", value = nameInput, onValueChange = { nameInput = it })
                            MetaSettingsDivider()
                            MetaSettingsTextField(label = "Recado", value = statusInput, onValueChange = { statusInput = it })
                        }

                        Spacer(Modifier.height(16.dp))

                        Text("DISPONIBILIDADE", modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall, color = WarmPrimary, fontWeight = FontWeight.Bold)
                        MetaSettingsSection {
                            Box(modifier = Modifier.fillMaxWidth().clickable { showPresenceMenu = true }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val color = when(selectedPresence) {
                                        "Online" -> MessengerOnline
                                        "Ocupado" -> MessengerBusy
                                        "Ausente" -> MessengerAway
                                        else -> Color.Gray
                                    }
                                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
                                    Spacer(Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Estado de Presença", style = MaterialTheme.typography.bodyLarge)
                                        Text(selectedPresence, style = MaterialTheme.typography.bodyMedium, color = WarmTextSecondary)
                                    }
                                    Icon(Icons.Default.ArrowDropDown, null, tint = Color.Gray)
                                }
                                DropdownMenu(expanded = showPresenceMenu, onDismissRequest = { showPresenceMenu = false }) {
                                    listOf("Online", "Ocupado", "Ausente", "Invisível").forEach { status ->
                                        DropdownMenuItem(
                                            text = { Text(status) },
                                            onClick = { selectedPresence = status; showPresenceMenu = false },
                                            leadingIcon = {
                                                val color = when(status) {
                                                    "Online" -> MessengerOnline
                                                    "Ocupado" -> MessengerBusy
                                                    "Ausente" -> MessengerAway
                                                    else -> Color.Gray
                                                }
                                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text("PRIVACIDADE", modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall, color = WarmPrimary, fontWeight = FontWeight.Bold)
                        MetaSettingsSection {
                            MetaSettingsSwitchItem(
                                icon = Icons.Default.PersonSearch,
                                iconColor = Color.Gray,
                                title = "Esconder da busca",
                                checked = hideFromSearch,
                                onCheckedChange = { hideFromSearch = it }
                            )
                        }
                        
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "O seu nome e foto de perfil são visíveis para seus contatos e nas conversas que você participa. Ativar 'Esconder da busca' impedirá que outros usuários encontrem seu perfil ao pesquisar pelo seu @usuario ou nome.",
                            modifier = Modifier.padding(horizontal = 28.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = WarmTextSecondary
                        )
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}
