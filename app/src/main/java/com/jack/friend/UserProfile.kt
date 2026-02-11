package com.jack.friend

import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName

@IgnoreExtraProperties
data class UserProfile(
    val id: String = "",
    val uid: String = "",
    val name: String = "",
    val photoUrl: String? = null,
    @get:PropertyName("isOnline")
    @set:PropertyName("isOnline")
    var isOnline: Boolean = false,
    var lastActive: Long = 0L,
    var status: String = "Olá! Estou usando o Friend.",
    var presenceStatus: String = "Online" // Online, Ocupado, Ausente, Invisível
)
