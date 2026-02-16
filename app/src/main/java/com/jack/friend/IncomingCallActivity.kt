package com.jack.friend

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.jack.friend.ui.theme.*

class IncomingCallActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var callStatusListener: ValueEventListener? = null
    private var roomId: String? = null
    private var isAccepted = false
    private val TAG = "IncomingCallActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or 
                          WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or 
                          WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or 
                          WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        val callMessage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("callMessage", Message::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("callMessage") as? Message
        }

        if (callMessage == null) {
            finish()
            return
        }
        
        roomId = callMessage.callRoomId
        observeCallStatus()
        startRingtone()

        setContent {
            FriendTheme {
                IncomingCallSwiftUI(
                    callerName = callMessage.senderName ?: callMessage.senderId,
                    callerPhotoUrl = callMessage.senderPhotoUrl,
                    onAccept = { acceptCall(callMessage) },
                    onReject = { rejectCall() }
                )
            }
        }
    }

    private fun observeCallStatus() {
        roomId?.let { id ->
            callStatusListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isAccepted) return
                    val status = snapshot.child("status").getValue(String::class.java)
                    if (status == "ENDED" || status == "REJECTED" || status == "CONNECTED") {
                        cleanupAndFinish()
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            FirebaseDatabase.getInstance().reference.child("calls").child(id).addValueEventListener(callStatusListener!!)
        }
    }

    private fun startRingtone() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, ringtoneUri)
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                isLooping = true
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar ringtone: ${e.message}")
        }
    }

    private fun acceptCall(message: Message) {
        if (isAccepted) return
        isAccepted = true
        stopRingtone()
        roomId?.let { FirebaseDatabase.getInstance().reference.child("calls").child(it).child("status").setValue("CONNECTED") }
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("roomId", message.callRoomId)
            putExtra("targetId", message.senderId)
            putExtra("targetPhotoUrl", message.senderPhotoUrl)
            putExtra("isOutgoing", false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun rejectCall() {
        roomId?.let { FirebaseDatabase.getInstance().reference.child("calls").child(it).child("status").setValue("REJECTED") }
        cleanupAndFinish()
    }

    private fun stopRingtone() {
        try {
            mediaPlayer?.let { if (it.isPlaying) it.stop(); it.release() }
        } catch (e: Exception) {}
        mediaPlayer = null
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(1002)
    }

    private fun cleanupAndFinish() {
        stopRingtone()
        roomId?.let { id -> callStatusListener?.let { FirebaseDatabase.getInstance().reference.child("calls").child(id).removeEventListener(it) } }
        if (!isDestroyed && !isFinishing) finish()
    }

    override fun onDestroy() {
        cleanupAndFinish()
        super.onDestroy()
    }
}

@Composable
fun IncomingCallSwiftUI(callerName: String, callerPhotoUrl: String?, onAccept: () -> Unit, onReject: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "scale"
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (callerPhotoUrl != null) {
            AsyncImage(
                model = callerPhotoUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(50.dp).scale(1.5f),
                contentScale = ContentScale.Crop,
                alpha = 0.4f
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(top = 100.dp, bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.scale(pulseScale)) {
                    Surface(
                        modifier = Modifier.size(140.dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        if (callerPhotoUrl != null) {
                            AsyncImage(
                                model = callerPhotoUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.Person, null, modifier = Modifier.size(80.dp).padding(30.dp), tint = Color.White)
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
                Text(callerName, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light)
                Text("Wappi Audio...", color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = onReject,
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Rounded.CallEnd, null, modifier = Modifier.size(40.dp), tint = Color.White)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Recusar", color = Color.White, fontSize = 15.sp)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Rounded.Call, null, modifier = Modifier.size(40.dp), tint = Color.White)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Aceitar", color = Color.White, fontSize = 15.sp)
                }
            }
        }
    }
}
