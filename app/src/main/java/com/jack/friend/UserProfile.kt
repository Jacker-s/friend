package com.jack.friend

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName

@IgnoreExtraProperties
data class UserProfile(

    // =========================
    // Identidade
    // =========================
    var id: String = "",          // username (ex: JACKSON)
    var uid: String = "",         // firebase auth uid
    var name: String = "",        // nome exibido
    var photoUrl: String? = null, // avatar

    // =========================
    // Presença
    // =========================
    @get:PropertyName("isOnline")
    @set:PropertyName("isOnline")
    var isOnline: Boolean = false,

    var lastActive: Long = 0L,

    /**
     * Status "bio" do usuário (ex: "Olá! Estou usando o Friend.")
     */
    var status: String = "Olá! Estou usando o Friend.",

    /**
     * Online, Ocupado, Ausente, Invisível (você usa string, mantive)
     */
    var presenceStatus: String = "Online",

    // =========================
    // Privacidade
    // =========================
    var showLastSeen: Boolean = true,
    var showReadReceipts: Boolean = true,
    var showOnlineStatus: Boolean = true,
    var isHiddenFromSearch: Boolean = false,

    /**
     * Todos, Contatos
     */
    var allowAddGroups: String = "Todos"

) {

    // ============================================================
    // Helpers (não vão pro Firebase)
    // ============================================================

    @get:Exclude
    val displayName: String
        get() = name.ifBlank { id }

    @get:Exclude
    val hasPhoto: Boolean
        get() = !photoUrl.isNullOrBlank()

    /**
     * Se o usuário escolheu "Invisível", considere que não deve aparecer online.
     * (você já aplica isso no ViewModel, aqui é só helper)
     */
    @get:Exclude
    val isVisibleOnline: Boolean
        get() = showOnlineStatus && presenceStatus != "Invisível"

    /**
     * Alguns perfis podem não ter lastActive preenchido ainda.
     */
    @get:Exclude
    val lastActiveOrNull: Long?
        get() = lastActive.takeIf { it > 0L }
}
