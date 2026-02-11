package com.jack.friend

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MessagingService : Service() {

    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private var callNotificationListener: ValueEventListener? = null
    private var myUsername: String? = null

    companion object {
        private const val CALL_CHANNEL_ID = "CALL_CHANNEL_V8"
        private const val MSG_CHANNEL_ID = "MESSAGE_CHANNEL_V1"
        private const val SILENT_CHANNEL_ID = "silent_service_channel"
        private const val FOREGROUND_ID = 1001
        private const val CALL_NOTIF_ID = 1002
        private const val TAG = "MessagingService"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startSilentForeground()
        auth.currentUser?.let { setupUserListener(it.uid) }
    }

    private fun startSilentForeground() {
        val notification = NotificationCompat.Builder(this, SILENT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle("Friend").setContentText("Ativo para chamadas e mensagens").setPriority(NotificationCompat.PRIORITY_MIN).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        else startForeground(FOREGROUND_ID, notification)
    }

    private fun setupUserListener(uid: String) {
        database.child("uid_to_username").child(uid).get().addOnSuccessListener { snapshot ->
            myUsername = snapshot.getValue(String::class.java)
            myUsername?.let { 
                listenForCallSignals(it)
                listenForNewMessages(it)
            }
        }
    }

    private fun listenForNewMessages(username: String) {
        database.child("chats").child(username).addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                handleMessageSnapshot(snapshot, username)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                handleMessageSnapshot(snapshot, username)
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun handleMessageSnapshot(snapshot: DataSnapshot, username: String) {
        val summary = snapshot.getValue(ChatSummary::class.java) ?: return
        
        // SÓ MOSTRA NOTIFICAÇÃO SE o app estiver em BACKGROUND
        // Se o app estiver aberto (isAppInForeground == true), não mostramos nada, 
        // pois a lista de conversas e o chat já se atualizam sozinhos.
        if (!FriendApplication.isAppInForeground) {
            if (summary.hasUnread && summary.lastSenderId != username) {
                showNewMessageNotification(summary)
            }
        }
    }

    private fun showNewMessageNotification(summary: ChatSummary) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(this, summary.friendId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, MSG_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(summary.friendName ?: summary.friendId)
            .setContentText(summary.lastMessage)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(summary.friendId.hashCode(), builder.build())
    }

    private fun listenForCallSignals(username: String) {
        val signalsRef = database.child("call_notifications").child(username)
        callNotificationListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val m = snapshot.getValue(Message::class.java) ?: return
                if (m.isCall && m.callStatus == "STARTING") {
                    showIncomingCall(m)
                    signalsRef.removeValue()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        signalsRef.addValueEventListener(callNotificationListener!!)
    }

    private fun showIncomingCall(message: Message) {
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra("callMessage", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        
        startActivity(intent)

        val fullScreenPendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CALL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Chamada de ${message.senderName ?: message.senderId}")
            .setContentText("Toque para atender")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(CALL_NOTIF_ID, builder.build())
        message.callRoomId?.let { monitorCallStatus(it) }
    }

    private fun monitorCallStatus(roomId: String) {
        val callRef = database.child("calls").child(roomId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("status").getValue(String::class.java)
                if (status == "ENDED" || status == "REJECTED" || status == "CONNECTED") {
                    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(CALL_NOTIF_ID)
                    callRef.removeEventListener(this)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        callRef.addValueEventListener(listener)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            
            // Canal de Chamadas
            nm.createNotificationChannel(NotificationChannel(CALL_CHANNEL_ID, "Chamadas", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build())
            })
            
            // Canal de Mensagens
            nm.createNotificationChannel(NotificationChannel(MSG_CHANNEL_ID, "Mensagens", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notificações de novas mensagens de chat"
                enableVibration(true)
            })
            
            // Canal Silencioso (Foreground Service)
            nm.createNotificationChannel(NotificationChannel(SILENT_CHANNEL_ID, "Serviço", NotificationManager.IMPORTANCE_MIN))
        }
    }

    override fun onDestroy() {
        myUsername?.let { 
            database.child("call_notifications").child(it).removeEventListener(callNotificationListener!!) 
        }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?) = null
}
