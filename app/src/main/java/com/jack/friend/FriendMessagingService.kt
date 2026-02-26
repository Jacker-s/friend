package com.jack.friend

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import java.net.HttpURLConnection
import java.net.URL

class FriendMessagingService : FirebaseMessagingService() {

    companion object {
        private const val CHANNEL_ID = "MESSAGES_CHANNEL_V24"
        private const val CALL_CHANNEL_ID = "CALL_CHANNEL_V24"
        private const val TAG = "FriendMessagingService"
        const val KEY_TEXT_REPLY = "key_text_reply"
        private const val PREFS_NAME = "friend_prefs"
        private const val KEY_MY_USERNAME = "cached_username"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        createChannels()

        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            val messageJson = data["message"]
            if (messageJson != null) {
                try {
                    val message = Gson().fromJson(messageJson, Message::class.java)
                    val isCall = data["type"] == "CALL" || (message.callType != null && message.callStatus == "STARTING")

                    if (isCall) {
                        handleIncomingCall(message)
                    } else {
                        val isChatOpen = FriendApplication.isAppInForeground && FriendApplication.currentOpenedChatId == message.senderId
                        
                        // Atualiza o resumo, mas define 'hasUnread' como falso se a conversa já estiver aberta
                        updateChatSummaryOnMessage(message, !isChatOpen)

                        if (isChatOpen) return
                        
                        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        val cachedUsername = prefs.getString(KEY_MY_USERNAME, null)
                        
                        // Processar em thread separada para permitir downloads sem travar o serviço
                        Thread {
                            showNotification(message, cachedUsername)
                        }.start()

                        if (cachedUsername == null) {
                            val uid = FirebaseAuth.getInstance().currentUser?.uid
                            if (uid != null) {
                                FirebaseDatabase.getInstance().reference.child("uid_to_username").child(uid).get()
                                    .addOnSuccessListener { snapshot ->
                                        snapshot.getValue(String::class.java)?.let {
                                            prefs.edit().putString(KEY_MY_USERNAME, it).apply()
                                        }
                                    }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing FCM: ${e.message}")
                }
            }
        }
    }

    private fun updateChatSummaryOnMessage(msg: Message, setAsUnread: Boolean) {
        val db = FirebaseDatabase.getInstance().reference
        val sender = msg.senderId
        val receiver = msg.receiverId
        
        val lastMsgText = when {
            msg.audioUrl != null -> "🎤 Áudio"
            msg.imageUrl != null -> "📷 Imagem"
            msg.videoUrl != null -> "📹 Vídeo"
            msg.stickerUrl != null -> "Sticker"
            else -> msg.text
        }

        // Atualiza o resumo para o receptor (eu)
        db.child("users").child(sender).get().addOnSuccessListener { snapshot ->
            val senderProfile = snapshot.getValue(UserProfile::class.java)
            val summary = ChatSummary(
                friendId = sender,
                lastMessage = lastMsgText,
                timestamp = msg.timestamp,
                lastSenderId = sender,
                friendName = senderProfile?.name ?: sender,
                friendPhotoUrl = senderProfile?.photoUrl,
                isGroup = false,
                isOnline = senderProfile?.isOnline ?: false,
                hasUnread = setAsUnread, 
                presenceStatus = senderProfile?.presenceStatus ?: "Online"
            )
            db.child("chats").child(receiver).child(sender).setValue(summary)
        }
    }

    private fun downloadBitmap(url: String?): Bitmap? {
        if (url.isNullOrEmpty()) return null
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000 
            connection.readTimeout = 5000
            connection.doInput = true
            connection.connect()
            BitmapFactory.decodeStream(connection.inputStream)
        } catch (e: Exception) {
            null
        }
    }

    private fun handleIncomingCall(message: Message) {
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra("callMessage", message)
            putExtra("isVideo", message.callType == "VIDEO")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (Settings.canDrawOverlays(this) || FriendApplication.isAppInForeground) {
            try { startActivity(intent) } catch (e: Exception) {}
        }
        showCallNotification(message)
    }

    private fun showCallNotification(message: Message) {
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra("callMessage", message)
            putExtra("isVideo", message.callType == "VIDEO")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreenPI = PendingIntent.getActivity(this, 1002, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CALL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(message.senderName ?: "Chamada")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPI, true)
            .setOngoing(true).setSilent(true)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1002, builder.build())
    }

    private fun showNotification(message: Message, myUsername: String?) {
        val chatId = if (message.isGroup) message.receiverId else message.senderId
        val senderName = message.senderName ?: "Wappi"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("targetId", chatId)
            putExtra("isGroup", message.isGroup)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, chatId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val userPerson = Person.Builder().setName(myUsername ?: "Eu").build()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setColor(0xFF007AFF.toInt())
            .setShortcutId(chatId) 
            .setLocusId(androidx.core.content.LocusIdCompat(chatId))

        // 1. Download IMEDIATO da imagem do remetente
        val senderBitmap = downloadBitmap(message.senderPhotoUrl)
        val senderIcon = if (senderBitmap != null) IconCompat.createWithBitmap(senderBitmap) else null
        
        val senderPerson = Person.Builder()
            .setName(senderName)
            .setIcon(senderIcon) 
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(userPerson)
            .setConversationTitle(if (message.isGroup) senderName else null)
            .setGroupConversation(message.isGroup)

        // 2. Download da imagem da mensagem ou texto alternativo para áudio
        val messageBitmap = if (message.isImage) downloadBitmap(message.imageUrl) else null
        val msgText = when {
            message.isImage -> "📷 Imagem"
            message.isAudio -> "🎤 Mensagem de áudio"
            message.isVideo -> "📹 Vídeo"
            message.isSticker -> "Sticker"
            else -> message.text
        }

        val notificationMessage = NotificationCompat.MessagingStyle.Message(msgText, message.timestamp, senderPerson)

        if (messageBitmap != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            notificationMessage.setData("image/", Uri.parse(message.imageUrl))
        }
        
        messagingStyle.addMessage(notificationMessage)
        builder.setStyle(messagingStyle)
        
        // Backup: Set LargeIcon para garantir visibilidade da foto em versões antigas
        if (senderBitmap != null) builder.setLargeIcon(senderBitmap)

        // Resposta rápida
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY).setLabel("Responder...").build()
        val replyIntent = Intent(this, ReplyReceiver::class.java).apply {
            putExtra("chatId", chatId)
            putExtra("senderName", myUsername ?: "")
            putExtra("isGroup", message.isGroup)
        }
        val replyPI = PendingIntent.getBroadcast(this, chatId.hashCode(), replyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        builder.addAction(NotificationCompat.Action.Builder(android.R.drawable.ic_menu_send, "Responder", replyPI).addRemoteInput(remoteInput).build())

        nm.notify(chatId.hashCode(), builder.build())
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Mensagens", NotificationManager.IMPORTANCE_HIGH))
            }
            if (nm.getNotificationChannel(CALL_CHANNEL_ID) == null) {
                nm.createNotificationChannel(NotificationChannel(CALL_CHANNEL_ID, "Chamadas", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(false)
                })
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().reference.child("uid_to_username").child(uid).get().addOnSuccessListener {
            it.getValue(String::class.java)?.let { user ->
                FirebaseDatabase.getInstance().reference.child("fcmTokens").child(user).child("token").setValue(token)
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_MY_USERNAME, user).apply()
            }
        }
    }
}
