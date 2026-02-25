package com.jack.friend

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.database.FirebaseDatabase

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val roomId = intent.getStringExtra("roomId") ?: return
        val targetId = intent.getStringExtra("targetId") ?: ""
        val callerName = intent.getStringExtra("callerName") ?: ""
        val isVideo = intent.getBooleanExtra("isVideo", false)
        val targetPhotoUrl = intent.getStringExtra("targetPhotoUrl")

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(1002) // Cancela a notificação de chamada

        if (action == "ACTION_REJECT") {
            // Atualiza o status para REJECTED no Firebase
            FirebaseDatabase.getInstance().reference
                .child("calls")
                .child(roomId)
                .child("status")
                .setValue("REJECTED")
        } else if (action == "ACTION_ACCEPT") {
            // Abre a CallActivity diretamente para conectar
            val acceptIntent = Intent(context, CallActivity::class.java).apply {
                putExtra("roomId", roomId)
                putExtra("targetId", targetId)
                putExtra("targetPhotoUrl", targetPhotoUrl)
                putExtra("isOutgoing", false)
                putExtra("isVideo", isVideo)
                putExtra("isAcceptedFromNotification", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(acceptIntent)
            
            // Fecha a IncomingCallActivity se ela estiver aberta (via Broadcast)
            val closeIntent = Intent("ACTION_CLOSE_INCOMING_CALL")
            context.sendBroadcast(closeIntent)
        }
    }
}
