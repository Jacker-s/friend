package com.jack.friend

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.app.RemoteInput
import com.google.firebase.database.FirebaseDatabase

class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val TAG = "ReplyReceiver"

        if (remoteInput != null) {
            val replyText = remoteInput.getCharSequence(FriendMessagingService.KEY_TEXT_REPLY)?.toString()
            val chatId = intent.getStringExtra("chatId") ?: return
            val isGroup = intent.getBooleanExtra("isGroup", false)
            val myUsername = intent.getStringExtra("senderName") ?: ""

            Log.d(TAG, "Resposta recebida: $replyText para $chatId")

            if (!replyText.isNullOrBlank()) {
                val db = FirebaseDatabase.getInstance().reference
                val msgId = db.push().key ?: return
                
                val msg = Message(
                    id = msgId,
                    senderId = myUsername,
                    receiverId = chatId,
                    text = replyText,
                    timestamp = System.currentTimeMillis(),
                    isGroup = isGroup
                )

                val path = if (isGroup) "group_messages/$chatId" else "messages/${chatPathFor(myUsername, chatId)}"
                
                db.child(path).child(msgId).setValue(msg).addOnSuccessListener {
                    Log.d(TAG, "Mensagem enviada via notificação com sucesso")
                    updateChatSummary(msg, myUsername)
                }.addOnFailureListener {
                    Log.e(TAG, "Erro ao enviar mensagem via notificação: ${it.message}")
                }

                // Notificar o sistema que a resposta foi processada (remove o ícone de carregamento na notificação)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(chatId.hashCode())
            }
        } else {
            Log.e(TAG, "RemoteInput é nulo")
        }
    }

    private fun chatPathFor(u1: String, u2: String): String {
        val user1 = u1.uppercase().trim()
        val user2 = u2.uppercase().trim()
        return if (user1 < user2) "${user1}_$user2" else "${user2}_$user1"
    }

    private fun updateChatSummary(msg: Message, me: String) {
        val db = FirebaseDatabase.getInstance().reference
        val friend = msg.receiverId
        val isGroup = msg.isGroup

        if (isGroup) {
            db.child("groups").child(friend).get().addOnSuccessListener { snapshot ->
                val group = snapshot.getValue(Group::class.java) ?: return@addOnSuccessListener
                val summary = ChatSummary(
                    friendId = friend,
                    lastMessage = "${msg.senderName ?: me}: ${msg.text}",
                    timestamp = msg.timestamp,
                    lastSenderId = me,
                    friendName = group.name,
                    friendPhotoUrl = group.photoUrl,
                    isGroup = true,
                    hasUnread = false
                )
                group.members.keys.forEach { memberUsername ->
                    db.child("chats").child(memberUsername).child(friend).setValue(summary.copy(hasUnread = memberUsername != me))
                }
            }
        } else {
            db.child("users").child(friend).get().addOnSuccessListener { snapshot ->
                val friendProf = snapshot.getValue(UserProfile::class.java)
                val summary = ChatSummary(
                    friendId = friend,
                    lastMessage = msg.text,
                    timestamp = msg.timestamp,
                    lastSenderId = me,
                    friendName = friendProf?.name ?: friend,
                    friendPhotoUrl = friendProf?.photoUrl,
                    isGroup = false,
                    isOnline = friendProf?.isOnline ?: false,
                    hasUnread = false,
                    presenceStatus = friendProf?.presenceStatus ?: "Online"
                )
                db.child("chats").child(me).child(friend).setValue(summary)
                
                db.child("users").child(me).get().addOnSuccessListener { meSnapshot ->
                    val meProf = meSnapshot.getValue(UserProfile::class.java)
                    db.child("chats").child(friend).child(me).setValue(summary.copy(
                        friendId = me,
                        friendName = meProf?.name ?: me,
                        friendPhotoUrl = meProf?.photoUrl,
                        hasUnread = true,
                        presenceStatus = meProf?.presenceStatus ?: "Online"
                    ))
                }
            }
        }
    }
}
