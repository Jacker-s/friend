package com.jack.friend.ui.chat

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import coil.compose.AsyncImage
import com.jack.friend.ChatViewModel
import com.jack.friend.MetaTextField
import com.jack.friend.ui.theme.LocalChatColors
import com.jack.friend.ui.theme.MessengerBlue
import com.jack.friend.ui.theme.MetaGray4

@Composable
fun LoginScreen(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("login_prefs", Context.MODE_PRIVATE) }

    var email by remember { mutableStateOf(prefs.getString("saved_email", "") ?: "") }
    var password by remember { mutableStateOf(prefs.getString("saved_password", "") ?: "") }
    var username by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isSignUp by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var showResetPassword by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(prefs.getBoolean("remember_me", false)) }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> selectedImageUri = uri }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Background Gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MessengerBlue.copy(alpha = 0.15f),
                            MessengerBlue.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            // Logo Section
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier
                        .size(100.dp)
                        .shadow(12.dp, RoundedCornerShape(28.dp)),
                    shape = RoundedCornerShape(28.dp),
                    color = MessengerBlue
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.ChatBubble,
                            null,
                            modifier = Modifier.size(50.dp),
                            tint = Color.White
                        )
                    }
                }
                
                // Decorative circles
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .border(1.dp, MessengerBlue.copy(alpha = 0.2f), CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "Wappi Messenger",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = MessengerBlue,
                letterSpacing = (-1).sp
            )
            
            Text(
                if (isSignUp) "Crie sua conta agora" else "Conecte-se com seus amigos",
                style = MaterialTheme.typography.bodyLarge,
                color = MetaGray4,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Card for Input Fields
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                color = LocalChatColors.current.secondaryBackground,
                shadowElevation = 0.dp,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedVisibility(
                        visible = isSignUp,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .background(LocalChatColors.current.tertiaryBackground)
                                    .clickable { photoLauncher.launch("image/*") }
                                    .border(2.dp, if (selectedImageUri != null) MessengerBlue else MessengerBlue.copy(0.3f), CircleShape),
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
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Rounded.AddAPhoto, null, tint = MessengerBlue, modifier = Modifier.size(32.dp))
                                        Text("Adicionar Foto", fontSize = 10.sp, color = MessengerBlue, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            MetaTextField(
                                value = username,
                                onValueChange = { if (!it.contains(".")) username = it },
                                placeholder = "Nome de usuário",
                                icon = Icons.Rounded.Person
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    MetaTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = "E-mail",
                        icon = Icons.Rounded.Email,
                        keyboardType = KeyboardType.Email
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    MetaTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "Senha",
                        icon = Icons.Rounded.Lock,
                        isPassword = true
                    )

                    if (!isSignUp) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = rememberMe,
                                    onCheckedChange = { rememberMe = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MessengerBlue,
                                        uncheckedColor = MetaGray4
                                    )
                                )
                                Text("Lembrar-me", fontSize = 13.sp, color = MetaGray4, fontWeight = FontWeight.Medium)
                            }
                            TextButton(onClick = { showResetPassword = true }) {
                                Text("Esqueceu a senha?", color = MessengerBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (loading) {
                        CircularProgressIndicator(color = MessengerBlue, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                    } else {
                        Button(
                            onClick = {
                                loading = true
                                if (isSignUp) viewModel.signUp(email, password, username, selectedImageUri) { s, e -> loading = false; if (!s) Toast.makeText(context, e ?: "Erro", Toast.LENGTH_SHORT).show() }
                                else viewModel.login(email, password) { s, e ->
                                    loading = false
                                    if (!s) {
                                        Toast.makeText(context, e ?: "Erro", Toast.LENGTH_SHORT).show()
                                    } else {
                                        if (rememberMe) {
                                            prefs.edit {
                                                putString("saved_email", email)
                                                putString("saved_password", password)
                                                putBoolean("remember_me", true)
                                            }
                                        } else {
                                            prefs.edit { clear() }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MessengerBlue),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 0.dp)
                        ) {
                            Text(
                                if (isSignUp) "Criar Conta" else "Entrar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = LocalChatColors.current.separator.copy(alpha = 0.5f))
                Text("OU", modifier = Modifier.padding(horizontal = 16.dp), color = MetaGray4, fontSize = 12.sp, fontWeight = FontWeight.Black)
                HorizontalDivider(modifier = Modifier.weight(1f), color = LocalChatColors.current.separator.copy(alpha = 0.5f))
            }
            
            TextButton(
                onClick = { isSignUp = !isSignUp },
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isSignUp) "Já tem uma conta?" else "Novo por aqui?",
                        color = MetaGray4,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (isSignUp) "Conectar-se" else "Crie um perfil",
                        color = MessengerBlue,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (showResetPassword) {
        var resetEmail by remember { mutableStateOf(email) }
        AlertDialog(
            onDismissRequest = { showResetPassword = false },
            title = { Text("Recuperar Senha", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enviaremos um link de redefinição para o seu e-mail.", color = MetaGray4)
                    Spacer(Modifier.height(20.dp))
                    MetaTextField(resetEmail, { resetEmail = it }, "E-mail de cadastro", Icons.Rounded.Email)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (resetEmail.isNotBlank()) viewModel.resetPassword(resetEmail) { s, e ->
                            if (s) { Toast.makeText(context, "Link enviado!", Toast.LENGTH_LONG).show(); showResetPassword = false }
                            else Toast.makeText(context, e ?: "Erro", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MessengerBlue)
                ) {
                    Text("Enviar Link", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetPassword = false }) {
                    Text("Cancelar", color = MetaGray4)
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = LocalChatColors.current.secondaryBackground
        )
    }
}

@Composable
fun InitialProfileSetupScreen(viewModel: ChatViewModel) {
    var username by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var loading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selectedImageUri = it }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp))
            Text("Quase lá!", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = MessengerBlue)
            Text("Defina seu nome de usuário para começar", textAlign = TextAlign.Center, color = MetaGray4, fontWeight = FontWeight.Medium)
            
            Spacer(Modifier.height(48.dp))
            
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(LocalChatColors.current.tertiaryBackground)
                    .clickable { photoLauncher.launch("image/*") }
                    .border(3.dp, MessengerBlue.copy(alpha = 0.5f), CircleShape)
                    .shadow(8.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selectedImageUri != null) {
                    AsyncImage(model = selectedImageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.AddAPhoto, null, modifier = Modifier.size(48.dp), tint = MessengerBlue)
                        Spacer(Modifier.height(8.dp))
                        Text("Foto de Perfil", fontSize = 12.sp, color = MessengerBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(48.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = LocalChatColors.current.secondaryBackground,
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    MetaTextField(
                        value = username, 
                        onValueChange = { if (!it.contains(".")) username = it }, 
                        placeholder = "Nome de usuário (Ex: jack)", 
                        icon = Icons.Rounded.AlternateEmail
                    )
                    Spacer(Modifier.height(16.dp))
                    MetaTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Seu nome completo",
                        icon = Icons.Rounded.Person
                    )
                }
            }
            
            Spacer(Modifier.height(40.dp))
            
            if (loading) {
                CircularProgressIndicator(color = MessengerBlue)
            } else {
                Button(
                    onClick = {
                        if (username.length < 3) {
                            Toast.makeText(context, "Username muito curto", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        loading = true
                        viewModel.finalizeProfile(username, name.ifBlank { username }, selectedImageUri) { success, error ->
                            loading = false
                            if (!success) {
                                Toast.makeText(context, error ?: "Erro ao salvar", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MessengerBlue),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text("Começar a usar", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
            }
        }
    }
}
