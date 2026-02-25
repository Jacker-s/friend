package com.jack.friend.ui.chat

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.jack.friend.ChatViewModel
import com.jack.friend.ui.components.BackgroundStyle
import com.jack.friend.ui.components.DynamicBackground

@Composable
fun SecurityWrapper(isUserLoggedIn: Boolean, viewModel: ChatViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE) }
    val uiPrefs = remember { context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE) }
    
    val isPinEnabled = remember { prefs.getBoolean("pin_enabled", false) }
    val isBiometricEnabled = remember { prefs.getBoolean("biometric_enabled", false) }
    val correctPin = remember { prefs.getString("security_pin", "") ?: "" }
    var isUnlocked by remember { mutableStateOf(!(isPinEnabled || isBiometricEnabled)) }

    // Carrega o estilo de fundo salvo
    var backgroundStyleName by remember { 
        mutableStateOf(uiPrefs.getString("background_style", BackgroundStyle.WAVES.name) ?: BackgroundStyle.WAVES.name) 
    }

    DisposableEffect(uiPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "background_style") {
                backgroundStyleName = prefs.getString("background_style", BackgroundStyle.WAVES.name) ?: BackgroundStyle.WAVES.name
            }
        }
        uiPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { uiPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val permissions = remember {
        mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            } else add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) { launcher.launch(permissions.toTypedArray()) }

    Box(modifier = Modifier.fillMaxSize()) {
        val currentStyle = try { BackgroundStyle.valueOf(backgroundStyleName) } catch(e: Exception) { BackgroundStyle.WAVES }
        DynamicBackground(style = currentStyle)

        when {
            !isUserLoggedIn -> LoginScreen(viewModel)
            !isUnlocked -> PinLockScreen(correctPin, isBiometricEnabled) { isUnlocked = true }
            else -> ChatScreen(viewModel)
        }
    }
}
