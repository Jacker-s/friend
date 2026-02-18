package com.jack.friend

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.jack.friend.ui.theme.*

class SettingsActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val uiPrefs = remember { context.getSharedPreferences("ui_prefs", MODE_PRIVATE) }
            var isDarkMode by remember { mutableStateOf(uiPrefs.getBoolean("dark_mode", false)) }
            var followSystem by remember { mutableStateOf(uiPrefs.getBoolean("follow_system", true)) }

            FriendTheme(isDarkModeOverride = if (followSystem) null else isDarkMode) {
                val viewModel: ChatViewModel = viewModel()
                
                val myName by viewModel.myName.collectAsStateWithLifecycle("")
                val myPhotoUrl by viewModel.myPhotoUrl.collectAsStateWithLifecycle(null)
                val myStatus by viewModel.myStatus.collectAsStateWithLifecycle("")
                val blockedProfiles by viewModel.blockedProfiles.collectAsStateWithLifecycle(emptyList())

                val securityPrefs = remember { context.getSharedPreferences("security_prefs", MODE_PRIVATE) }

                var isPinEnabled by remember { mutableStateOf(securityPrefs.getBoolean("pin_enabled", false)) }
                var isBiometricEnabled by remember { mutableStateOf(securityPrefs.getBoolean("biometric_enabled", false)) }

                var showPinDialog by remember { mutableStateOf(false) }
                var pinInput by remember { mutableStateOf("") }
                var showDeleteAccountDialog by remember { mutableStateOf(false) }
                var showBlockedDialog by remember { mutableStateOf(false) }

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("Configurações", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
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
                        // User Profile Summary (Clickable to ProfileActivity)
                        Surface(
                            onClick = { context.startActivity(Intent(context, ProfileActivity::class.java)) },
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = myPhotoUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(60.dp).clip(CircleShape).background(LocalChatColors.current.separator),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = myName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text(text = myStatus, style = MaterialTheme.typography.bodyMedium, color = WarmTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
                            }
                        }

                        Text("PRIVACIDADE", modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall, color = WarmPrimary, fontWeight = FontWeight.Bold)
                        MetaSettingsSection {
                            MetaSettingsItem(title = "Usuários Bloqueados", icon = Icons.Default.Block, iconColor = MessengerBusy, onClick = { showBlockedDialog = true })
                        }

                        Text("APARÊNCIA", modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall, color = WarmPrimary, fontWeight = FontWeight.Bold)
                        MetaSettingsSection {
                            MetaSettingsSwitchItem(icon = Icons.Default.SettingsSuggest, iconColor = Color.Gray, title = "Seguir Sistema", checked = followSystem, onCheckedChange = { 
                                followSystem = it
                                uiPrefs.edit().putBoolean("follow_system", it).apply()
                            })
                            MetaSettingsDivider()
                            MetaSettingsSwitchItem(icon = Icons.Default.DarkMode, iconColor = Color.DarkGray, title = "Modo Escuro", checked = isDarkMode, onCheckedChange = { 
                                isDarkMode = it
                                uiPrefs.edit().putBoolean("dark_mode", it).apply()
                            }, enabled = !followSystem)
                        }

                        Text("SEGURANÇA", modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall, color = WarmPrimary, fontWeight = FontWeight.Bold)
                        MetaSettingsSection {
                            MetaSettingsSwitchItem(icon = Icons.Default.Lock, iconColor = WarmTextSecondary, title = "Bloqueio com PIN", checked = isPinEnabled, onCheckedChange = {
                                if (it) showPinDialog = true else {
                                    isPinEnabled = false
                                    securityPrefs.edit().putBoolean("pin_enabled", false).apply()
                                }
                            })
                            if (isPinEnabled) {
                                MetaSettingsDivider()
                                MetaSettingsItem(title = "Usar Biometria", icon = Icons.Default.Fingerprint, iconColor = WarmPrimary, trailing = {
                                    Switch(checked = isBiometricEnabled, onCheckedChange = {
                                        isBiometricEnabled = it
                                        securityPrefs.edit().putBoolean("biometric_enabled", it).apply()
                                    }, colors = SwitchDefaults.colors(checkedTrackColor = WarmPrimary))
                                })
                            }
                        }

                        Text("CONTA", modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall, color = WarmPrimary, fontWeight = FontWeight.Bold)
                        MetaSettingsSection {
                            MetaSettingsItem(title = "Sair", icon = Icons.AutoMirrored.Filled.Logout, iconColor = WarmTextSecondary, onClick = {
                                viewModel.logout()
                                val intent = Intent(context, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                }
                                context.startActivity(intent)
                                finish()
                            })
                            MetaSettingsDivider()
                            MetaSettingsItem(title = "Excluir Conta", icon = Icons.Default.DeleteForever, iconColor = MessengerBusy, textColor = MessengerBusy, onClick = { showDeleteAccountDialog = true })
                        }
                        Spacer(Modifier.height(40.dp))
                    }
                }

                if (showBlockedDialog) {
                    AlertDialog(
                        onDismissRequest = { showBlockedDialog = false },
                        title = { Text("Usuários Bloqueados") },
                        text = {
                            if (blockedProfiles.isEmpty()) {
                                Text("Nenhum usuário bloqueado.")
                            } else {
                                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                                    items(blockedProfiles) { profile ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                AsyncImage(model = profile.photoUrl, contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape).background(LocalChatColors.current.separator), contentScale = ContentScale.Crop)
                                                Spacer(Modifier.width(12.dp))
                                                Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            TextButton(onClick = { viewModel.unblockUser(profile.id) }) {
                                                Text("Desbloquear", color = WarmPrimary)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = { TextButton(onClick = { showBlockedDialog = false }) { Text("Fechar") } }
                    )
                }

                if (showDeleteAccountDialog) {
                    AlertDialog(onDismissRequest = { showDeleteAccountDialog = false }, title = { Text("Excluir conta?") }, text = { Text("Esta ação não pode ser desfeita. Para sua segurança, você precisa ter feito login recentemente para excluir a conta.") },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.deleteAccount { success, error ->
                                    if (success) {
                                        val intent = Intent(context, MainActivity::class.java).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                        }
                                        context.startActivity(intent)
                                        finish()
                                    } else {
                                        Toast.makeText(context, error ?: "Erro ao excluir conta. Tente sair e entrar novamente.", Toast.LENGTH_LONG).show()
                                    }
                                }
                                showDeleteAccountDialog = false
                            }) {
                                Text("Excluir", color = MessengerBusy)
                            }
                        },
                        dismissButton = { TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Cancelar") } }
                    )
                }

                if (showPinDialog) {
                    AlertDialog(onDismissRequest = { showPinDialog = false }, title = { Text("Definir PIN") },
                        text = { TextField(value = pinInput, onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinInput = it }, placeholder = { Text("0000") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()) },
                        confirmButton = { TextButton(onClick = { if (pinInput.length == 4) {
                            securityPrefs.edit().putBoolean("pin_enabled", true).putString("security_pin", pinInput).apply()
                            isPinEnabled = true
                            showPinDialog = false
                            pinInput = ""
                        } }) { Text("Definir") } }
                    )
                }
            }
        }
    }
}
