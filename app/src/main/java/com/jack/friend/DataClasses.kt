package com.jack.friend

import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName

// =========================
// Firebase Models (SEGUROS)
// =========================

@IgnoreExtraProperties
data class ChatSummary(
    var friendId: String = "",
    var lastMessage: String = "",
    var timestamp: Long = 0L,
    var hasUnread: Boolean = false,
    var lastSenderId: String = "",
    var lastMessageRead: Boolean = false,
    var friendName: String? = null,
    var friendPhotoUrl: String? = null,

    @get:PropertyName("isOnline")
    @set:PropertyName("isOnline")
    var isOnline: Boolean = false,

    var lastActive: Long = 0L,

    @get:PropertyName("isPinned")
    @set:PropertyName("isPinned")
    var isPinned: Boolean = false,

    @get:PropertyName("isMuted")
    @set:PropertyName("isMuted")
    var isMuted: Boolean = false,

    @get:PropertyName("isTyping")
    @set:PropertyName("isTyping")
    var isTyping: Boolean = false,

    @get:PropertyName("isEphemeral")
    @set:PropertyName("isEphemeral")
    var isEphemeral: Boolean = false,

    @get:PropertyName("isGroup")
    @set:PropertyName("isGroup")
    var isGroup: Boolean = false,

    var presenceStatus: String = "Online"
)

@IgnoreExtraProperties
data class UserStatus(
    var userId: String = "",

    @get:PropertyName("isOnline")
    @set:PropertyName("isOnline")
    var isOnline: Boolean = false,

    var lastActive: Long = 0L,
    var username: String = "",
    var imageUrl: String = "",
    var timestamp: Long = 0L,
    var userPhotoUrl: String? = null,
    var id: String = "",
    var viewers: Map<String, Long> = emptyMap() // userId -> timestamp
)

@IgnoreExtraProperties
data class Group(
    var id: String = "",
    var name: String = "",
    var photoUrl: String? = null,
    var description: String = "",
    var members: Map<String, Boolean> = emptyMap(), // username -> true
    var createdBy: String = "",
    var timestamp: Long = 0L
)

// =========================
// Helpers (Extensions) - NÃO QUEBRAM FIREBASE
// =========================

// ChatSummary helpers
val ChatSummary.displayName: String
    get() = friendName?.takeIf { it.isNotBlank() } ?: friendId

val ChatSummary.hasPhoto: Boolean
    get() = !friendPhotoUrl.isNullOrBlank()

val ChatSummary.isVisibleOnline: Boolean
    get() = isOnline && presenceStatus != "Invisível"

// UserStatus helpers
val UserStatus.viewerCount: Int
    get() = viewers.size

fun UserStatus.hasViewed(userId: String): Boolean = viewers.containsKey(userId)

val UserStatus.isExpired: Boolean
    get() = System.currentTimeMillis() - timestamp > 86_400_000L // 24h

// Group helpers
val Group.memberCount: Int
    get() = members.size

fun Group.isMember(username: String): Boolean = members.containsKey(username)

val Group.hasPhoto: Boolean
    get() = !photoUrl.isNullOrBlank()
