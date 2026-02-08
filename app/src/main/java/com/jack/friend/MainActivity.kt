package com.jack.friend

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.jack.friend.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Iniciar serviço de segundo plano (Background Service)
        val serviceIntent = Intent(this, MessagingService::class.java)
        startService(serviceIntent)

        setContent {
            FriendTheme {
                val viewModel: ChatViewModel = viewModel()
                val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsState()
                
                val context = LocalContext.current
                val prefs = remember { context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE) }
                val isPinEnabled = remember { prefs.getBoolean("pin_enabled", false) }
                val correctPin = remember { prefs.getString("security_pin", "") ?: "" }
                
                var isUnlocked by remember { mutableStateOf(!isPinEnabled) }

                // Solicitar permissão de notificação no Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        if (!isGranted) {
                            Toast.makeText(context, "Permissão de notificação negada", Toast.LENGTH_SHORT).show()
                        }
                    }
                    LaunchedEffect(Unit) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                if (!isUserLoggedIn) {
                    LoginScreen(viewModel)
                } else if (!isUnlocked) {
                    PinLockScreen(correctPin) {
                        isUnlocked = true
                    }
                } else {
                    ChatScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun LoginScreen(viewModel: ChatViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isSignUp by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val chatColors = LocalChatColors.current
    val context = LocalContext.current

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(chatColors.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isSignUp) "Criar Conta" else "Entrar no Wapi",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = chatColors.primary
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        if (isSignUp) {
            // Foto de Perfil no Cadastro
            Box(
                modifier = Modifier
                    .size(100.dp)
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
                } else {
                    Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(40.dp), tint = Color.White)
                }
            }
            Text("Adicionar Foto", fontSize = 12.sp, color = chatColors.primary, modifier = Modifier.padding(top = 8.dp))
            
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Nome de Usuário") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Senha") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (loading) {
            CircularProgressIndicator(color = chatColors.primary)
        } else {
            Button(
                onClick = {
                    loading = true
                    if (isSignUp) {
                        viewModel.signUp(email, password, username, selectedImageUri) { success, error ->
                            loading = false
                            if (!success) Toast.makeText(context, error ?: "Erro ao cadastrar", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        viewModel.login(email, password) { success, error ->
                            loading = false
                            if (!success) Toast.makeText(context, error ?: "Erro ao entrar", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = chatColors.primary)
            ) {
                Text(if (isSignUp) "Cadastrar" else "Entrar", color = Color.White, fontSize = 16.sp)
            }

            TextButton(onClick = { isSignUp = !isSignUp }) {
                Text(
                    if (isSignUp) "Já tem uma conta? Entre" else "Não tem uma conta? Cadastre-se",
                    color = chatColors.primary
                )
            }
        }
    }
}

@Composable
fun PinLockScreen(correctPin: String, onUnlock: () -> Unit) {
    var pinInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val chatColors = LocalChatColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(chatColors.primary)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color.White
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Wapi App Protegido",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Insira seu PIN para entrar",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 16.sp
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = pinInput,
            onValueChange = { 
                if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                    pinInput = it
                    error = false
                    if (it == correctPin) {
                        onUnlock()
                    }
                }
            },
            modifier = Modifier.width(200.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            isError = error,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black
            ),
            shape = RoundedCornerShape(12.dp)
        )
        
        if (error) {
            Text("PIN incorreto", color = Color.Red, modifier = Modifier.padding(top = 8.dp))
        }

        if (pinInput.length == 4 && pinInput != correctPin) {
            LaunchedEffect(pinInput) {
                error = true
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val myId by viewModel.myId.collectAsState()
    val myName by viewModel.myName.collectAsState()
    val myPhotoUrl by viewModel.myPhotoUrl.collectAsState()
    val targetId by viewModel.targetId.collectAsState()
    val targetProfile by viewModel.targetProfile.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val activeChats by viewModel.activeChats.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val blockedUsers by viewModel.blockedUsers.collectAsState()
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val chatColors = LocalChatColors.current
    
    var textState by remember { mutableStateOf("") }
    var searchInput by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    
    val clipboard = LocalClipboard.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var fullScreenImage by remember { mutableStateOf<String?>(null) }

    // Gerenciamento de Status Online (Apenas em primeiro plano)
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

    // Intercepta o botão voltar do Android
    BackHandler(enabled = targetId.isNotEmpty() || fullScreenImage != null || isSearching) {
        if (fullScreenImage != null) {
            fullScreenImage = null
        } else if (isSearching) {
            isSearching = false
            searchInput = ""
            viewModel.searchUsers("")
        } else {
            viewModel.setTargetId("")
        }
    }

    // Controle do aviso de mensagens novas
    var hasNewMessages by remember { mutableStateOf(false) }
    var lastMessageCount by remember { mutableIntStateOf(0) }

    // Detectar se o usuário está no final da lista
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (layoutInfo.totalItemsCount == 0) {
                true
            } else {
                val lastVisibleItem = visibleItemsInfo.lastOrNull()
                lastVisibleItem?.index == layoutInfo.totalItemsCount - 1
            }
        }
    }

    // Resetar estados e scroll ao trocar de chat
    LaunchedEffect(targetId) {
        if (targetId.isNotEmpty()) {
            lastMessageCount = 0
            hasNewMessages = false
            // Tenta scrollar para o fim caso já existam mensagens (cache local)
            if (messages.isNotEmpty()) {
                listState.scrollToItem(messages.size - 1)
            }
        }
    }

    // Monitorar novas mensagens para scroll automático ou alerta
    LaunchedEffect(messages) {
        if (messages.size > lastMessageCount) {
            if (isAtBottom) {
                // Se já estiver no fim, acompanha as novas mensagens
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.size - 1)
                }
                hasNewMessages = false
            } else if (lastMessageCount > 0) {
                // Se não estiver no fim e NÃO for o carregamento inicial, avisa que há novas mensagens
                hasNewMessages = true
            } else {
                // Carregamento inicial do chat: vai direto para o fim
                if (messages.isNotEmpty()) {
                    listState.scrollToItem(messages.size - 1)
                }
            }
        }
        lastMessageCount = messages.size
    }

    LaunchedEffect(isAtBottom) {
        if (isAtBottom) hasNewMessages = false
    }

    // Launcher para Galeria
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadImage(it) }
    }

    // Launcher para Câmera
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempPhotoUri?.let { viewModel.uploadImage(it) }
        }
    }

    // Launcher para Solicitar Permissões
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (recordAudioGranted) {
            Toast.makeText(context, "Permissão de áudio concedida", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCamera() {
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            val file = File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            tempPhotoUri = uri
            cameraLauncher.launch(uri)
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = {
                    if (targetId.isNotEmpty()) {
                        val currentChat = activeChats.find { it.friendId == targetId }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = Color.LightGray
                            ) {
                                val photoUrl = targetProfile?.photoUrl ?: currentChat?.friendPhotoUrl
                                if (photoUrl != null) {
                                    AsyncImage(
                                        model = photoUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.Person, null, modifier = Modifier.padding(4.dp), tint = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    targetProfile?.name ?: currentChat?.friendName ?: targetId,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = chatColors.onTopBar,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val isOnline = targetProfile?.isOnline == true
                                val lastActive = targetProfile?.lastActive ?: 0L
                                val statusText = when {
                                    isOnline -> "online"
                                    lastActive > 0 -> {
                                        val now = System.currentTimeMillis()
                                        val diff = now - lastActive
                                        if (diff < 60000) "visto por último agora"
                                        else "visto por último " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastActive))
                                    }
                                    else -> ""
                                }
                                if (statusText.isNotEmpty()) {
                                    Text(statusText, style = MaterialTheme.typography.labelSmall, color = chatColors.onTopBar.copy(alpha = 0.8f))
                                }
                            }
                        }
                    } else {
                        if (isSearching) {
                            TextField(
                                value = searchInput,
                                onValueChange = { 
                                    searchInput = it
                                    viewModel.searchUsers(it)
                                },
                                placeholder = { Text("Buscar usuário...", color = chatColors.onTopBar.copy(alpha = 0.6f)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = chatColors.onTopBar,
                                    unfocusedTextColor = chatColors.onTopBar,
                                    cursorColor = chatColors.onTopBar
                                )
                            )
                        } else {
                            Text("Wapi", color = chatColors.onTopBar, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                navigationIcon = {
                    if (targetId.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setTargetId("") }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = chatColors.onTopBar)
                        }
                    } else if (isSearching) {
                        IconButton(onClick = { 
                            isSearching = false
                            searchInput = ""
                            viewModel.searchUsers("")
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar Busca", tint = chatColors.onTopBar)
                        }
                    }
                },
                actions = {
                    if (targetId.isEmpty()) {
                        if (!isSearching) {
                            IconButton(onClick = { isSearching = true }) {
                                Icon(Icons.Default.Search, null, tint = chatColors.onTopBar)
                            }
                        }
                        // Foto de Perfil no Topo Direito (Substitui os 3 pontinhos na Home)
                        IconButton(onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) }) {
                            Surface(
                                modifier = Modifier.size(32.dp),
                                shape = CircleShape,
                                color = Color.LightGray
                            ) {
                                if (myPhotoUrl != null) {
                                    AsyncImage(
                                        model = myPhotoUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.Person, null, modifier = Modifier.padding(4.dp), tint = Color.White)
                                }
                            }
                        }
                    } else {
                        // Menu apenas dentro do Chat (Para Bloquear e Limpar)
                        Box {
                            IconButton(onClick = { menuExpanded = true }) { 
                                Icon(Icons.Default.MoreVert, null, tint = chatColors.onTopBar) 
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Limpar Conversa") },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.clearChat()
                                    },
                                    leadingIcon = { Icon(Icons.Default.DeleteSweep, null) }
                                )
                                val isBlocked = blockedUsers.contains(targetId)
                                DropdownMenuItem(
                                    text = { Text(if (isBlocked) "Desbloquear Usuário" else "Bloquear Usuário") },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.toggleBlockUser(targetId)
                                        if (!isBlocked) viewModel.setTargetId("") 
                                    },
                                    leadingIcon = { Icon(if (isBlocked) Icons.Default.LockOpen else Icons.Default.Block, null) }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = chatColors.topBar)
            )
        },
        bottomBar = {
            if (targetId.isNotBlank()) {
                WhatsAppInput(
                    text = textState,
                    onTextChange = { textState = it },
                    onEmojiClick = { showEmojiPicker = true },
                    onImageClick = { imageLauncher.launch("image/*") },
                    onCameraClick = { launchCamera() },
                    onSend = {
                        if (textState.isNotBlank()) {
                            viewModel.sendMessage(textState)
                            textState = ""
                        }
                    },
                    onStartAudio = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            viewModel.startRecording()
                        } else {
                            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                        }
                    },
                    onStopAudio = { viewModel.stopRecording() }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(chatColors.background)
        ) {
            if (targetId.isBlank()) {
                if (isSearching && searchInput.isNotEmpty()) {
                    // Lista de resultados da busca integrada
                    if (searchResults.isNotEmpty()) {
                        LazyColumn(modifier = Modifier.fillMaxSize().background(Color.White)) {
                            items(searchResults) { user ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            isSearching = false
                                            searchInput = ""
                                            viewModel.setTargetId(user.id)
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(modifier = Modifier.size(50.dp), shape = CircleShape, color = Color.LightGray) {
                                        if (user.photoUrl != null) {
                                            AsyncImage(model = user.photoUrl, contentDescription = null, contentScale = ContentScale.Crop)
                                        } else {
                                            Icon(Icons.Default.Person, null, modifier = Modifier.padding(10.dp), tint = Color.White)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(user.id, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                            Text("Nenhum usuário encontrado", color = Color.Gray)
                        }
                    }
                } else {
                    // Lista de conversas ativas
                    if (activeChats.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.AutoMirrored.Filled.Message, null, modifier = Modifier.size(80.dp), tint = Color.LightGray.copy(alpha = 0.5f))
                                Spacer(Modifier.height(16.dp))
                                Text("Nenhuma mensagem recebida ainda", color = Color.Gray)
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().background(Color.White)) {
                            items(activeChats) { summary ->
                                var showMenu by remember { mutableStateOf(false) }
                                
                                Box {
                                    WhatsChatItem(
                                        summary = summary, 
                                        myId = myId,
                                        onLongClick = { showMenu = true }
                                    ) {
                                        viewModel.setTargetId(summary.friendId)
                                    }

                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        val isFriendBlocked = blockedUsers.contains(summary.friendId)
                                        DropdownMenuItem(
                                            text = { Text(if (isFriendBlocked) "Desbloquear" else "Bloquear") },
                                            leadingIcon = { Icon(if (isFriendBlocked) Icons.Default.LockOpen else Icons.Default.Block, null) },
                                            onClick = {
                                                viewModel.toggleBlockUser(summary.friendId)
                                                showMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Apagar Conversa", color = Color.Red) },
                                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) },
                                            onClick = {
                                                viewModel.clearChat(summary.friendId)
                                                showMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Tela de Chat
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        WhatsMessageBubble(
                            message = message, 
                            isMe = message.senderId == myId,
                            onImageClick = { fullScreenImage = it }
                        )
                    }
                }
                
                // Botão de "Novas Mensagens Abaixo"
                AnimatedVisibility(
                    visible = hasNewMessages,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 90.dp, end = 16.dp)
                ) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(messages.size - 1)
                                hasNewMessages = false
                            }
                        },
                        containerColor = Color.White,
                        contentColor = chatColors.primary,
                        shape = CircleShape,
                        modifier = Modifier.height(40.dp),
                        elevation = FloatingActionButtonDefaults.elevation(4.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, null)
                        Text("Mensagens novas", fontSize = 12.sp)
                    }
                }
            }

            if (showEmojiPicker) {
                ModalBottomSheet(
                    onDismissRequest = { showEmojiPicker = false },
                    sheetState = sheetState,
                    containerColor = Color.White
                ) {
                    EmojiPicker(onEmojiSelected = { 
                        textState += it
                        showEmojiPicker = false
                    })
                }
            }

            // Full screen image viewer
            if (fullScreenImage != null) {
                FullScreenImageViewer(
                    imageUrl = fullScreenImage!!,
                    onDismiss = { fullScreenImage = null }
                )
            }
        }
    }
}

@Composable
fun FullScreenImageViewer(imageUrl: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
            }
        }
    }
}

@Composable
fun EmojiPicker(onEmojiSelected: (String) -> Unit) {
    val emojis = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
        "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
        "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩",
        "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣",
        "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬",
        "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗",
        "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯",
        "😦", "😧", "😮", "😲", "😴", "🤤", "😪", "😵", "🤐", "🥴",
        "🤢", "🤮", "🤧", "😷", "🧼", "🤕", "🤑", "🤠", "😈", "👿",
        "👹", "👺", "🤡", "👻", "💀", "☠️", "👽", "👾", "🤖", "🎃"
    )

    Column(modifier = Modifier.fillMaxWidth().height(300.dp).padding(16.dp)) {
        Text("Emojis", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(emojis) { emoji ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onEmojiSelected(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 24.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WhatsChatItem(
    summary: ChatSummary, 
    myId: String, 
    onLongClick: () -> Unit = {},
    onClick: () -> Unit
) {
    val chatColors = LocalChatColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(50.dp), shape = CircleShape, color = Color.LightGray) {
            if (summary.friendPhotoUrl != null) {
                AsyncImage(model = summary.friendPhotoUrl, contentDescription = null, contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.Person, null, modifier = Modifier.padding(10.dp), tint = Color.White)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(summary.friendName ?: summary.friendId, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (summary.isOnline) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(8.dp).background(Color.Green, CircleShape))
                    }
                }
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(summary.timestamp))
                Text(time, fontSize = 12.sp, color = if (summary.hasUnread) chatColors.fab else Color.Gray)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (summary.lastSenderId == myId) { // Se você foi o remetente
                     Icon(
                         Icons.Default.DoneAll, 
                         null, 
                         modifier = Modifier.size(16.dp), 
                         tint = if (summary.lastMessageRead) WhatsBlue else Color.Gray
                     )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    summary.lastMessage,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.weight(1f)
                )
                if (summary.hasUnread) {
                    Box(modifier = Modifier.size(20.dp).background(chatColors.fab, CircleShape), contentAlignment = Alignment.Center) {
                        Text("1", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WhatsMessageBubble(
    message: Message, 
    isMe: Boolean, 
    onLongClick: () -> Unit = {},
    onImageClick: (String) -> Unit = {}
) {
    val alignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    val chatColors = LocalChatColors.current
    val bgColor = if (isMe) chatColors.bubbleMe else chatColors.bubbleOther
    
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Surface(
            color = bgColor,
            shape = RoundedCornerShape(
                topStart = 8.dp, topEnd = 8.dp,
                bottomStart = if (isMe) 8.dp else 0.dp,
                bottomEnd = if (isMe) 0.dp else 8.dp
            ),
            shadowElevation = 1.dp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(vertical = 2.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick
                )
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                if (message.imageUrl != null) {
                    AsyncImage(
                        model = message.imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onImageClick(message.imageUrl!!) },
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                if (message.audioUrl != null) {
                    AudioPlayer(message.audioUrl!!, isMe)
                }
                
                Row(verticalAlignment = Alignment.Bottom) {
                    if (message.text.isNotEmpty()) {
                        Text(
                            text = message.text,
                            fontSize = 16.sp,
                            color = if (isMe && chatColors.bubbleMe == AppleBlue) Color.White else Color.Unspecified,
                            modifier = Modifier.padding(end = 8.dp).weight(1f, fill = false)
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
                        Text(time, fontSize = 10.sp, color = if (isMe && chatColors.bubbleMe == AppleBlue) Color.White.copy(alpha = 0.7f) else Color.Gray)
                        if (isMe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.DoneAll, null, modifier = Modifier.size(14.dp), tint = if (message.isRead) WhatsBlue else Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioPlayer(audioUrl: String, isMe: Boolean) {
    val chatColors = LocalChatColors.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableIntStateOf(1) }
    
    val mediaPlayer = remember { MediaPlayer() }
    
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    // Loop para atualizar a barra de progresso
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                currentPosition = mediaPlayer.currentPosition.toFloat() / duration
                if (!mediaPlayer.isPlaying) isPlaying = false
                delay(50)
            }
        }
    }

    val bubbleColor = if (isMe && chatColors.bubbleMe == AppleBlue) Color.White else chatColors.primary
    val iconColor = if (isMe && chatColors.bubbleMe == AppleBlue) AppleBlue else Color.White

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .height(56.dp)
            .background(bubbleColor.copy(alpha = 0.1f), RoundedCornerShape(28.dp))
            .padding(horizontal = 4.dp)
    ) {
        // Botão Play/Pause Circular
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(bubbleColor, CircleShape)
                .clickable {
                    if (isPlaying) {
                        mediaPlayer.pause()
                        isPlaying = false
                    } else {
                        try {
                            if (mediaPlayer.currentPosition > 0) {
                                mediaPlayer.start()
                                isPlaying = true
                            } else {
                                mediaPlayer.reset()
                                mediaPlayer.setDataSource(audioUrl)
                                mediaPlayer.prepareAsync()
                                mediaPlayer.setOnPreparedListener { 
                                    duration = it.duration
                                    it.start()
                                    isPlaying = true
                                }
                                mediaPlayer.setOnCompletionListener { 
                                    isPlaying = false
                                    currentPosition = 0f
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Visualizador de Onda / Progresso
        Box(modifier = Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val barWidth = 4.dp.toPx()
                val gap = 2.dp.toPx()
                val barCount = (canvasWidth / (barWidth + gap)).toInt()
                
                for (i in 0 until barCount) {
                    val progress = i.toFloat() / barCount
                    val isPast = progress < currentPosition
                    
                    // Altura aleatória para simular onda (baseada no index)
                    val randomFactor = (Math.sin(i.toDouble() * 0.5) + 1) / 2
                    val h = (canvasHeight * 0.3f + canvasHeight * 0.5f * randomFactor).toFloat()
                    
                    drawLine(
                        color = if (isPast) bubbleColor else bubbleColor.copy(alpha = 0.3f),
                        start = Offset(i * (barWidth + gap), (canvasHeight - h) / 2),
                        end = Offset(i * (barWidth + gap), (canvasHeight + h) / 2),
                        strokeWidth = barWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))
        
        // Timer
        val remainingSeconds = if (isPlaying) (duration - mediaPlayer.currentPosition) / 1000 else duration / 1000
        Text(
            text = String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60),
            fontSize = 12.sp,
            color = bubbleColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(end = 12.dp)
        )
    }
}

@Composable
fun WhatsAppInput(
    text: String,
    onTextChange: (String) -> Unit,
    onEmojiClick: () -> Unit,
    onImageClick: () -> Unit,
    onCameraClick: () -> Unit,
    onSend: () -> Unit,
    onStartAudio: () -> Unit,
    onStopAudio: () -> Unit
) {
    val chatColors = LocalChatColors.current
    var isRecording by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 1.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                IconButton(onClick = onEmojiClick) { Icon(Icons.Default.SentimentSatisfied, null, tint = Color.Gray) }
                TextField(
                    value = if (isRecording) "Gravando áudio..." else text,
                    onValueChange = onTextChange,
                    placeholder = { Text("Mensagem") },
                    modifier = Modifier.weight(1f),
                    enabled = !isRecording,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                if (!isRecording) {
                    IconButton(onClick = onImageClick) { Icon(Icons.Default.AttachFile, null, tint = Color.Gray) }
                    IconButton(onClick = onCameraClick) { Icon(Icons.Default.PhotoCamera, null, tint = Color.Gray) }
                }
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        
        val buttonColor = if (isRecording) Color.Red else chatColors.primary
        
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(buttonColor, CircleShape)
                .pointerInput(text) {
                    detectTapGestures(
                        onPress = {
                            if (text.isBlank()) {
                                try {
                                    isRecording = true
                                    onStartAudio()
                                    awaitRelease()
                                } finally {
                                    if (isRecording) {
                                        isRecording = false
                                        onStopAudio()
                                    }
                                }
                            } else {
                                onSend()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (text.isBlank()) Icons.Default.Mic else Icons.AutoMirrored.Filled.Send,
                contentDescription = "Enviar",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
