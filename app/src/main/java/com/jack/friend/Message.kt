package com.jack.friend

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName
import java.io.Serializable

@IgnoreExtraProperties
data class LinkPreview(
    var url: String = "",
    var title: String? = null,
    var description: String? = null,
    var imageUrl: String? = null
) : Serializable

@IgnoreExtraProperties
data class Message(

    // =========================
    // Identificação básica
    // =========================
    var id: String = "",
    var senderId: String = "",
    var receiverId: String = "",
    var timestamp: Long = 0L,

    // =========================
    // Conteúdo principal
    // =========================
    var text: String = "",
    var imageUrl: String? = null,
    var videoUrl: String? = null,
    var videoThumbnailUrl: String? = null,
    var audioUrl: String? = null,
    var stickerUrl: String? = null,
    var audioDurationSeconds: Long? = null, // ✅ Duração do áudio

    // Campo genérico de mídia (fallback futuro)
    var isMedia: Boolean = false,
    var mediaUrl: String? = null,

    // Link Preview
    var linkPreview: LinkPreview? = null,

    // =========================
    // Estados da mensagem
    // =========================
    @get:PropertyName("isRead")
    @set:PropertyName("isRead")
    var isRead: Boolean = false,

    var isDeleted: Boolean = false,
    var isEdited: Boolean = false,
    var isForwarded: Boolean = false,
    var isStarred: Boolean = false,
    var isViewOnce: Boolean = false,

    @get:PropertyName("audioPlayed")
    @set:PropertyName("audioPlayed")
    var audioPlayed: Boolean = false,

    // =========================
    // Reply (resposta)
    // =========================
    var replyToId: String? = null,
    var replyToText: String? = null,
    var replyToName: String? = null,

    // =========================
    // Reações
    // =========================
    var reactions: Map<String, String>? = null, // userId -> emoji

    // =========================
    // Grupo
    // =========================
    var isGroup: Boolean = false,
    var senderName: String? = null,
    var senderPhotoUrl: String? = null,
    var isSticker: Boolean = false,

    // =========================
    // Chamadas
    // =========================
    var isCall: Boolean = false,
    var callRoomId: String? = null,
    var callType: String? = null,   // AUDIO | VIDEO
    var callStatus: String? = null, // STARTING | RINGING | ACCEPTED | ENDED

    // =========================
    // Expiração (mensagem temporária)
    // =========================
    var expiryTime: Long? = null,
    var tempDurationMillis: Long? = null,

    // =========================
    // Local-only (não vai para o Firebase)
    // =========================
    @get:Exclude
    @set:Exclude
    var localAudioPath: String? = null

) : Serializable {

    // ============================================================
    // 🔥 Helpers Profissionais (não quebram Firebase)
    // ============================================================

    @get:Exclude
    val isImage: Boolean
        get() = !imageUrl.isNullOrEmpty()

    @get:Exclude
    val isVideo: Boolean
        get() = !videoUrl.isNullOrEmpty()

    @get:Exclude
    val isAudio: Boolean
        get() = !audioUrl.isNullOrEmpty()

    @get:Exclude
    val isText: Boolean
        get() = text.isNotBlank()

    @get:Exclude
    val hasMedia: Boolean
        get() = isImage || isVideo || isAudio || !mediaUrl.isNullOrEmpty() || isSticker

    @get:Exclude
    val isExpired: Boolean
        get() = expiryTime != null && System.currentTimeMillis() > expiryTime!!

    @get:Exclude
    val safeText: String
        get() = when {
            isDeleted -> "Mensagem apagada"
            isImage -> "📷 Imagem"
            isVideo -> "📹 Vídeo"
            isAudio -> "🎤 Áudio"
            isSticker -> "Sticker"
            isCall -> if (callType == "VIDEO") "📹 Chamada de vídeo" else "📞 Chamada de áudio"
            else -> text
        }

    @get:Exclude
    val reactionCount: Int
        get() = reactions?.size ?: 0
}