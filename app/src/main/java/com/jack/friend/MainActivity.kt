package com.jack.friend

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.jack.friend.ui.chat.ChatScreen
import com.jack.friend.ui.theme.FriendTheme

class MainActivity : FragmentActivity() {
    private lateinit var mainViewModel: ChatViewModel
    private lateinit var appUpdateManager: AppUpdateManager
    private val updateType = AppUpdateType.FLEXIBLE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WebRTCManager.initialize(applicationContext)
        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForAppUpdate()

        setContent {
            FriendTheme {
                mainViewModel = viewModel()
                val isUserLoggedIn by mainViewModel.isUserLoggedIn.collectAsStateWithLifecycle()

                val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.POST_NOTIFICATIONS,
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_AUDIO
                    )
                } else {
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                }

                val multiplePermissionResultLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                    onResult = { perms ->
                        perms.forEach { (perm, isGranted) ->
                            Log.d("MainActivity", "Permission $perm granted: $isGranted")
                        }
                    }
                )

                LaunchedEffect(Unit) {
                    val allPermissionsGranted = permissionsToRequest.all {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) == PackageManager.PERMISSION_GRANTED
                    }
                    if (!allPermissionsGranted) {
                        multiplePermissionResultLauncher.launch(permissionsToRequest)
                    }
                }

                if (!isUserLoggedIn) {
                    LaunchedEffect(Unit) {
                        startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                        finish()
                    }
                } else {
                    ChatScreen(mainViewModel)
                }

                var showOverlayDialog by remember { mutableStateOf(false) }

                val overlayLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) {
                    // Verificação após retorno
                }

                LaunchedEffect(Unit) {
                    if (!Settings.canDrawOverlays(this@MainActivity)) {
                        showOverlayDialog = true
                    }
                }

                if (showOverlayDialog) {
                    AlertDialog(
                        onDismissRequest = { showOverlayDialog = false },
                        title = { Text("Chamadas em Segundo Plano") },
                        text = { Text("Para que você receba chamadas instantaneamente em qualquer lugar, o Wappi precisa da permissão de 'Sobreposição'. Deseja configurar?") },
                        confirmButton = {
                            Button(onClick = {
                                showOverlayDialog = false
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                                overlayLauncher.launch(intent)
                            }) {
                                Text("Configurar")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showOverlayDialog = false }) {
                                Text("Agora não")
                            }
                        }
                    )
                }

                LaunchedEffect(intent) {
                    handleIntent(intent)
                }
            }
        }
    }

    private fun checkForAppUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && info.isUpdateTypeAllowed(updateType)) {
                try {
                    appUpdateManager.startUpdateFlowForResult(info, updateType, this, 123)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        val listener = InstallStateUpdatedListener { state ->
            if (state.installStatus() == InstallStatus.DOWNLOADED) appUpdateManager.completeUpdate()
        }
        appUpdateManager.registerListener(listener)
    }

    override fun onResume() {
        super.onResume()
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) appUpdateManager.completeUpdate()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            if ("text/plain" == type) {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                mainViewModel.setPendingShare(sharedText, emptyList())
            } else if (type.startsWith("image/") || type.startsWith("video/")) {
                val mediaUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (mediaUri != null) {
                    mainViewModel.setPendingShare(null, listOf(mediaUri))
                }
            }
        } else if (Intent.ACTION_SEND_MULTIPLE == action && type != null) {
            if (type.startsWith("image/") || type.startsWith("video/")) {
                val mediaUris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (mediaUris != null) {
                    mainViewModel.setPendingShare(null, mediaUris)
                }
            }
        } else {
            val targetId = intent.getStringExtra("targetId")
            if (!targetId.isNullOrEmpty()) {
                mainViewModel.setTargetId(targetId)
            }
        }
    }
}