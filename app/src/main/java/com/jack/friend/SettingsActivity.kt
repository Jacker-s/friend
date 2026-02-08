package com.jack.friend

import android.content.Context
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.jack.friend.ui.theme.FriendTheme
import com.jack.friend.ui.theme.LocalChatColors

class SettingsActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FriendTheme {
                val viewModel: ChatViewModel = viewModel()
                val myName by viewModel.myName.collectAsState()
                val myPhotoUrl by viewModel.myPhotoUrl.collectAsState()
                val myId by viewModel.myId.collectAsState()

                val context = LocalContext.current
                val securityPrefs = remember { context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE) }
                val themePrefs = remember { context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE) }
                val chatColors = LocalChatColors.current
                
                var nameInput by remember { mutableStateOf("") }
                var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
                
                var isPinEnabled by remember { mutableStateOf(securityPrefs.getBoolean("pin_enabled", false)) }
                var showPinDialog by remember { mutableStateOf(false) }
                var pinInput by remember { mutableStateOf("") }

                var showDeleteAccountDialog by remember { mutableStateOf(false) }

                var selectedTheme by remember { mutableStateOf(themePrefs.getString("app_theme", "Material Design") ?: "Material Design") }
                var themeMenuExpanded by remember { mutableStateOf(false) }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START -> viewModel.updatePresence(true)
                            Lifecycle.Event.ON_STOP -> viewModel.updatePresence(false)
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                LaunchedEffect(myName) {
                    nameInput = myName
                }

                val photoLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    selectedImageUri = uri
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Configurações", color = chatColors.onTopBar) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = chatColors.onTopBar)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = chatColors.topBar)
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Foto de Perfil
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(Color.LightGray)
                                .clickable { photoLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedImageUri != null) {
                                AsyncImage(
                                    model = selectedImageUri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else if (myPhotoUrl != null) {
                                AsyncImage(
                                    model = myPhotoUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Person, null, modifier = Modifier.size(60.dp), tint = Color.White)
                            }
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.padding(bottom = 8.dp).size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text("Seu Nome de Usuário", fontSize = 12.sp, color = chatColors.primary, fontWeight = FontWeight.Bold)
                        Text("@$myId", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        
                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Seu Nome") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = chatColors.primary,
                                focusedLabelColor = chatColors.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        // Aparência
                        Text("Aparência", fontSize = 12.sp, color = chatColors.primary, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                        
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { themeMenuExpanded = true }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Palette, null, tint = Color.Gray)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Tema do App")
                                        Text(selectedTheme, fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                            }
                            
                            DropdownMenu(
                                expanded = themeMenuExpanded,
                                onDismissRequest = { themeMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Material Design") },
                                    onClick = {
                                        selectedTheme = "Material Design"
                                        themePrefs.edit().putString("app_theme", "Material Design").apply()
                                        themeMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("SwiftUI") },
                                    onClick = {
                                        selectedTheme = "SwiftUI"
                                        themePrefs.edit().putString("app_theme", "SwiftUI").apply()
                                        themeMenuExpanded = false
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        // Segurança
                        Text("Segurança", fontSize = 12.sp, color = chatColors.primary, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, null, tint = Color.Gray)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("PIN de segurança")
                            }
                            Switch(
                                checked = isPinEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) showPinDialog = true
                                    else {
                                        securityPrefs.edit().putBoolean("pin_enabled", false).apply()
                                        isPinEnabled = false
                                    }
                                }
                            )
                        }

                        if (isPinEnabled) {
                            TextButton(
                                onClick = { showPinDialog = true },
                                modifier = Modifier.align(Alignment.Start)
                            ) {
                                Text("Alterar PIN", color = chatColors.primary)
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = {
                                viewModel.updateProfile(nameInput, selectedImageUri)
                                Toast.makeText(this@SettingsActivity, "Perfil atualizado!", Toast.LENGTH_SHORT).show()
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = chatColors.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Salvar Perfil", color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Botão Excluir Conta
                        TextButton(
                            onClick = { showDeleteAccountDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                        ) {
                            Icon(Icons.Default.DeleteForever, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Excluir minha conta permanentemente")
                        }
                    }
                }

                // Diálogos
                if (showDeleteAccountDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteAccountDialog = false },
                        title = { Text("Excluir Conta?") },
                        text = { Text("Isso apagará permanentemente seu perfil e suas mensagens. Esta ação não pode ser desfeita.") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.deleteAccount { success, error ->
                                        if (success) {
                                            Toast.makeText(context, "Conta excluída", Toast.LENGTH_SHORT).show()
                                            finish()
                                        } else {
                                            Toast.makeText(context, "Erro: $error", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) {
                                Text("Excluir", color = Color.White)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteAccountDialog = false }) {
                                Text("Cancelar")
                            }
                        }
                    )
                }

                if (showPinDialog) {
                    AlertDialog(
                        onDismissRequest = { 
                            showPinDialog = false 
                            pinInput = ""
                            if (!securityPrefs.getBoolean("pin_enabled", false)) isPinEnabled = false
                        },
                        title = { Text("Definir PIN de Segurança") },
                        text = {
                            Column {
                                Text("Insira um PIN de 4 dígitos para proteger o acesso ao app.")
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedTextField(
                                    value = pinInput,
                                    onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) pinInput = it },
                                    label = { Text("PIN") },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (pinInput.length == 4) {
                                        securityPrefs.edit().putBoolean("pin_enabled", true).putString("security_pin", pinInput).apply()
                                        isPinEnabled = true
                                        showPinDialog = false
                                        pinInput = ""
                                        Toast.makeText(context, "PIN configurado!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) { Text("Confirmar") }
                        }
                    )
                }
            }
        }
    }
}
