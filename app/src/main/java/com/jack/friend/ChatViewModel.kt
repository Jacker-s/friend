package com.jack.friend

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.*
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Salva/recupera emojis recentes (ex: para reações) usando SharedPreferences.
 * Depende do FriendApplication.instance já existir.
 */
object RecentEmojiStore {
    private const val PREFS = "recent_emojis_prefs"
    private const val KEY = "recent_emojis_list"
    private const val MAX = 24

    private fun prefs() =
        FriendApplication.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(): List<String> = try {
        val raw = prefs().getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val v = arr.optString(i, "")
                if (v.isNotBlank()) add(v)
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun add(emoji: String): List<String> {
        val clean = emoji.trim()
        if (clean.isEmpty()) return get()

        val current = get().toMutableList()
        current.removeAll { it == clean }
        current.add(0, clean)

        val trimmed = current.take(MAX)

        try {
            val arr = JSONArray()
            trimmed.forEach { arr.put(it) }
            prefs().edit().putString(KEY, arr.toString()).apply()
        } catch (_: Exception) {}

        return trimmed
    }

    fun clear() {
        try { prefs().edit().remove(KEY).apply() } catch (_: Exception) {}
    }
}

class ChatViewModel : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"

        private const val RECORD_MIN_MS = 800L
        private const val TIMER_TICK_MS = 100L
        private const val STATUS_TTL_MS = 86_400_000L // 24h

        private const val FOLDER_PROFILES = "profiles"
        private const val FOLDER_CHAT_IMAGES = "chat_images"
        private const val FOLDER_CHAT_VIDEOS = "chat_videos"
        private const val FOLDER_STATUSES = "statuses"
    }

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private val storage = FirebaseStorage.getInstance().reference

    private val errorHandler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, "Coroutine error: ${t.message}", t)
    }
    // ---------------------------
// Call logs
// ---------------------------
    private val _callLogs = MutableStateFlow<List<CallLog>>(emptyList())
    val callLogs: StateFlow<List<CallLog>> = _callLogs

    private var callLogsListener: ValueEventListener? = null

    // ---------------------------
    // StateFlows (UI state)
    // ---------------------------
    private val _isUserLoggedIn = MutableStateFlow(auth.currentUser != null)
    val isUserLoggedIn: StateFlow<Boolean> = _isUserLoggedIn

    private val _myUsername = MutableStateFlow("")
    val myUsername: StateFlow<String> = _myUsername

    private val _myId = MutableStateFlow(auth.currentUser?.uid ?: "")
    val myId: StateFlow<String> = _myId

    private val _myName = MutableStateFlow("")
    val myName: StateFlow<String> = _myName

    private val _myPhotoUrl = MutableStateFlow<String?>(null)
    val myPhotoUrl: StateFlow<String?> = _myPhotoUrl

    private val _myStatus = MutableStateFlow("")
    val myStatus: StateFlow<String> = _myStatus

    private val _myPresenceStatus = MutableStateFlow("Online")
    val myPresenceStatus: StateFlow<String> = _myPresenceStatus

    private val _recentEmojis = MutableStateFlow<List<String>>(emptyList())
    val recentEmojis: StateFlow<List<String>> = _recentEmojis

    private val _showLastSeen = MutableStateFlow(true)
    val showLastSeen: StateFlow<Boolean> = _showLastSeen

    private val _showReadReceipts = MutableStateFlow(true)
    val showReadReceipts: StateFlow<Boolean> = _showReadReceipts

    private val _showOnlineStatus = MutableStateFlow(true)
    val showOnlineStatus: StateFlow<Boolean> = _showOnlineStatus

    private val _isHiddenFromSearch = MutableStateFlow(false)
    val isHiddenFromSearch: StateFlow<Boolean> = _isHiddenFromSearch

    private val _isScreenshotDisabled = MutableStateFlow(false)
    val isScreenshotDisabled: StateFlow<Boolean> = _isScreenshotDisabled

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _activeChats = MutableStateFlow<List<ChatSummary>>(emptyList())
    val activeChats: StateFlow<List<ChatSummary>> = _activeChats

    private val _contacts = MutableStateFlow<List<UserProfile>>(emptyList())
    val contacts: StateFlow<List<UserProfile>> = _contacts

    private val _targetId = MutableStateFlow("")
    val targetId: StateFlow<String> = _targetId

    private val _targetProfile = MutableStateFlow<UserProfile?>(null)
    val targetProfile: StateFlow<UserProfile?> = _targetProfile

    private val _pinnedMessage = MutableStateFlow<Message?>(null)
    val pinnedMessage: StateFlow<Message?> = _pinnedMessage

    private val _isTargetTyping = MutableStateFlow(false)
    val isTargetTyping: StateFlow<Boolean> = _isTargetTyping

    private val _searchResults = MutableStateFlow<List<UserProfile>>(emptyList())
    val searchResults: StateFlow<List<UserProfile>> = _searchResults

    private val _statuses = MutableStateFlow<List<UserStatus>>(emptyList())
    val statuses: StateFlow<List<UserStatus>> = _statuses

    private val _blockedUsers = MutableStateFlow<List<String>>(emptyList())
    val blockedUsers: StateFlow<List<String>> = _blockedUsers

    private val _blockedProfiles = MutableStateFlow<List<UserProfile>>(emptyList())
    val blockedProfiles: StateFlow<List<UserProfile>> = _blockedProfiles

    private val _localMedia = MutableStateFlow<List<LocalMedia>>(emptyList())
    val localMedia: StateFlow<List<LocalMedia>> = _localMedia

    // Compartilhamento externo
    private val _pendingSharedMedia = MutableStateFlow<List<Uri>>(emptyList())
    val pendingSharedMedia: StateFlow<List<Uri>> = _pendingSharedMedia

    private val _pendingSharedText = MutableStateFlow<String?>(null)
    val pendingSharedText: StateFlow<String?> = _pendingSharedText

    // ---------------------------
    // Listeners / Jobs / Timers
    // ---------------------------
    private var chatsListener: ValueEventListener? = null
    private var contactsListener: ValueEventListener? = null
    private var messagesListener: ValueEventListener? = null
    private var targetProfileListener: ValueEventListener? = null
    private var statusesListener: ValueEventListener? = null
    private var pinnedMessageListener: ValueEventListener? = null
    private var blockedListener: ValueEventListener? = null
    private var currentChatPath: String? = null

    private val presenceListeners = mutableMapOf<String, ValueEventListener>()
    private val typingListeners = mutableMapOf<String, ValueEventListener>()

    private var searchJob: Job? = null
    private var presenceJob: Job? = null
    private var ephemeralCleanupJob: Job? = null
    private var connectedListener: ValueEventListener? = null

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var recordingStartTime: Long = 0L

    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration

    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    init {
        loadRecentEmojis()
        if (auth.currentUser != null) setupUserSession()
    }

    override fun onCleared() {
        super.onCleared()
        removeListeners()
    }


    private fun listenToCallLogs(username: String) {
        callLogsListener?.let { db.child("call_logs").child(username).removeEventListener(it) }

        callLogsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { it.getValue(CallLog::class.java) }
                    .sortedByDescending { it.timestamp }
                _callLogs.value = list
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        db.child("call_logs").child(username)
            .limitToLast(200)
            .addValueEventListener(callLogsListener!!)
    }

    // ---------------------------
    // Helpers (paths / logging)
    // ---------------------------
    private fun logE(msg: String, t: Throwable? = null) {
        if (t != null) Log.e(TAG, msg, t) else Log.e(TAG, msg)
    }

    private fun chatKey(u1: String, u2: String): String {
        val a = u1.uppercase().trim()
        val b = u2.uppercase().trim()
        return if (a < b) "${a}_$b" else "${b}_$a"
    }

    private fun messagePath(me: String, target: String): String =
        "messages/${chatKey(me, target)}"

    private fun safeMe(): String = _myUsername.value
    private fun safeTarget(): String = _targetId.value

    // ---------------------------
    // Recent emojis
    // ---------------------------
    fun loadRecentEmojis() {
        _recentEmojis.value = RecentEmojiStore.get()
    }

    private fun pushRecentEmoji(emoji: String) {
        _recentEmojis.value = RecentEmojiStore.add(emoji)
    }

    fun clearRecentEmojis() {
        RecentEmojiStore.clear()
        _recentEmojis.value = emptyList()
    }

    // ---------------------------
    // Session
    // ---------------------------
    private fun setupUserSession() {
        val uid = auth.currentUser?.uid ?: return
        _myId.value = uid

        db.child("uid_to_username").child(uid).get()
            .addOnSuccessListener { snapshot ->
                val username = snapshot.getValue(String::class.java).orEmpty()
                if (username.isNotEmpty()) {
                    _myUsername.value = username
                    loadMyProfile(username)
                    listenToBlockedUsers(username)
                    listenToChats(username)
                    listenToCallLogs(username)
                    listenToContacts(username)
                    listenToStatuses(username)
                    setupPresence(username)
                    updateFcmToken(username)

                } else {
                    logE("Username não encontrado para o UID: $uid")
                }
            }
            .addOnFailureListener { logE("Erro setupUserSession: ${it.message}", it) }
    }

    private fun updateFcmToken(username: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                db.child("fcmTokens").child(username).child("token").setValue(task.result)
            } else {
                logE("Falha ao obter token FCM: ${task.exception?.message}")
            }
        }
    }

    private fun loadMyProfile(username: String) {
        db.child("users").child(username).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val profile = snapshot.getValue(UserProfile::class.java)
                _myName.value = profile?.name ?: username
                _myPhotoUrl.value = profile?.photoUrl
                _myStatus.value = profile?.status ?: "Olá! Estou usando o Friend."
                _myPresenceStatus.value = profile?.presenceStatus ?: "Online"
                _showLastSeen.value = profile?.showLastSeen ?: true
                _showReadReceipts.value = profile?.showReadReceipts ?: true
                _showOnlineStatus.value = profile?.showOnlineStatus ?: true
                _isHiddenFromSearch.value = profile?.isHiddenFromSearch ?: false
            }

            override fun onCancelled(error: DatabaseError) {
                logE("loadMyProfile cancelled: ${error.message}")
            }
        })
    }

    private fun setupPresence(username: String) {
        presenceJob?.cancel()
        connectedListener?.let { db.child(".info/connected").removeEventListener(it) }

        val statusRef = db.child("users").child(username).child("isOnline")
        val lastActiveRef = db.child("users").child(username).child("lastActive")

        connectedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.getValue(Boolean::class.java) == true) {
                    statusRef.onDisconnect().setValue(false)
                    lastActiveRef.onDisconnect().setValue(ServerValue.TIMESTAMP)
                    updatePresence(FriendApplication.instance.isForeground.value)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child(".info/connected").addValueEventListener(connectedListener!!)

        presenceJob = viewModelScope.launch(errorHandler) {
            FriendApplication.instance.isForeground.collect { updatePresence(it) }
        }
    }

    fun logout() {
        val user = auth.currentUser
        val username = safeMe()
        if (username.isNotEmpty()) {
            db.child("users").child(username).child("isOnline").setValue(false)
            db.child("users").child(username).child("lastActive").setValue(ServerValue.TIMESTAMP)
            // Remove FCM token apenas se o usuário está realmente fazendo logout e não sendo excluído
            // A exclusão do token para o cenário de exclusão de conta será tratada em deleteAccount
            db.child("fcmTokens").child(username).removeValue()
        }

        removeListeners()

        callLogsListener?.let { l ->
            if (username.isNotEmpty()) db.child("call_logs").child(username).removeEventListener(l)
        }
        callLogsListener = null

        auth.signOut()

        _isUserLoggedIn.value = false
        _myUsername.value = ""
        _activeChats.value = emptyList()
        _messages.value = emptyList()
        _targetId.value = ""
        _targetProfile.value = null
        _pinnedMessage.value = null
        _isTargetTyping.value = false
        _myId.value = "" // Limpar também o myId
        _myName.value = ""
        _myPhotoUrl.value = null
        _myStatus.value = ""
        _myPresenceStatus.value = "Online"
        _recentEmojis.value = emptyList()
        _showLastSeen.value = true
        _showReadReceipts.value = true
        _showOnlineStatus.value = true
        _isHiddenFromSearch.value = false
        _isScreenshotDisabled.value = false
        _contacts.value = emptyList()
        _searchResults.value = emptyList()
        _statuses.value = emptyList()
        _blockedUsers.value = emptyList()
        _blockedProfiles.value = emptyList()
    }

    private fun removeListeners() {
        val me = safeMe()

        if (me.isNotEmpty()) {
            chatsListener?.let { db.child("chats").child(me).removeEventListener(it) }
            contactsListener?.let { db.child("contacts").child(me).removeEventListener(it) }
            blockedListener?.let { db.child("blocks").child(me).removeEventListener(it) }
            statusesListener?.let { db.child("status").removeEventListener(it) }
        }

        currentChatPath?.let { path ->
            messagesListener?.let { db.child(path).removeEventListener(it) }
        }

        pinnedMessageListener?.let { l ->
            val key = chatKey(me, safeTarget())
            db.child("pinned").child(key).removeEventListener(l)
        }

        targetProfileListener?.let { l ->
            val t = safeTarget()
            if (t.isNotEmpty()) db.child("users").child(t).removeEventListener(l)
        }

        presenceListeners.forEach { (id, listener) ->
            db.child("users").child(id).removeEventListener(listener)
        }
        presenceListeners.clear()

        typingListeners.forEach { (friendId, listener) ->
            val key = chatKey(me, friendId)
            db.child("typing").child(key).child(friendId).removeEventListener(listener)
        }
        typingListeners.clear()

        connectedListener?.let { db.child(".info/connected").removeEventListener(it) }
        connectedListener = null
        presenceJob?.cancel()
        presenceJob = null
        ephemeralCleanupJob?.cancel()
        ephemeralCleanupJob = null

        stopTimer()
        releaseRecorderSafely()
    }

    // ---------------------------\
    // Target
    // ---------------------------\
    fun setTargetId(id: String) {
        val me = safeMe()
        if (_targetId.value == id && id.isNotEmpty()) return

        // limpar alvo anterior
        val oldTarget = _targetId.value
        if (oldTarget.isNotEmpty() && me.isNotEmpty()) {
            val oldKey = chatKey(me, oldTarget)
            db.child("typing").child(oldKey).child(me).setValue(false)
        }

        targetProfileListener?.let { l ->
            if (oldTarget.isNotEmpty()) db.child("users").child(oldTarget).removeEventListener(l)
        }
        targetProfileListener = null

        pinnedMessageListener?.let { l ->
            val oldKey = chatKey(me, oldTarget)
            db.child("pinned").child(oldKey).removeEventListener(l)
        }
        pinnedMessageListener = null

        currentChatPath?.let { oldPath ->
            messagesListener?.let { l -> db.child(oldPath).removeEventListener(l) }
        }
        messagesListener = null
        currentChatPath = null
        ephemeralCleanupJob?.cancel()
        ephemeralCleanupJob = null

        _targetId.value = id

        if (id.isBlank()) {
            _targetProfile.value = null
            _messages.value = emptyList()
            _isTargetTyping.value = false
            _pinnedMessage.value = null
            _isScreenshotDisabled.value = false
            return
        }

        _isTargetTyping.value = _activeChats.value.find { it.friendId == id }?.isTyping ?: false

        // pinned
        val pinKey = chatKey(me, id)
        pinnedMessageListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _pinnedMessage.value = snapshot.getValue(Message::class.java)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("pinned").child(pinKey).addValueEventListener(pinnedMessageListener!!)

        // perfil
        targetProfileListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _targetProfile.value = snapshot.getValue(UserProfile::class.java)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("users").child(id).addValueEventListener(targetProfileListener!!)

        // Monitorar Screenshot Disabled do outro lado
        db.child("chats").child(id).child(me).child("isScreenshotDisabled").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _isScreenshotDisabled.value = snapshot.getValue(Boolean::class.java) ?: false
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        listenToMessages(id)
    }

    private fun syncFriendPresence(friendUsername: String) {
        if (presenceListeners.containsKey(friendUsername)) {
            db.child("users").child(friendUsername).child("isOnline").get().addOnSuccessListener { snap ->
                val online = snap.getValue(Boolean::class.java) ?: false
                updateLocalChatPresence(friendUsername, online)
            }
            return
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(UserProfile::class.java)
                val isOnline = user?.isOnline ?: false
                val presenceStatus = user?.presenceStatus ?: "Online"
                updateLocalChatPresence(friendUsername, isOnline, presenceStatus)
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        presenceListeners[friendUsername] = listener
        db.child("users").child(friendUsername).addValueEventListener(listener)
    }

    private fun updateLocalChatPresence(friendId: String, isOnline: Boolean, status: String? = null) {
        _activeChats.value = _activeChats.value.map {
            if (it.friendId == friendId) {
                it.copy(isOnline = isOnline, presenceStatus = status ?: it.presenceStatus)
            } else it
        }
        _contacts.value = _contacts.value.map {
            if (it.id == friendId) {
                it.copy(isOnline = isOnline, presenceStatus = status ?: it.presenceStatus)
            } else it
        }
    }

    private fun syncTypingStatus(friendUsername: String) {
        if (typingListeners.containsKey(friendUsername)) return
        val me = safeMe()
        if (me.isEmpty()) return

        val key = chatKey(me, friendUsername)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isTyping = snapshot.getValue(Boolean::class.java) ?: false

                _activeChats.value = _activeChats.value.map {
                    if (it.friendId == friendUsername) it.copy(isTyping = isTyping) else it
                }
                if (friendUsername == _targetId.value) _isTargetTyping.value = isTyping
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        typingListeners[friendUsername] = listener
        val ref = db.child("typing").child(key).child(friendUsername)
        ref.addValueEventListener(listener)
    }

    fun setTyping(isTyping: Boolean) {
        val me = safeMe()
        val target = safeTarget()
        if (me.isEmpty() || target.isEmpty()) return

        val key = chatKey(me, target)
        val typingRef = db.child("typing").child(key).child(me)
        typingRef.setValue(isTyping)
        if (isTyping) typingRef.onDisconnect().setValue(false)
    }

    // ---------------------------\\
    // Send message / sticker / reactions
    // ---------------------------\\
    fun sendMessage(text: String, tempDurationMillis: Long = 0, replyingTo: Message? = null) {
        val me = safeMe()
        val target = safeTarget()
        if (me.isEmpty() || target.isEmpty()) return
        if (_blockedUsers.value.contains(target)) return

        val msgId = db.push().key ?: return
        val msg = Message(
            id = msgId,
            senderId = me,
            receiverId = target,
            text = text,
            timestamp = System.currentTimeMillis(),
            isGroup = false,
            senderName = _myName.value,
            replyToId = replyingTo?.id,
            replyToText = replyingTo?.text
                ?: if (replyingTo?.imageUrl != null) "📷 Imagem"
                else if (replyingTo?.audioUrl != null) "🎤 Áudio"
                else null,
            replyToName = replyingTo?.senderName ?: replyingTo?.senderId,
            tempDurationMillis = if (tempDurationMillis > 0) tempDurationMillis else null
        )

        // Verificação de link
        val url = extractUrl(text)
        if (url != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val preview = fetchLinkPreview(url)
                withContext(Dispatchers.Main) {
                    msg.linkPreview = preview
                    sendMessageObject(msg)
                }
            }
        } else {
            sendMessageObject(msg)
        }
        
        setTyping(false)
    }

    private fun extractUrl(text: String): String? {
        val matcher = Patterns.WEB_URL.matcher(text)
        return if (matcher.find()) matcher.group() else null
    }

    private suspend fun fetchLinkPreview(url: String): LinkPreview? = withContext(Dispatchers.IO) {
        try {
            val response = Jsoup.connect(url).timeout(5000).get()
            val title = response.select("meta[property=og:title]").attr("content").takeIf { it.isNotEmpty() }
                ?: response.title()
            val description = response.select("meta[property=og:description]").attr("content").takeIf { it.isNotEmpty() }
                ?: response.select("meta[name=description]").attr("content")
            val image = response.select("meta[property=og:image]").attr("content").takeIf { it.isNotEmpty() }
            
            LinkPreview(url = url, title = title, description = description, imageUrl = image)
        } catch (e: Exception) {
            null
        }
    }

    fun sendSticker(stickerUrl: String, replyingTo: Message? = null) {
        val me = safeMe()
        val target = safeTarget()
        if (me.isEmpty() || target.isEmpty()) return
        if (_blockedUsers.value.contains(target)) return

        val msgId = db.push().key ?: return
        val msg = Message(
            id = msgId,
            senderId = me,
            receiverId = target,
            stickerUrl = stickerUrl,
            isSticker = true,
            timestamp = System.currentTimeMillis(),
            isGroup = false,
            senderName = _myName.value,
            replyToId = replyingTo?.id,
            replyToText = replyingTo?.text
                ?: if (replyingTo?.imageUrl != null) "📷 Imagem"
                else if (replyingTo?.audioUrl != null) "🎤 Áudio"
                else if (replyingTo?.stickerUrl != null) "Sticker" else null,
            replyToName = replyingTo?.senderName ?: replyingTo?.senderId
        )

        sendMessageObject(msg)
        setTyping(false)
    }

    fun editMessage(message: Message, newText: String) {
        val me = safeMe()
        if (me != message.senderId) return

        val path = "messages/${chatKey(message.senderId, message.receiverId)}"

        val updates = mutableMapOf<String, Any?>("text" to newText, "isEdited" to true)
        
        // Se o novo texto tiver link, atualizar o preview
        val url = extractUrl(newText)
        if (url != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val preview = fetchLinkPreview(url)
                withContext(Dispatchers.Main) {
                    updates["linkPreview"] = preview
                    db.child(path).child(message.id).updateChildren(updates)
                }
            }
        } else {
            updates["linkPreview"] = null
            db.child(path).child(message.id).updateChildren(updates)
        }
    }

    fun pinMessage(message: Message) {
        val key = chatKey(message.senderId, message.receiverId)
        db.child("pinned").child(key).setValue(message)
    }

    fun unpinMessage() {
        val me = safeMe()
        val target = safeTarget()
        val key = chatKey(me, target)
        db.child("pinned").child(key).removeValue()
    }

    fun addReaction(message: Message, emoji: String) {
        val me = safeMe()
        if (me.isEmpty()) return
        pushRecentEmoji(emoji)

        val path = "messages/${chatKey(message.senderId, message.receiverId)}"

        db.child(path).child(message.id).child("reactions").child(me).setValue(emoji)
    }

    fun markAsRead() {
        val me = safeMe()
        val target = safeTarget()
        if (me.isEmpty() || target.isEmpty()) return

        val path = "messages/${chatKey(me, target)}"
        db.child(path).orderByChild("receiverId").equalTo(me).get()
            .addOnSuccessListener { snapshot ->
                snapshot.children.forEach {
                    val isRead = it.child("isRead").getValue(Boolean::class.java) ?: false
                    if (!isRead) {
                        val updates = mutableMapOf<String, Any?>("isRead" to true)
                        
                        val tempDuration = it.child("tempDurationMillis").getValue(Long::class.java)
                        val expiryTime = it.child("expiryTime").getValue(Long::class.java)
                        
                        if (tempDuration != null && tempDuration > 0 && expiryTime == null) {
                            updates["expiryTime"] = System.currentTimeMillis() + tempDuration
                        }
                        
                        it.ref.updateChildren(updates)
                    }
                }
            }
        db.child("chats").child(me).child(target).child("hasUnread").setValue(false)
    }

    fun markAudioAsPlayed(messageId: String) {
        val me = safeMe()
        val target = safeTarget()
        if (me.isEmpty() || target.isEmpty()) return

        val path = "messages/${chatKey(me, target)}"
        db.child(path).child(messageId).child("audioPlayed").setValue(true)
    }

    // ---------------------------\\
    // Audio recording + upload
    // ---------------------------\\
    fun startRecording(cacheDir: File) {
        try {
            audioFile = File.createTempFile("audio_", ".m4a", cacheDir)
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            recordingStartTime = System.currentTimeMillis()
            _recordingDuration.value = 0L
            startTimer()
        } catch (e: Exception) {
            logE("Start recording error: ${e.message}", e)
            releaseRecorderSafely()
            audioFile?.delete()
            audioFile = null
        }
    }

    private fun startTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                _recordingDuration.value = System.currentTimeMillis() - recordingStartTime
                timerHandler.postDelayed(this, TIMER_TICK_MS)
            }
        }
        timerHandler.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        timerRunnable = null
        _recordingDuration.value = 0L
    }

    private fun releaseRecorderSafely() {
        try { mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null
    }

    fun stopRecording(tempDurationMillis: Long = 0, cancel: Boolean = false) {
        stopTimer()
        val duration = System.currentTimeMillis() - recordingStartTime

        try { mediaRecorder?.stop() } catch (e: Exception) { logE("Stop recording failed: ${e.message}", e) }
        finally { releaseRecorderSafely() }

        if (cancel || duration < RECORD_MIN_MS) {
            audioFile?.delete()
            audioFile = null
            return
        }

        audioFile?.let { uploadAudio(it, tempDurationMillis) }
    }

    private suspend fun uploadToCloudinary(uri: Uri, folder: String, resourceType: String = "auto"): String? =
        suspendCoroutine { continuation ->
            MediaManager.get().upload(uri)
                .option("folder", folder)
                .option("resource_type", resourceType)
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {}
                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        continuation.resume(resultData["secure_url"] as? String)
                    }
                    override fun onError(requestId: String, error: ErrorInfo) {
                        logE("Cloudinary upload error: ${error.description}")
                        continuation.resume(null)
                    }
                    override fun onReschedule(requestId: String, error: ErrorInfo) {
                        continuation.resume(null)
                    }
                }).dispatch()
        }

    private fun uploadAudio(file: File, tempDurationMillis: Long) {
        val me = safeMe()
        val target = safeTarget()
        if (me.isEmpty() || target.isEmpty()) return

        viewModelScope.launch(errorHandler) {
            try {
                val durationMs = getAudioDuration(file)
                val audioRef = storage.child("audios/${UUID.randomUUID()}.m4a")
                audioRef.putFile(Uri.fromFile(file)).await()
                val url = audioRef.downloadUrl.await().toString()

                val msgId = db.push().key ?: return@launch
                val msg = Message(
                    id = msgId,
                    senderId = me,
                    receiverId = target,
                    audioUrl = url,
                    audioDurationSeconds = durationMs / 1000,
                    timestamp = System.currentTimeMillis(),
                    isGroup = false,
                    senderName = _myName.value,
                    tempDurationMillis = if (tempDurationMillis > 0) tempDurationMillis else null
                )
                msg.localAudioPath = file.absolutePath
                sendMessageObject(msg)
            } catch (e: Exception) {
                logE("Upload áudio Firebase falhou: ${e.message}", e)
            }
        }
    }

    private fun getAudioDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    fun uploadImage(uri: Uri, tempDurationMillis: Long = 0) {
        val me = safeMe()
        val target = safeTarget()
        if (me.isEmpty() || target.isEmpty()) return

        viewModelScope.launch(errorHandler) {
            try {
                val url = uploadToCloudinary(uri, FOLDER_CHAT_IMAGES, "image")
                if (url != null) {
                    val msgId = db.push().key ?: return@launch
                    sendMessageObject(
                        Message(
                            id = msgId,
                            senderId = me,
                            receiverId = target,
                            imageUrl = url,
                            timestamp = System.currentTimeMillis(),
                            isGroup = false,
                            senderName = _myName.value,
                            tempDurationMillis = if (tempDurationMillis > 0) tempDurationMillis else null
                        )
                    )
                }
            } catch (e: Exception) {
                logE("uploadImage error: ${e.message}", e)
            }
        }
    }

    fun uploadVideo(uri: Uri, tempDurationMillis: Long = 0) {
        val me = safeMe()
        val target = safeTarget()
        if (me.isEmpty() || target.isEmpty()) return

        viewModelScope.launch(errorHandler) {
            try {
                val url = uploadToCloudinary(uri, FOLDER_CHAT_VIDEOS, "video")
                if (url != null) {
                    val msgId = db.push().key ?: return@launch
                    sendMessageObject(
                        Message(
                            id = msgId,
                            senderId = me,
                            receiverId = target,
                            videoUrl = url,
                            timestamp = System.currentTimeMillis(),
                            isGroup = false,
                            senderName = _myName.value,
                            tempDurationMillis = if (tempDurationMillis > 0) tempDurationMillis else null
                        )
                    )
                }
            } catch (e: Exception) {
                logE("uploadVideo error: ${e.message}", e)
            }
        }
    }

    // ---------------------------\
    // Persist message + summaries
    // ---------------------------\
    private fun sendMessageObject(msg: Message) {
        viewModelScope.launch(errorHandler) {
            try {
                if (_blockedUsers.value.contains(msg.receiverId)) return@launch
                db.child("messages/${chatKey(msg.senderId, msg.receiverId)}").child(msg.id).setValue(msg)
                updateChatSummary(msg)
            } catch (e: Exception) {
                logE("Erro ao enviar mensagem: ${e.message}", e)
            }
        }
    }

    private fun updateChatSummary(msg: Message) {
        viewModelScope.launch(errorHandler) {
            try {
                val me = msg.senderId
                val friend = msg.receiverId

                val lastMsgText = when {
                    msg.audioUrl != null -> "🎤 Áudio"
                    msg.imageUrl != null -> "📷 Imagem"
                    msg.videoUrl != null -> "📹 Vídeo"
                    msg.stickerUrl != null -> "Sticker"
                    else -> msg.text
                }

                val friendProf = db.child("users").child(friend).get().await().getValue(UserProfile::class.java)

                val summarySnap = db.child("chats").child(me).child(friend).get().await()
                val existingSummary = summarySnap.getValue(ChatSummary::class.java)

                val summary = ChatSummary(
                    friendId = friend,
                    lastMessage = lastMsgText,
                    timestamp = msg.timestamp,
                    lastSenderId = me,
                    friendName = friendProf?.name ?: friend,
                    friendPhotoUrl = friendProf?.photoUrl,
                    isGroup = false,
                    isOnline = friendProf?.isOnline ?: false,
                    hasUnread = false,
                    presenceStatus = friendProf?.presenceStatus ?: "Online",
                    isPinned = existingSummary?.isPinned ?: false,
                    isMuted = existingSummary?.isMuted ?: false,
                    isEphemeral = existingSummary?.isEphemeral ?: false,
                    tempDuration = existingSummary?.tempDuration ?: 0L
                )

                db.child("chats").child(me).child(friend).setValue(summary)

                val meProf = db.child("users").child(me).get().await().getValue(UserProfile::class.java)
                
                val friendSummarySnap = db.child("chats").child(friend).child(me).get().await()
                val existingFriendSummary = friendSummarySnap.getValue(ChatSummary::class.java)

                db.child("chats").child(friend).child(me).setValue(
                    summary.copy(
                        friendId = me,
                        friendName = meProf?.name ?: me,
                        friendPhotoUrl = meProf?.photoUrl,
                        hasUnread = true,
                        presenceStatus = meProf?.presenceStatus ?: "Online",
                        isPinned = existingFriendSummary?.isPinned ?: false,
                        isMuted = existingFriendSummary?.isMuted ?: false,
                        isEphemeral = existingFriendSummary?.isEphemeral ?: false,
                        tempDuration = existingFriendSummary?.tempDuration ?: 0L
                    )
                )
            } catch (e: Exception) {
                logE("Erro ao atualizar resumo: ${e.message}", e)
            }
        }
    }

    // ---------------------------\
    // Presence
    // ---------------------------\
    fun updatePresence(online: Boolean) {
        val user = safeMe()
        if (user.isEmpty()) return

        val isVisible = _myPresenceStatus.value != "Invisível" && _showOnlineStatus.value
        db.child("users").child(user).child("isOnline").setValue(online && isVisible)

        if (!online) {
            db.child("users").child(user).child("lastActive").setValue(ServerValue.TIMESTAMP)
        }
    }

    // ---------------------------\
    // Chats / Contacts listeners
    // ---------------------------\
    private fun listenToChats(username: String) {
        chatsListener?.let { db.child("chats").child(username).removeEventListener(it) }

        chatsListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val blocked = _blockedUsers.value.toSet()
                val incomingChats = s.children.mapNotNull { it.getValue(ChatSummary::class.java) }
                    .filter { it.friendId.isNotBlank() && !blocked.contains(it.friendId) }
                    .sortedWith(compareByDescending<ChatSummary> { it.isPinned }.thenByDescending { it.timestamp })

                val currentChats = _activeChats.value
                val mergedChats = incomingChats.map { newChat ->
                    val existing = currentChats.find { it.friendId == newChat.friendId }
                    if (existing != null) {
                        newChat.copy(
                            isOnline = existing.isOnline,
                            presenceStatus = existing.presenceStatus,
                            isTyping = existing.isTyping
                        )
                    } else {
                        newChat
                    }
                }

                _activeChats.value = mergedChats

                mergedChats.forEach { chat ->
                    syncFriendPresence(chat.friendId)
                    syncTypingStatus(chat.friendId)
                }
            }
            override fun onCancelled(e: DatabaseError) {}
        }

        db.child("chats").child(username).addValueEventListener(chatsListener!!)
    }

    private fun listenToContacts(username: String) {
        contactsListener?.let { db.child("contacts").child(username).removeEventListener(it) }

        contactsListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val ids = s.children.mapNotNull { it.key }
                val blocked = _blockedUsers.value.toSet()

                viewModelScope.launch(errorHandler) {
                    try {
                        val profiles = ids.filter { it.isNotBlank() && !blocked.contains(it) }
                            .mapNotNull { id -> db.child("users").child(id).get().await().getValue(UserProfile::class.java) }

                        _contacts.value = profiles
                        profiles.forEach { syncFriendPresence(it.id) }
                    } catch (_: Exception) {}
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        db.child("contacts").child(username).addValueEventListener(contactsListener!!)
    }

    fun addContact(targetUsername: String, callback: (Boolean, String?) -> Unit) {
        val me = safeMe()
        val cleanTarget = if (targetUsername.startsWith("@")) targetUsername.substring(1) else targetUsername
        val target = cleanTarget.uppercase().trim()

        if (me.isEmpty()) return callback(false, "Usuário não autenticado")
        if (me == target) return callback(false, "Não pode adicionar a si mesmo")

        db.child("users").child(target).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                db.child("contacts").child(me).child(target).setValue(true).addOnCompleteListener { task ->
                    if (task.isSuccessful) callback(true, null) else callback(false, task.exception?.message)
                }
            } else callback(false, "Usuário não encontrado")
        }.addOnFailureListener { callback(false, it.message) }
    }

    fun deleteContact(targetId: String, callback: (Boolean, String?) -> Unit) {
        val me = safeMe()
        if (me.isEmpty() || targetId.isBlank()) return
        db.child("contacts").child(me).child(targetId).removeValue().addOnCompleteListener { task ->
            if (task.isSuccessful) callback(true, null) else callback(false, task.exception?.message)
        }
    }

    // ---------------------------\
    // Messages listener
    // ---------------------------\
    private fun listenToMessages(target: String) {
        val me = safeMe()
        if (me.isEmpty()) return

        currentChatPath?.let { path -> messagesListener?.let { db.child(path).removeEventListener(it) } }

        val path = messagePath(me, target)
        currentChatPath = path

        startEphemeralCleanup(target)

        messagesListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val now = System.currentTimeMillis()
                val blocked = _blockedUsers.value.toSet()

                val msgs = s.children.mapNotNull { it.getValue(Message::class.java) }
                val filtered = msgs.filter {
                    (it.expiryTime == null || it.expiryTime!! > now) && !blocked.contains(it.senderId)
                }

                filtered.forEach { if (it.audioUrl != null) downloadAudioIfNeeded(it) }

                _messages.value = filtered

                // remove expirados
                msgs.forEach {
                    if (it.expiryTime != null && it.expiryTime!! < now) {
                        deleteMessage(it.id, target)
                    }
                }
            }
            override fun onCancelled(e: DatabaseError) {}
        }

        db.child(path).addValueEventListener(messagesListener!!)
    }

    private fun startEphemeralCleanup(target: String) {
        ephemeralCleanupJob?.cancel()
        ephemeralCleanupJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                val currentMsgs = _messages.value
                val hasExpired = currentMsgs.any { it.expiryTime != null && it.expiryTime!! < now }
                
                if (hasExpired) {
                    val filtered = currentMsgs.filter { it.expiryTime == null || it.expiryTime!! > now }
                    _messages.value = filtered
                    
                    // Cleanup DB
                    currentMsgs.forEach {
                        if (it.expiryTime != null && it.expiryTime!! < now) {
                            deleteMessage(it.id, target)
                        }
                    }
                }
            }
        }
    }

    private fun downloadAudioIfNeeded(msg: Message) {
        val audioUrl = msg.audioUrl ?: return
        val cacheFile = File(FriendApplication.instance.cacheDir, "audio_${msg.id}.m4a")

        if (cacheFile.exists()) {
            msg.localAudioPath = cacheFile.absolutePath
            return
        }

        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            try {
                URL(audioUrl).openStream().use { input ->
                    FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
                }
                withContext(Dispatchers.Main) {
                    msg.localAudioPath = cacheFile.absolutePath
                    _messages.value = _messages.value.toList()
                }
            } catch (e: Exception) {
                logE("Download audio failed: ${e.message}", e)
            }
        }
    }

    // ---------------------------\
    // Auth
    // ---------------------------\
    fun signUp(email: String, password: String, username: String, imageUri: Uri?, callback: (Boolean, String?) -> Unit) {
        val upper = username.uppercase().trim()
        val currentUser = auth.currentUser

        if (currentUser != null && _myId.value == currentUser.uid && _myUsername.value.isEmpty()) {
            // Usuário já autenticado mas sem perfil (caso de conta órfã)
            completeProfile(currentUser.uid, upper, username, imageUri, callback)
        } else {
            auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid ?: ""
                    completeProfile(uid, upper, username, imageUri, callback)
                } else {
                    val errorMsg = when (task.exception) {
                        is FirebaseAuthUserCollisionException -> "Este e-mail já está em uso."
                        is FirebaseAuthInvalidCredentialsException -> "Formato de e-mail inválido ou senha fraca."
                        else -> task.exception?.localizedMessage ?: "Erro ao criar conta"
                    }
                    callback(false, errorMsg)
                }
            }
        }
    }

    private fun completeProfile(uid: String, upper: String, username: String, imageUri: Uri?, callback: (Boolean, String?) -> Unit) {
        db.child("users").child(upper).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                // Se o username já existir, não deletamos o user do auth se ele já existia antes
                callback(false, "Este nome de usuário já está em uso.")
            } else {
                viewModelScope.launch(errorHandler) {
                    try {
                        val photoUrl = imageUri?.let { uploadToCloudinary(it, FOLDER_PROFILES) }
                        val profile = UserProfile(id = upper, uid = uid, name = username, photoUrl = photoUrl, isOnline = true)

                        db.child("uid_to_username").child(uid).setValue(upper).await()
                        db.child("users").child(upper).setValue(profile).await()

                        _myUsername.value = upper
                        _isUserLoggedIn.value = true
                        setupUserSession()
                        callback(true, null)
                    } catch (e: Exception) {
                        callback(false, "Erro ao salvar perfil: ${e.message}")
                    }
                }
            }
        }.addOnFailureListener {
            callback(false, "Erro de permissão no banco: ${it.message}")
        }
    }

    fun finalizeProfile(username: String, name: String, imageUri: Uri?, callback: (Boolean, String?) -> Unit) {
        val upper = username.uppercase().trim()
        val uid = _myId.value
        if (uid.isEmpty()) return callback(false, "Sessão inválida")
        completeProfile(uid, upper, name, imageUri, callback)
    }

    fun login(email: String, password: String, callback: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val uid = auth.currentUser?.uid ?: ""
                db.child("uid_to_username").child(uid).get().addOnSuccessListener { snapshot ->
                    val username = snapshot.getValue(String::class.java)
                    if (username != null) {
                        _myUsername.value = username
                        _isUserLoggedIn.value = true
                        setupUserSession()
                        callback(true, null)
                    } else {
                        // MUDANÇA: Permite o login mesmo sem perfil
                        _myUsername.value = ""
                        _isUserLoggedIn.value = true
                        _myId.value = uid
                        callback(true, "NEED_PROFILE")
                    }
                }.addOnFailureListener {
                    callback(false, "Erro ao carregar dados do perfil: ${it.message}")
                }
            } else {
                val errorMsg = when (task.exception) {
                    is FirebaseAuthInvalidUserException -> "Usuário não encontrado ou desativado."
                    is FirebaseAuthInvalidCredentialsException -> "E-mail ou senha incorretos."
                    else -> "E-mail ou senha incorretos."
                }
                callback(false, errorMsg)
            }
        }
    }

    fun resetPassword(email: String, callback: (Boolean, String?) -> Unit) {
        auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
            if (task.isSuccessful) callback(true, null) else callback(false, task.exception?.message)
        }
    }

    fun signInWithGoogle(idToken: String, callback: (Boolean, String?) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                val uid = user?.uid ?: ""

                db.child("uid_to_username").child(uid).get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        _isUserLoggedIn.value = true
                        setupUserSession()
                        callback(true, null)
                    } else {
                        val emailBase = user?.email?.split("@")?.get(0)?.uppercase()
                            ?: UUID.randomUUID().toString().take(8).uppercase()

                        val profile = UserProfile(
                            id = emailBase,
                            uid = uid,
                            name = user?.displayName ?: emailBase,
                            photoUrl = user?.photoUrl?.toString(),
                            isOnline = true
                        )

                        viewModelScope.launch(errorHandler) {
                            try {
                                db.child("uid_to_username").child(uid).setValue(emailBase).await()
                                db.child("users").child(emailBase).setValue(profile).await()
                                _isUserLoggedIn.value = true
                                setupUserSession()
                                callback(true, null)
                            } catch (e: Exception) {}
                        }
                    }
                }
            } else callback(false, task.exception?.message)
        }
    }

    // ---------------------------\
    // Profile update / delete account
    // ---------------------------\
    fun updateProfile(
        name: String? = null,
        imageUri: Uri? = null,
        status: String? = null,
        presenceStatus: String? = null,
        privacySettings: Map<String, Any>? = null,
        callback: ((Boolean) -> Unit)? = null
    ) {
        val username = safeMe()
        if (username.isEmpty()) {
            callback?.invoke(false)
            return
        }

        viewModelScope.launch(errorHandler) {
            try {
                val updates = mutableMapOf<String, Any?>()
                if (name != null) updates["name"] = name

                var photoUrl = _myPhotoUrl.value
                if (imageUri != null) {
                    photoUrl = uploadToCloudinary(imageUri, FOLDER_PROFILES)
                    if (photoUrl != null) updates["photoUrl"] = photoUrl
                }

                if (status != null) updates["status"] = status
                if (presenceStatus != null) updates["presenceStatus"] = presenceStatus
                privacySettings?.forEach { (k, v) -> updates[k] = v }

                if (updates.isNotEmpty()) {
                    db.child("users").child(username).updateChildren(updates).await()

                    if (name != null) _myName.value = name
                    if (updates["photoUrl"] != null) _myPhotoUrl.value = updates["photoUrl"] as String
                    if (status != null) _myStatus.value = status
                    if (presenceStatus != null) {
                        _myPresenceStatus.value = presenceStatus
                        updatePresence(FriendApplication.instance.isForeground.value)
                    }

                    privacySettings?.let {
                        (it["showLastSeen"] as? Boolean)?.let { v -> _showLastSeen.value = v }
                        (it["showReadReceipts"] as? Boolean)?.let { v -> _showReadReceipts.value = v }
                        (it["showOnlineStatus"] as? Boolean)?.let { v ->
                            _showOnlineStatus.value = v
                            updatePresence(FriendApplication.instance.isForeground.value)
                        }
                        (it["isHiddenFromSearch"] as? Boolean)?.let { v -> _isHiddenFromSearch.value = v }
                    }
                }

                callback?.invoke(true)
            } catch (e: Exception) {
                logE("Update profile error: ${e.message}", e)
                callback?.invoke(false)
            }
        }
    }

    fun toggleScreenshotBlock(friendId: String, currentStatus: Boolean) {
        val me = safeMe()
        if (me.isEmpty() || friendId.isEmpty()) return
        
        db.child("chats").child(me).child(friendId).child("isScreenshotDisabled").setValue(!currentStatus)
    }

    fun deleteAccount(callback: (Boolean, String?) -> Unit) {
        val user = auth.currentUser ?: return callback(false, "Usuário não autenticado")
        val username = safeMe()
        val uid = user.uid

        viewModelScope.launch(errorHandler) {
            try {
                if (username.isNotEmpty()) {
                    // 1. Limpar mídias e status (antes de perder o UID)
                    try {
                        val statusSnap = db.child("status").orderByChild("userId").equalTo(username).get().await()
                        statusSnap.children.forEach { s -> s.ref.removeValue() }
                    } catch (e: Exception) { Log.e(TAG, "Erro status: ${e.message}") }

                    // 2. Limpar os outros nós (Tokens, Chats, Logs)
                    val paths = listOf(
                        "fcmTokens/$username",
                        "chats/$username",
                        "contacts/$username",
                        "blocks/$username",
                        "call_logs/$username",
                        "call_notifications/$username"
                    )
                    paths.forEach { path ->
                        db.child(path).removeValue().await()
                    }

                    // 3. Deletar o perfil principal (users/LULU)
                    // Fazemos isso por último nos dados para garantir que os outros nós
                    // que dependem de 'users' ou 'uid_to_username' ainda validem a regra
                    db.child("users/$username").removeValue().await()
                    db.child("uid_to_username/$uid").removeValue().await()
                }

                // 4. AGORA SIM: Deletar a credencial de acesso
                // Após este comando, o auth.uid vira null e nada mais pode ser deletado no Database
                user.delete().await()

                logout()
                callback(true, null)
            } catch (e: Exception) {
                val msg = if (e.message?.contains("recent-login") == true)
                    "Re-autenticação necessária." else e.message
                callback(false, msg)
            }
        }
    }

    // ---------------------------\
    // Status
    // ---------------------------\
    fun uploadStatus(uris: List<Uri>) {
        val uid = safeMe()
        if (uid.isEmpty()) return

        viewModelScope.launch(errorHandler) {
            uris.forEach { uri ->
                try {
                    val mime = FriendApplication.instance.contentResolver.getType(uri)
                    val isVideo = mime?.startsWith("video") == true
                    val folder = if (isVideo) FOLDER_CHAT_VIDEOS else FOLDER_STATUSES
                    val resourceType = if (isVideo) "video" else "image"
                    
                    val url = uploadToCloudinary(uri, folder, resourceType)
                    if (url != null) {
                        val statusId = db.push().key ?: return@forEach
                        val status = UserStatus(
                            id = statusId,
                            userId = uid,
                            username = _myName.value,
                            imageUrl = if (isVideo) "" else url,
                            videoUrl = if (isVideo) url else null,
                            isVideo = isVideo,
                            userPhotoUrl = _myPhotoUrl.value,
                            timestamp = System.currentTimeMillis()
                        )
                        db.child("status").child(statusId).setValue(status)
                    }
                } catch (e: Exception) {
                    logE("Upload status error: ${e.message}")
                }
            }
        }
    }

    fun deleteStatus(statusId: String) {
        db.child("status").child(statusId).get().addOnSuccessListener { snapshot ->
            val status = snapshot.getValue(UserStatus::class.java)
            if (status != null) {
                val urlToDelete = if (status.isVideo) status.videoUrl else status.imageUrl
                if (!urlToDelete.isNullOrEmpty()) {
                    deleteFromFirebaseStorage(urlToDelete)
                }
            }
            db.child("status").child(statusId).removeValue()
        }
    }

    fun markStatusAsViewed(statusId: String) {
        val me = safeMe()
        if (me.isEmpty()) return
        db.child("status").child(statusId).child("viewers").child(me).setValue(System.currentTimeMillis())
    }

    private fun listenToStatuses(myUsername: String) {
        statusesListener?.let { db.child("status").removeEventListener(it) }

        statusesListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val now = System.currentTimeMillis()
                val blocked = _blockedUsers.value.toSet()

                val list = s.children.mapNotNull { it.getValue(UserStatus::class.java) }
                    .filter { now - it.timestamp < STATUS_TTL_MS && !blocked.contains(it.userId) }

                db.child("contacts").child(myUsername).get().addOnSuccessListener { contactSnapshot ->
                    val contactIds = contactSnapshot.children.mapNotNull { it.key }.toSet()
                    _statuses.value = list
                        .filter { it.userId == myUsername || contactIds.contains(it.userId) }
                        .sortedByDescending { it.timestamp }
                }
            }
            override fun onCancelled(e: DatabaseError) {}
        }

        db.child("status").limitToLast(50).addValueEventListener(statusesListener!!)
    }

    // ---------------------------\
    // Search / chat ops / block / calls
    // ---------------------------\
    fun searchUsers(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        searchJob = viewModelScope.launch(errorHandler) {
            delay(300)
            try {
                val blocked = _blockedUsers.value.toSet()
                val snapshot = db.child("users").get().await()
                val users = snapshot.children.mapNotNull { it.getValue(UserProfile::class.java) }
                    .filter {
                        it.id != _myUsername.value &&
                                !blocked.contains(it.id) &&
                                !it.isHiddenFromSearch
                    }
                    .filter {
                        if (query.startsWith("@")) {
                            val idQuery = query.substring(1)
                            it.id.contains(idQuery, true)
                        } else {
                            it.name.contains(query, true)
                        }
                    }
                    .distinctBy { it.id }
                _searchResults.value = users
            } catch (e: Exception) {
                logE("Search error: ${e.message}", e)
            }
        }
    }

    fun deleteChat(friendId: String) {
        val me = safeMe()
        if (me.isEmpty() || friendId.isBlank()) return

        db.child("messages/${chatKey(me, friendId)}").get().addOnSuccessListener { snapshot ->
            snapshot.children.forEach { msgSnap ->
                val msg = msgSnap.getValue(Message::class.java)
                if (msg != null) deleteMessageMedia(msg)
            }
            db.child("chats").child(me).child(friendId).removeValue()
        }
    }

    fun clearChat(friendId: String) {
        val me = safeMe()
        if (me.isEmpty()) return

        val path = "messages/${chatKey(me, friendId)}"
        db.child(path).get().addOnSuccessListener { snapshot ->
            snapshot.children.forEach { msgSnap ->
                val msg = msgSnap.getValue(Message::class.java)
                if (msg != null) deleteMessageMedia(msg)
            }
            db.child(path).removeValue()

            val upd = mapOf("lastMessage" to "Conversa limpa", "timestamp" to System.currentTimeMillis(), "hasUnread" to false)
            db.child("chats").child(me).child(friendId).updateChildren(upd)
            db.child("chats").child(friendId).child(me).updateChildren(upd)
        }
    }

    fun blockUser(targetId: String) {
        val me = safeMe()
        if (me.isEmpty()) return
        db.child("blocks").child(me).child(targetId).setValue(true)
    }

    fun unblockUser(targetId: String) {
        val me = safeMe()
        if (me.isEmpty()) return
        db.child("blocks").child(me).child(targetId).removeValue()
    }

    private fun listenToBlockedUsers(username: String) {
        blockedListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val ids = s.children.mapNotNull { it.key }
                _blockedUsers.value = ids

                listenToChats(username)
                listenToContacts(username)
                listenToStatuses(username)

                viewModelScope.launch(errorHandler) {
                    try {
                        val profiles = ids.mapNotNull { id ->
                            db.child("users").child(id).get().await().getValue(UserProfile::class.java)
                        }
                        _blockedProfiles.value = profiles
                    } catch (_: Exception) {}
                }
            }
            override fun onCancelled(error: DatabaseError) {
                logE("listenToBlockedUsers cancelled: ${error.message}")
            }
        }
        db.child("blocks").child(username).addValueEventListener(blockedListener!!)
    }

    fun deleteMessage(messageId: String, friendId: String) {
        val me = safeMe()
        if (me.isEmpty()) return

        val path = "messages/${chatKey(me, friendId)}"

        db.child(path).child(messageId).get().addOnSuccessListener { snapshot ->
            val msg = snapshot.getValue(Message::class.java)
            if (msg != null) deleteMessageMedia(msg)

            db.child(path).child(messageId).removeValue().addOnSuccessListener {
                db.child(path).orderByChild("timestamp").limitToLast(1).get().addOnSuccessListener { nextSnapshot ->
                    val lastMsg = nextSnapshot.children.firstOrNull()?.getValue(Message::class.java)
                    val meRef = db.child("chats").child(me).child(friendId)
                    val frRef = db.child("chats").child(friendId).child(me)

                    if (lastMsg != null) {
                        val updatedText = when {
                            lastMsg.audioUrl != null -> "🎤 Áudio"
                            lastMsg.imageUrl != null -> "📷 Imagem"
                            lastMsg.videoUrl != null -> "📹 Vídeo"
                            lastMsg.stickerUrl != null -> "Sticker"
                            else -> lastMsg.text
                        }
                        meRef.child("lastMessage").setValue(updatedText)
                        meRef.child("timestamp").setValue(lastMsg.timestamp)
                        frRef.child("lastMessage").setValue(updatedText)
                        frRef.child("timestamp").setValue(lastMsg.timestamp)
                    } else {
                        meRef.child("lastMessage").setValue("Conversa vazia")
                        frRef.child("lastMessage").setValue("Conversa vazia")
                    }
                }
            }
        }
    }

    private fun deleteMessageMedia(msg: Message) {
        msg.imageUrl?.let { deleteFromFirebaseStorage(it) }
        msg.videoUrl?.let { deleteFromFirebaseStorage(it) }
        msg.audioUrl?.let { deleteFromFirebaseStorage(it) }
    }

    private fun deleteFromFirebaseStorage(url: String) {
        if (!url.contains("firebasestorage.googleapis.com")) return
        try {
            FirebaseStorage.getInstance().getReferenceFromUrl(url).delete()
                .addOnFailureListener { logE("Erro ao deletar mídia do Storage: ${it.message}") }
        } catch (e: Exception) {
            logE("Erro ao obter referência para deletar mídia: ${e.message}")
        }
    }

    fun startCall(isVideo: Boolean, customRoomId: String) {
        val me = safeMe()
        val target = safeTarget()
        if (me.isEmpty()) return
        if (target.isEmpty()) return

        viewModelScope.launch(errorHandler) {
            try {
                if (_blockedUsers.value.contains(target)) return@launch

                val roomId = customRoomId
                val callData = mapOf(
                    "callerId" to me,
                    "receiverId" to target,
                    "status" to "RINGING",
                    "isVideo" to isVideo,
                    "timestamp" to ServerValue.TIMESTAMP
                )
                db.child("calls").child(roomId).setValue(callData)

                // ✅ Puxa dados do alvo para cachear no log
                val targetProf = db.child("users").child(target).get().await().getValue(UserProfile::class.java)
                val meProf = db.child("users").child(me).get().await().getValue(UserProfile::class.java)

                val now = System.currentTimeMillis()
                val type = if (isVideo) "VIDEO" else "AUDIO"

                // ✅ LOG para quem ligou
                val myLog = CallLog(
                    id = roomId,
                    peerId = target,
                    peerName = targetProf?.name ?: target,
                    peerPhotoUrl = targetProf?.photoUrl,
                    direction = "OUT",
                    type = type,
                    status = "RINGING",
                    timestamp = now
                )

                // ✅ LOG para quem recebe
                val theirLog = CallLog(
                    id = roomId,
                    peerId = me,
                    peerName = meProf?.name ?: me,
                    peerPhotoUrl = meProf?.photoUrl,
                    direction = "IN",
                    type = type,
                    status = "RINGING",
                    timestamp = now
                )

                db.child("call_logs").child(me).child(roomId).setValue(myLog)
                db.child("call_logs").child(target).child(roomId).setValue(theirLog)

                // ✅ sua notificação + mensagem “isCall”
                val msgId = db.push().key ?: return@launch
                val msg = Message(
                    id = msgId,
                    senderId = me,
                    receiverId = target,
                    text = if (isVideo) "Chamada de vídeo" else "Chamada de áudio",
                    timestamp = now,
                    isGroup = false,
                    senderName = _myName.value,
                    senderPhotoUrl = _myPhotoUrl.value,
                    callRoomId = roomId,
                    callType = type,
                    callStatus = "STARTING",
                    isCall = true
                )

                db.child("call_notifications").child(target).setValue(msg)
                db.child(messagePath(me, target)).child(msgId).setValue(msg)

            } catch (e: Exception) {
                logE("Erro ao iniciar chamada: ${e.message}", e)
            }
        }
    }


    fun togglePinChat(friendId: String, currentStatus: Boolean) {
        val me = safeMe()
        if (me.isEmpty()) return
        db.child("chats").child(me).child(friendId).child("isPinned").setValue(!currentStatus)
    }

    fun toggleMuteChat(friendId: String, currentStatus: Boolean) {
        val me = safeMe()
        if (me.isEmpty()) return
        db.child("chats").child(me).child(friendId).child("isMuted").setValue(!currentStatus)
    }

    fun setTempMessageDuration(friendId: String, duration: Long) {
        val me = safeMe()
        if (me.isEmpty() || friendId.isEmpty()) return
        val updates = mapOf(
            "tempDuration" to duration,
            "isEphemeral" to (duration > 0)
        )
        db.child("chats").child(me).child(friendId).updateChildren(updates)
        db.child("chats").child(friendId).child(me).updateChildren(updates)
    }

    fun clearCallHistory() {
        val me = safeMe()
        if (me.isEmpty()) return
        db.child("call_logs").child(me).removeValue()
    }

    fun deleteCallLog(logId: String) {
        val me = safeMe()
        if (me.isEmpty()) return
        db.child("call_logs").child(me).child(logId).removeValue()
    }

    fun fetchLocalMedia(context: Context) {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            val mediaList = mutableListOf<LocalMedia>()

            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.DURATION,
                MediaStore.Files.FileColumns.DATE_ADDED
            )

            val selection = (MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                    + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                    + " OR "
                    + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + "="
                    + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)

            val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Files.getContentUri("external")
            }

            context.contentResolver.query(
                queryUri,
                projection,
                selection,
                null,
                "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val type = cursor.getInt(typeColumn)
                    val duration = cursor.getLong(durationColumn)
                    val dateAdded = cursor.getLong(dateColumn)

                    val contentUri = if (type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    } else {
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    }

                    mediaList.add(
                        LocalMedia(
                            uri = contentUri,
                            isVideo = type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,
                            duration = duration,
                            dateAdded = dateAdded
                        )
                    )
                }
            }
            withContext(Dispatchers.Main) {
                _localMedia.value = mediaList
            }
        }
    }

    // Métodos para lidar com compartilhamento externo
    fun setPendingShare(text: String?, uris: List<Uri>) {
        _pendingSharedText.value = text
        _pendingSharedMedia.value = uris
    }

    fun clearPendingShare() {
        _pendingSharedText.value = null
        _pendingSharedMedia.value = emptyList()
    }

    fun sendPendingShare(targetId: String, tempDurationMillis: Long = 0) {
        val text = _pendingSharedText.value
        val media = _pendingSharedMedia.value

        setTargetId(targetId)

        viewModelScope.launch {
            if (!text.isNullOrBlank()) {
                sendMessage(text, tempDurationMillis)
            }
            media.forEach { uri ->
                val mime = FriendApplication.instance.contentResolver.getType(uri)
                if (mime?.startsWith("video") == true) {
                    uploadVideo(uri, tempDurationMillis)
                } else {
                    uploadImage(uri, tempDurationMillis)
                }
            }
            clearPendingShare()
        }
    }

    fun notifyScreenshotAttempt() {
        val me = safeMe()
        val target = safeTarget()
        if (me.isEmpty() || target.isEmpty()) return

        val msgId = db.push().key ?: return
        val msg = Message(
            id = msgId,
            senderId = me,
            receiverId = target,
            text = "Tentou tirar um print da tela! 📸",
            timestamp = System.currentTimeMillis(),
            isGroup = false,
            senderName = _myName.value,
            isEdited = false, // Usar um campo para sinalizar aviso do sistema se quiser, ou texto simples
        )
        sendMessageObject(msg)
    }
}