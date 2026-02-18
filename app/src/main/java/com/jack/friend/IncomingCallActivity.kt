package com.jack.friend

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.database.*
import com.jack.friend.ui.theme.FriendTheme
import kotlin.math.max
import kotlin.math.roundToInt

class IncomingCallActivity : ComponentActivity() {

    companion object {
        private const val TAG = "IncomingCallActivity"
        private const val EXTRA_CALL_MESSAGE = "callMessage"

        // ✅ vamos padronizar: o app todo usa "isVideo"
        private const val EXTRA_IS_VIDEO = "isVideo"

        private const val NOTIF_ID_INCOMING_CALL = 1002

        private const val STATUS_CONNECTED = "CONNECTED"
        private const val STATUS_REJECTED = "REJECTED"
        private const val STATUS_ENDED = "ENDED"

        private const val TIMEOUT_MS = 30_000L
    }

    private var mediaPlayer: MediaPlayer? = null

    private var callRef: DatabaseReference? = null
    private var callStatusListener: ValueEventListener? = null
    private var roomId: String? = null

    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val handler by lazy { Handler(mainLooper) }
    private var timeoutRunnable: Runnable? = null

    private var hasFinished = false
    private var isAccepted = false

    // ✅ tipo da call (áudio/vídeo)
    private var isVideoCall: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        showOnLockScreen()
        initVibrator()
        acquireWakeLock()

        val message = getCallMessageOrFinish() ?: return
        roomId = message.callRoomId

        // ✅ 1) tenta pegar do Intent
        isVideoCall = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)

        observeCallStatusAndType()
        startTimeout()
        startRingtone()
        startVibration()

        setContent {
            FriendTheme {
                IncomingCallScreen(
                    callerName = message.senderName ?: message.senderId,
                    callerPhotoUrl = message.senderPhotoUrl,
                    isVideo = isVideoCall,
                    onAccept = { acceptCall(message) },
                    onReject = { rejectCall() }
                )
            }
        }
    }

    private fun getCallMessageOrFinish(): Message? {
        val msg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_CALL_MESSAGE, Message::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_CALL_MESSAGE) as? Message
        }

        if (msg == null) {
            safeFinish()
            return null
        }
        return msg
    }

    private fun showOnLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * ✅ CORREÇÃO: observa STATUS e também isVideo (fallback Firebase).
     * Assim o incoming reconhece vídeo mesmo que a Message não tenha esse campo.
     */
    private fun observeCallStatusAndType() {
        val id = roomId ?: return
        callRef = FirebaseDatabase.getInstance().reference.child("calls").child(id)

        callStatusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // ✅ fallback do tipo de call via Firebase
                val firebaseIsVideo = snapshot.child("isVideo").getValue(Boolean::class.java)
                if (firebaseIsVideo != null) {
                    isVideoCall = firebaseIsVideo
                }

                if (hasFinished || isAccepted) return

                val status = snapshot.child("status").getValue(String::class.java)
                if (status == STATUS_ENDED || status == STATUS_REJECTED || status == STATUS_CONNECTED) {
                    cleanupAndFinish()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "observeCallStatus cancelled: ${error.message}")
            }
        }

        callRef?.addValueEventListener(callStatusListener!!)
    }

    private fun startTimeout() {
        cancelTimeout()
        timeoutRunnable = Runnable {
            if (!hasFinished && !isAccepted) {
                setStatus(STATUS_ENDED)
                cleanupAndFinish()
            }
        }
        handler.postDelayed(timeoutRunnable!!, TIMEOUT_MS)
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun startVibration() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        val pattern = longArrayOf(0, 350, 200, 350, 800)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, 0)
            }
        } catch (_: Exception) {}
    }

    private fun stopVibration() {
        try { vibrator?.cancel() } catch (_: Exception) {}
    }

    private fun startRingtone() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ringtone error: ${e.message}", e)
        }
    }

    private fun stopRingtone() {
        stopVibration()
        try {
            mediaPlayer?.let {
                runCatching { if (it.isPlaying) it.stop() }
                runCatching { it.release() }
            }
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
        }

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID_INCOMING_CALL)
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:incoming_call")
            wakeLock?.setReferenceCounted(false)
            wakeLock?.acquire(TIMEOUT_MS + 10_000L)
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock error: ${e.message}", e)
        }
    }

    private fun releaseWakeLock() {
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        wakeLock = null
    }

    private fun setStatus(status: String) {
        val id = roomId ?: return
        FirebaseDatabase.getInstance().reference
            .child("calls").child(id).child("status")
            .setValue(status)
    }

    private fun acceptCall(message: Message) {
        if (isAccepted || hasFinished) return
        isAccepted = true

        cancelTimeout()
        stopRingtone()
        setStatus(STATUS_CONNECTED)

        // ✅ CORREÇÃO PRINCIPAL: passar isVideo corretamente
        startActivity(
            Intent(this, CallActivity::class.java).apply {
                putExtra("roomId", message.callRoomId)
                putExtra("targetId", message.senderId)
                putExtra("targetPhotoUrl", message.senderPhotoUrl)
                putExtra("isOutgoing", false)
                putExtra("isVideo", isVideoCall) // ✅ agora sempre certo
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )

        safeFinish()
    }

    private fun rejectCall() {
        if (hasFinished) return
        cancelTimeout()
        setStatus(STATUS_REJECTED)
        cleanupAndFinish()
    }

    private fun cleanupAndFinish() {
        if (hasFinished) return
        hasFinished = true

        cancelTimeout()
        stopRingtone()

        callStatusListener?.let { listener ->
            callRef?.removeEventListener(listener)
        }
        callStatusListener = null
        callRef = null

        releaseWakeLock()
        safeFinish()
    }

    private fun safeFinish() {
        if (!isFinishing && !isDestroyed) finish()
    }

    override fun onDestroy() {
        cancelTimeout()
        stopRingtone()

        callStatusListener?.let { listener ->
            callRef?.removeEventListener(listener)
        }
        callStatusListener = null
        callRef = null

        releaseWakeLock()
        super.onDestroy()
    }
}

// ============================
// UI
// ============================
@Composable
private fun IncomingCallScreen(
    callerName: String,
    callerPhotoUrl: String?,
    isVideo: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "scale"
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        if (!callerPhotoUrl.isNullOrBlank()) {
            AsyncImage(
                model = callerPhotoUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(50.dp).scale(1.5f),
                contentScale = ContentScale.Crop,
                alpha = 0.4f
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 100.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Box(contentAlignment = Alignment.Center, modifier = Modifier.scale(pulseScale)) {
                    Surface(
                        modifier = Modifier.size(140.dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.10f)
                    ) {
                        if (!callerPhotoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = callerPhotoUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp).padding(30.dp),
                                tint = Color.White
                            )
                        }
                    }
                }

                Spacer(Modifier.height(26.dp))

                Text(
                    text = callerName,
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Light
                )

                Text(
                    text = if (isVideo) "Wappi Vídeo..." else "Wappi Áudio...",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 90.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SlideToAction(
                    text = "Deslize para atender",
                    trackColor = Color.White.copy(alpha = 0.12f),
                    accentColor = Color(0xFF34C759),
                    icon = Icons.Rounded.Call,
                    direction = SlideDirection.RIGHT,
                    onTriggered = onAccept
                )

                Spacer(Modifier.height(16.dp))

                SlideToAction(
                    text = "Deslize para recusar",
                    trackColor = Color.White.copy(alpha = 0.12f),
                    accentColor = Color(0xFFFF3B30),
                    icon = Icons.Rounded.CallEnd,
                    direction = SlideDirection.LEFT,
                    onTriggered = onReject
                )
            }
        }
    }
}

private enum class SlideDirection { LEFT, RIGHT }

@Composable
private fun SlideToAction(
    text: String,
    trackColor: Color,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    direction: SlideDirection,
    onTriggered: () -> Unit
) {
    val trackHeight = 56.dp
    val knobSize = 48.dp
    val shape = RoundedCornerShape(999.dp)

    val density = LocalDensity.current
    var widthPx by remember { mutableStateOf(0f) }

    val knobPx = with(density) { knobSize.toPx() }
    val paddingPx = with(density) { 6.dp.toPx() }

    var offsetPx by remember { mutableStateOf(0f) }
    var triggered by remember { mutableStateOf(false) }

    val maxOffset = max(0f, widthPx - knobPx - paddingPx * 2)
    val threshold = maxOffset * 0.72f

    fun reset() {
        offsetPx = 0f
        triggered = false
    }

    val draggableState = rememberDraggableState { delta ->
        if (triggered) return@rememberDraggableState

        offsetPx = when (direction) {
            SlideDirection.RIGHT -> offsetPx + delta
            SlideDirection.LEFT -> offsetPx - delta
        }.coerceIn(0f, maxOffset)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .height(trackHeight)
            .clip(shape)
            .background(trackColor)
            .onSizeChanged { widthPx = it.width.toFloat() }
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.align(Alignment.Center),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )

        Box(
            modifier = Modifier
                .padding(6.dp)
                .offset {
                    val x = when (direction) {
                        SlideDirection.RIGHT -> offsetPx
                        SlideDirection.LEFT -> (maxOffset - offsetPx)
                    }
                    IntOffset(x.roundToInt(), 0)
                }
                .size(knobSize)
                .clip(CircleShape)
                .background(accentColor)
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        if (!triggered && offsetPx >= threshold) {
                            triggered = true
                            onTriggered()
                        } else {
                            reset()
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
    }
}
