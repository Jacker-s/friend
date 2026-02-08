package com.jack.friend

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MessagingService : Service() {

    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private var globalListener: ChildEventListener? = null
    private var myUsername: String? = null

    companion object {
        private const val CHANNEL_ID = "messages_channel"
        private const val SILENT_CHANNEL_ID = "silent_service_channel"
        private const val FOREGROUND_ID = 1001
        private const val GROUP_KEY = "com.jack.friend.MESSAGES"
        private const val SUMMARY_ID = 0
        private const val TAG = "MessagingService"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startSilentForeground()
        observeMessages()
    }

    private fun startSilentForeground() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        // Esta notificação mantém o app vivo mas é "silenciosa" e não aparece na barra de status
        val notification = NotificationCompat.Builder(this, SILENT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Friend")
            .setContentText("Conectado") // Texto mínimo
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(FOREGROUND_ID, notification)
    }

    private fun observeMessages() {
        val user = auth.currentUser
        if (user != null) {
            fetchUsernameAndSetup(user.uid)
        }
        
        auth.addAuthStateListener { firebaseAuth ->
            val newUser = firebaseAuth.currentUser
            if (newUser != null) {
                fetchUsernameAndSetup(newUser.uid)
            } else {
                removeListener()
                myUsername = null
            }
        }
    }

    private fun fetchUsernameAndSetup(uid: String) {
        database.child("uid_to_username").child(uid).get().addOnSuccessListener { snapshot ->
            val username = snapshot.getValue(String::class.java)
            if (username != null && username != myUsername) {
                myUsername = username
                setupListener(username)
            }
        }
    }

    private fun removeListener() {
        globalListener?.let { 
            database.child("messages").removeEventListener(it)
            globalListener = null
        }
    }

    private fun setupListener(username: String) {
        removeListener()
        val startTime = System.currentTimeMillis()
        
        database.child(".info/connected").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) Log.d(TAG, "Conectado ao Firebase")
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        globalListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val m = snapshot.getValue(Message::class.java) ?: return
                
                if (m.receiverId == username && m.timestamp >= startTime) {
                    database.child("users").child(username).child("isOnline").get().addOnSuccessListener { onlineSnapshot ->
                        val isOnline = onlineSnapshot.getValue(Boolean::class.java) ?: false
                        
                        if (!isOnline) {
                            database.child("users").child(m.senderId).child("name").get().addOnSuccessListener { nameSnapshot ->
                                val senderName = nameSnapshot.getValue(String::class.java) ?: m.senderId
                                val content = when {
                                    m.isDeleted -> "🚫 Mensagem apagada"
                                    m.imageUrl != null -> "📷 Foto"
                                    m.audioUrl != null -> "🎤 Áudio"
                                    else -> m.text
                                }
                                showNotification(senderName, content)
                            }
                        }
                    }
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Erro no listener: ${error.message}")
            }
        }
        
        database.child("messages")
            .orderByChild("timestamp")
            .startAt(startTime.toDouble())
            .addChildEventListener(globalListener!!)
    }

    private fun showNotification(title: String, message: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 
            System.currentTimeMillis().toInt(), 
            intent, 
            PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Notificação de Mensagem (Esta continua normal, com som e vibração)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setContentIntent(pendingIntent)

        val summaryBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Friend App")
            .setContentText("Novas mensagens")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.apply {
            notify(System.currentTimeMillis().toInt(), builder.build())
            notify(SUMMARY_ID, summaryBuilder.build())
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Canal de Mensagens (Importância Alta)
            val msgChannel = NotificationChannel(CHANNEL_ID, "Mensagens", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notificações de novas mensagens"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            
            // Canal Silencioso para o Serviço (Importância Mínima)
            // Isso evita o ícone no topo da barra de status
            val silentChannel = NotificationChannel(SILENT_CHANNEL_ID, "Serviço de Conexão", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Mantém o app conectado em segundo plano"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            
            notificationManager.createNotificationChannel(msgChannel)
            notificationManager.createNotificationChannel(silentChannel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        removeListener()
        super.onDestroy()
    }
}
