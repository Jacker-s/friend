package com.jack.friend

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class CallLog(
    var id: String = "",            // roomId
    var peerId: String = "",        // outro usuário (ou groupId futuramente)
    var peerName: String = "",      // nome cacheado
    var peerPhotoUrl: String? = null,
    var direction: String = "OUT",  // OUT | IN
    var type: String = "AUDIO",     // AUDIO | VIDEO
    var status: String = "RINGING", // RINGING | STARTING | ENDED | MISSED | CANCELED | REJECTED
    var timestamp: Long = 0L,
    var durationSec: Long? = null   // opcional (se você medir depois)
)
