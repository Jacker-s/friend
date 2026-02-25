package com.jack.friend.ui.chat

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.jack.friend.ui.theme.LocalChatColors
import com.jack.friend.ui.theme.MessengerBlue

@Composable
fun PinLockScreen(correctPin: String, isBiometricEnabled: Boolean, onUnlock: () -> Unit) {
    var pinInput by remember { mutableStateOf("") }
    val activity = LocalActivity.current
    val view = LocalView.current

    fun showBiometricPrompt() {
        val fragmentActivity = activity as? FragmentActivity ?: return
        val executor = ContextCompat.getMainExecutor(fragmentActivity)
        val biometricPrompt = BiometricPrompt(fragmentActivity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onUnlock()
                }
            })
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Bloqueio de Segurança")
            .setSubtitle("Uma biometria para entrar")
            .setNegativeButtonText("Usar PIN")
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    LaunchedEffect(Unit) { if (isBiometricEnabled) showBiometricPrompt() }

    Column(modifier = Modifier.fillMaxSize().background(Color.Transparent).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.Lock, null, modifier = Modifier.size(64.dp), tint = MessengerBlue)
        Spacer(modifier = Modifier.height(24.dp))
        Text("App Protegido", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { index ->
                Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(if (pinInput.length > index) MessengerBlue else LocalChatColors.current.separator))
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "DEL")
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            items(keys) { key ->
                if (key.isNotEmpty()) {
                    val isDel = key == "DEL"
                    Box(
                        modifier = Modifier.size(75.dp).clip(CircleShape).background(LocalChatColors.current.tertiaryBackground.copy(alpha = 0.8f)).clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            if (isDel) {
                                if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
                            } else if (pinInput.length < 4) {
                                pinInput += key
                                if (pinInput.length == 4) {
                                    if (pinInput == correctPin) onUnlock()
                                    else {
                                        pinInput = ""
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                    }
                                }
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDel) Icon(Icons.AutoMirrored.Filled.Backspace, null, tint = MaterialTheme.colorScheme.onSurface)
                        else Text(key, style = MaterialTheme.typography.titleMedium.copy(fontSize = 28.sp))
                    }
                } else Spacer(Modifier.size(75.dp))
            }
        }

        if (isBiometricEnabled) {
            IconButton(onClick = { showBiometricPrompt() }, modifier = Modifier.padding(top = 24.dp)) {
                Icon(Icons.Rounded.Fingerprint, null, modifier = Modifier.size(40.dp), tint = MessengerBlue)
            }
        }
    }
}
