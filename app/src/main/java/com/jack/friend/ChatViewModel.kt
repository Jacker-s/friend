package com.jack.friend

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.auth.FirebaseAuth
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
        private const val FOLDER_GROUP_PROFILES = "group_profiles"
        private const val FOLDER_CHAT_IMAGES = "chat_images"
        private const val FOLDER_STATUSES = "statuses"
    }

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private val storage = FirebaseStorage.getInstance().reference

    private val errorHandler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, "Coroutine error: ${t.message}", t)
    }

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

    private val _targetGroup = MutableStateFlow<Group?>(null)
    val targetGroup: StateFlow<Group?> = _targetGroup

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

    private fun messagePath(me: String, target: String, isGroup: Boolean): String =
        if (isGroup) "group_messages/$target" else "messages/${chatKey(me, target)}"

    private fun pinnedPathKey(me: String, target: String, isGroup: Boolean): String =
        if (isGroup) target else chatKey(me, target)

    private fun safeMe(): String = _myUsername.value
    private fun safeTarget(): String = _targetId.value
    private fun isGroupTarget(): Boolean = _targetGroup.value != null

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
        val user = safeMe()
        if (user.isNotEmpty()) {
            db.child("users").child(user).child("isOnline").setValue(false)
            db.child("users").child(user).child("lastActive").setValue(ServerValue.TIMESTAMP)
            db.child("fcmTokens").child(user).removeValue()
        }

        removeListeners()
        auth.signOut()

        _isUserLoggedIn.value = false
        _myUsername.value = ""
        _activeChats.value = emptyList()
        _messages.value = emptyList()
        _targetId.value = ""
        _targetProfile.value = null
        _targetGroup.value = null
        _pinnedMessage.value = null
        _isTargetTyping.value = false
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
            val key = pinnedPathKey(me, safeTarget(), isGroupTarget())
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

        stopTimer()
        releaseRecorderSafely()
    }

    // ---------------------------
    // Target
    // ---------------------------
    fun setTargetId(id: String, isGroup: Boolean = false) {
        val me = safeMe()
        if (_targetId.value == id && id.isNotEmpty()) return

        // limpar alvo anterior (sem duplicar código em 4 lugares)
        val oldTarget = _targetId.value
        if (oldTarget.isNotEmpty() && me.isNotEmpty()) {
            val oldKey = pinnedPathKey(me, oldTarget, _targetGroup.value != null)
            db.child("typing").child(oldKey).child(me).setValue(false)
        }

        targetProfileListener?.let { l ->
            if (oldTarget.isNotEmpty()) db.child("users").child(oldTarget).removeEventListener(l)
        }
        targetProfileListener = null

        pinnedMessageListener?.let { l ->
            val oldKey = pinnedPathKey(me, oldTarget, _targetGroup.value != null)
            db.child("pinned").child(oldKey).removeEventListener(l)
        }
        pinnedMessageListener = null

        currentChatPath?.let { oldPath ->
            messagesListener?.let { l -> db.child(oldPath).removeEventListener(l) }
        }
        messagesListener = null
        currentChatPath = null

        _targetId.value = id

        if (id.isBlank()) {
            _targetProfile.value = null
            _targetGroup.value = null
            _messages.value = emptyList()
            _isTargetTyping.value = false
            _pinnedMessage.value = null
            return
        }

        _isTargetTyping.value = _activeChats.value.find { it.friendId == id }?.isTyping ?: false

        // pinned
        val pinKey = pinnedPathKey(me, id, isGroup)
        pinnedMessageListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _pinnedMessage.value = snapshot.getValue(Message::class.java)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("pinned").child(pinKey).addValueEventListener(pinnedMessageListener!!)

        // perfil ou grupo
        if (!isGroup) {
            _targetGroup.value = null
            targetProfileListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    _targetProfile.value = snapshot.getValue(UserProfile::class.java)
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            db.child("users").child(id).addValueEventListener(targetProfileListener!!)
        } else {
            _targetProfile.value = null
            db.child("groups").child(id).get()
                .addOnSuccessListener { _targetGroup.value = it.getValue(Group::class.java) }
        }

        listenToMessages(id, isGroup)
    }

    private fun syncTypingStatus(friendUsername: String, isGroup: Boolean = false) {
        if (typingListeners.containsKey(friendUsername)) return
        val me = safeMe()
        if (me.isEmpty()) return

        val key = if (isGroup) friendUsername else chatKey(me, friendUsername)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isTyping = if (isGroup) {
                    snapshot.children.any { it.key != me && it.getValue(Boolean::class.java) == true }
                } else snapshot.getValue(Boolean::class.java) ?: false

                _activeChats.value = _activeChats.value.map {
                    if (it.friendId == friendUsername) it.copy(isTyping = isTyping) else it
                }
                if (friendUsername == _targetId.value) _isTargetTyping.value = isTyping
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        typingListeners[friendUsername] = listener
        val ref = if (isGroup) db.child("typing").child(key) else db.child("typing").child(key).child(friendUsername)
        ref.addValueEventListener(listener)
    }

    fun setTyping(isTyping: Boolean) {
        val me = safeMe()
        val target = safeTarget()
        if (me.isEmpty() || target.isEmpty()) return

        val isGroup = isGroupTarget()
        val key = pinnedPathKey(me, target, isGroup)
        val typingRef = db.child("typing").child(key).child(me)
        typingRef.setValue(isTyping)
        if (isTyping) typingRef.onDisconnect().setValue(false)
    }

    // ---------------------------
    // Send message / sticker / reactions
    // ---------------------------
    fun sendMessage(text: String, isGroup: Boolean, tempDurationHours: Int = 0, replyingTo: Message? = null) {
        val me = safeMe()
        val target = safeTarget()
        if (me.isEmpty() || target.isEmpty()) return
        if (!isGroup && _blockedUsers.value.contains(target)) return

        val msgId = db.push().key ?: return
        val msg = Message(
            id = msgId,
            senderId = me,
            receiverId = target,
            text = text,
            timestamp = System.currentTimeMillis(),
            isGroup = isGroup,
            senderName = _myName.value,
            replyToId = replyingTo?.id,
            replyToText = replyingTo?.text
                ?: if (replyingTo?.imageUrl != null) "📷 Imagem"
                else if (replyingTo?.audioUrl != null) "🎤 Áudio"
                else null,
            replyToName = replyingTo?.senderName ?: replyingTo?.senderId,
            expiryTime = if (tempDurationHours > 0) System.currentTimeMillis() + (tempDurationHours * 3_600_000L) else null
        )

        sendMessageObject(msg)
        setTyping(false)
    }

    fun sendSticker(stickerUrl: String, isGroup: Boolean, replyingTo: Message? = null) {
        val me = safeMe()
        val target = safeTarget()
        if (me.isEmpty() || target.isEmpty()) return
        if (!isGroup && _blockedUsers.value.contains(target)) return

        val msgId = db.push().key ?: return
        val msg = Message(
            id = msgId,
            senderId = me,
            receiverId = target,
            stickerUrl = stickerUrl,
            isSticker = true,
            timestamp = System.currentTimeMillis(),
            isGroup = isGroup,
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

        val path = if (message.isGroup) "group_messages/${message.receiverId}"
        else "messages/${chatKey(message.senderId, message.receiverId)}"

        db.child(path).child(message.id).updateChildren(mapOf("text" to newText, "isEdited" to true))
    }

    fun pinMessage(message: Message) {
        val key = if (message.isGroup) message.receiverId else chatKey(message.senderId, message.receiverId)
        db.child("pinned").child(key).setValue(message)
    }

    fun unpinMessage() {
        val me = safeMe()
        val target = safeTarget()
        val isGroup = isGroupTarget()
        val key = pinnedPathKey(me, target, isGroup)
        db.child("pinned").child(key).removeValue()
    }

    fun addReaction(message: Message, emoji: String) {
        val me = safeMe()
        if (me.isEmpty()) return
        pushRecentEmoji(emoji)

        val path = if (message.isGroup) "group_messages/${message.receiverId}"
        else "messages/${chatKey(message.senderId, message.receiverId)}"

        db.child(path).child(message.id).child("reactions").child(me).setValue(emoji)
    }

    fun markAsRead() {
        val me = safeMe()
        val target = safeTarget()
        if (me.isEmpty() || target.isEmpty()) return
        val isGroup = isGroupTarget()

        if (!isGroup) {
            val path = "messages/${chatKey(me, target)}"
            db.child(path).orderByChild("receiverId").equalTo(me).get()
                .addOnSuccessListener { snapshot ->
                    snapshot.children.forEach {
                        if (it.child("isRead").getValue(Boolean::class.java) != true) {
                            it.ref.child("isRead").setValue(true)
                        }
                    }
                }
        }
        db.child("chats").child(me).child(target).child("hasUnread").setValue(false)
    }

    // ---------------------------
    // Audio recording + upload
    // ---------------------------
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

    fun stopRecording(isGroup: Boolean, tempDurationHours: Int = 0, cancel: Boolean = false) {
        stopTimer()
        val duration = System.currentTimeMillis() - recordingStartTime

        try { mediaRecorder?.stop() } catch (e: Exception) { logE("Stop recording failed: ${e.message}", e) }
        finally { releaseRecorderSafely() }

        if (cancel || duration < RECORD_MIN_MS) {
            audioFile?.delete()
            audioFile = null
            return
        }

        audioFile?.let { uploadAudio(it, isGroup, tempDurationHours) }
    }

    private suspend fun uploadToCloudinary(uri: Uri, folder: String): String? =
        suspendCoroutine { continuation ->
            MediaManager.get().upload(uri)
                .option("folder", folder)
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

    private fun uploadAudio(file: File, isGroup: Boolean, tempDurationHours: Int) {
        val me = safeMe()
        val target = safeTarget()
        if (me.isEmpty() || target.isEmpty()) return

        viewModelScope.launch(errorHandler) {
            try {
                val audioRef = storage.child("audios/${UUID.randomUUID()}.m4a")
                audioRef.putFile(Uri.fromFile(file)).await()
                val url = audioRef.downloadUrl.await().toString()

                val msgId = db.push().key ?: return@launch
                val msg = Message(
                    id = msgId,
                    senderId = me,
                    receiverId = target,
                    audioUrl = url,
                    timestamp = System.currentTimeMillis(),
                    isGroup = isGroup,
                    senderName = _myName.value,
                    expiryTime = if (tempDurationHours > 0) System.currentTimeMillis() + (tempDurationHours * 3_600_000L) else null
                )
                msg.localAudioPath = file.absolutePath
                sendMessageObject(msg)
            } catch (e: Exception) {
                logE("Upload áudio Firebase falhou: ${e.message}", e)
            }
        }
    }

    fun uploadImage(uri: Uri, isGroup: Boolean, tempDurationHours: Int = 0) {
        val me = safeMe()
        val target = safeTarget()
        if (me.isEmpty() || target.isEmpty()) return

        viewModelScope.launch(errorHandler) {
            try {
                val url = uploadToCloudinary(uri, FOLDER_CHAT_IMAGES)
                if (url != null) {
                    val msgId = db.push().key ?: return@launch
                    sendMessageObject(
                        Message(
                            id = msgId,
                            senderId = me,
                            receiverId = target,
                            imageUrl = url,
                            timestamp = System.currentTimeMillis(),
                            isGroup = isGroup,
                            senderName = _myName.value,
                            expiryTime = if (tempDurationHours > 0) System.currentTimeMillis() + (tempDurationHours * 3_600_000L) else null
                        )
                    )
                }
            } catch (e: Exception) {
                logE("uploadImage error: ${e.message}", e)
            }
        }
    }

    // ---------------------------
    // Persist message + summaries
    // ---------------------------
    private fun sendMessageObject(msg: Message) {
        if (msg.isGroup) {
            db.child("group_messages/${msg.receiverId}").child(msg.id).setValue(msg)
            updateChatSummary(msg)
            return
        }

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
                    msg.stickerUrl != null -> "Sticker"
                    else -> msg.text
                }

                if (msg.isGroup) {
                    val group = db.child("groups").child(friend).get().await().getValue(Group::class.java) ?: return@launch

                    val summary = ChatSummary(
                        friendId = friend,
                        lastMessage = "${msg.senderName}: $lastMsgText",
                        timestamp = msg.timestamp,
                        lastSenderId = me,
                        friendName = group.name,
                        friendPhotoUrl = group.photoUrl,
                        isGroup = true,
                        hasUnread = false
                    )

                    group.members.keys.forEach { member ->
                        db.child("chats").child(member).child(friend)
                            .setValue(summary.copy(hasUnread = member != me))
                    }
                } else {
                    val friendProf = db.child("users").child(friend).get().await().getValue(UserProfile::class.java)

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
                        presenceStatus = friendProf?.presenceStatus ?: "Online"
                    )

                    db.child("chats").child(me).child(friend).setValue(summary)

                    val meProf = db.child("users").child(me).get().await().getValue(UserProfile::class.java)
                    db.child("chats").child(friend).child(me).setValue(
                        summary.copy(
                            friendId = me,
                            friendName = meProf?.name ?: me,
                            friendPhotoUrl = meProf?.photoUrl,
                            hasUnread = true,
                            presenceStatus = meProf?.presenceStatus ?: "Online"
                        )
                    )
                }
            } catch (e: Exception) {
                logE("Erro ao atualizar resumo: ${e.message}", e)
            }
        }
    }

    // ---------------------------
    // Groups
    // ---------------------------
    fun createGroup(name: String, members: List<String>, photoUri: Uri?, callback: (Boolean, String?) -> Unit) {
        val me = safeMe()
        if (me.isEmpty()) return

        viewModelScope.launch(errorHandler) {
            try {
                val groupId = "GROUP_${UUID.randomUUID()}"
                val photoUrl = photoUri?.let { uploadToCloudinary(it, FOLDER_GROUP_PROFILES) }

                val membersMap = (members + me).associateWith { true }
                val group = Group(
                    id = groupId,
                    name = name,
                    photoUrl = photoUrl,
                    members = membersMap,
                    createdBy = me,
                    timestamp = System.currentTimeMillis()
                )

                db.child("groups").child(groupId).setValue(group).await()

                val welcomeMsg = Message(
                    id = db.push().key ?: "",
                    senderId = "SYSTEM",
                    receiverId = groupId,
                    text = "Grupo criado",
                    timestamp = System.currentTimeMillis(),
                    isGroup = true,
                    senderName = "Sistema"
                )
                db.child("group_messages/$groupId").child(welcomeMsg.id).setValue(welcomeMsg).await()
                updateChatSummary(welcomeMsg)

                callback(true, groupId)
            } catch (e: Exception) {
                callback(false, e.message)
            }
        }
    }

    fun deleteGroup(groupId: String, callback: (Boolean, String?) -> Unit) {
        val me = safeMe()
        if (me.isEmpty()) return

        db.child("groups").child(groupId).get().addOnSuccessListener { snapshot ->
            val group = snapshot.getValue(Group::class.java)
            if (group != null && group.createdBy == me) {
                viewModelScope.launch(errorHandler) {
                    try {
                        db.child("group_messages").child(groupId).removeValue()
                        group.members.keys.forEach { member ->
                            db.child("chats").child(member).child(groupId).removeValue()
                        }
                        db.child("groups").child(groupId).removeValue().await()
                        callback(true, null)
                    } catch (e: Exception) {
                        callback(false, e.message)
                    }
                }
            } else callback(false, "Apenas o criador pode excluir o grupo")
        }.addOnFailureListener { callback(false, it.message) }
    }

    // ---------------------------
    // Presence
    // ---------------------------
    fun updatePresence(online: Boolean) {
        val user = safeMe()
        if (user.isEmpty()) return

        val isVisible = _myPresenceStatus.value != "Invisível" && _showOnlineStatus.value
        db.child("users").child(user).child("isOnline").setValue(online && isVisible)

        if (!online) {
            db.child("users").child(user).child("lastActive").setValue(ServerValue.TIMESTAMP)
        }
    }

    // ---------------------------
    // Chats / Contacts listeners
    // ---------------------------
    private fun listenToChats(username: String) {
        chatsListener?.let { db.child("chats").child(username).removeEventListener(it) }

        chatsListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val blocked = _blockedUsers.value.toSet()
                val chats = s.children.mapNotNull { it.getValue(ChatSummary::class.java) }
                    .filter { it.friendId.isNotBlank() && !blocked.contains(it.friendId) }
                    .sortedWith(compareByDescending<ChatSummary> { it.isPinned }.thenByDescending { it.timestamp })

                _activeChats.value = chats

                chats.forEach { chat ->
                    if (!chat.isGroup) syncFriendPresence(chat.friendId)
                    syncTypingStatus(chat.friendId, chat.isGroup)
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

    private fun syncFriendPresence(friendUsername: String) {
        if (presenceListeners.containsKey(friendUsername)) return

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(UserProfile::class.java)
                val isOnline = user?.isOnline ?: false
                val presenceStatus = user?.presenceStatus ?: "Online"

                _activeChats.value = _activeChats.value.map {
                    if (it.friendId == friendUsername) it.copy(isOnline = isOnline, presenceStatus = presenceStatus) else it
                }
                _contacts.value = _contacts.value.map {
                    if (it.id == friendUsername) it.copy(isOnline = isOnline, presenceStatus = presenceStatus) else it
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        presenceListeners[friendUsername] = listener
        db.child("users").child(friendUsername).addValueEventListener(listener)
    }

    // ---------------------------
    // Messages listener
    // ---------------------------
    private fun listenToMessages(target: String, isGroup: Boolean) {
        val me = safeMe()
        if (me.isEmpty()) return

        currentChatPath?.let { path -> messagesListener?.let { db.child(path).removeEventListener(it) } }

        val path = messagePath(me, target, isGroup)
        currentChatPath = path

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
                        db.child(path).child(it.id).removeValue()
                    }
                }
            }
            override fun onCancelled(e: DatabaseError) {}
        }

        db.child(path).addValueEventListener(messagesListener!!)
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

    // ---------------------------
    // Auth (mantido)
    // ---------------------------
    fun signUp(email: String, password: String, username: String, imageUri: Uri?, callback: (Boolean, String?) -> Unit) {
        val upper = username.uppercase().trim()

        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val uid = auth.currentUser?.uid ?: ""
                db.child("users").child(upper).get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        auth.currentUser?.delete()
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
                                auth.currentUser?.delete()
                                callback(false, "Erro ao salvar perfil: ${e.message}")
                            }
                        }
                    }
                }.addOnFailureListener {
                    auth.currentUser?.delete()
                    callback(false, "Erro de permissão no banco: ${it.message}")
                }
            } else callback(false, task.exception?.message ?: "Erro ao criar conta")
        }
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
                        auth.signOut()
                        callback(false, "Perfil não encontrado no banco de dados.")
                    }
                }.addOnFailureListener {
                    auth.signOut()
                    callback(false, "Erro ao carregar dados do perfil: ${it.message}")
                }
            } else callback(false, task.exception?.message ?: "E-mail ou senha incorretos")
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

    // ---------------------------
    // Profile update / delete account (mantido)
    // ---------------------------
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
                    }
                }

                callback?.invoke(true)
            } catch (e: Exception) {
                logE("Update profile error: ${e.message}", e)
                callback?.invoke(false)
            }
        }
    }

    fun deleteAccount(callback: (Boolean, String?) -> Unit) {
        val user = auth.currentUser ?: return
        val username = safeMe()

        viewModelScope.launch(errorHandler) {
            try {
                val chatsSnapshot = db.child("chats").child(username).get().await()
                chatsSnapshot.children.forEach { chatSnap ->
                    val summary = chatSnap.getValue(ChatSummary::class.java) ?: return@forEach
                    val friendId = summary.friendId

                    if (!summary.isGroup) {
                        val update = mapOf(
                            "friendName" to "Conta Excluída",
                            "lastMessage" to "Esta conta foi excluída",
                            "friendPhotoUrl" to null,
                            "isOnline" to false,
                            "presenceStatus" to "Offline"
                        )
                        db.child("chats").child(friendId).child(username).updateChildren(update)
                    }
                }

                user.delete().await()

                if (username.isNotEmpty()) {
                    db.child("users").child(username).removeValue()
                    db.child("chats").child(username).removeValue()
                    db.child("contacts").child(username).removeValue()
                    db.child("blocks").child(username).removeValue()
                    db.child("fcmTokens").child(username).removeValue()

                    db.child("status").orderByChild("userId").equalTo(username).get().addOnSuccessListener {
                        it.children.forEach { s -> s.ref.removeValue() }
                    }
                }
                db.child("uid_to_username").child(user.uid).removeValue()

                logout()
                callback(true, null)
            } catch (e: Exception) {
                callback(false, e.message)
            }
        }
    }

    // ---------------------------
    // Status (mantido)
    // ---------------------------
    fun uploadStatus(uri: Uri) {
        val uid = safeMe()
        if (uid.isEmpty()) return

        viewModelScope.launch(errorHandler) {
            try {
                val url = uploadToCloudinary(uri, FOLDER_STATUSES)
                if (url != null) {
                    val statusId = db.push().key ?: return@launch
                    val status = UserStatus(
                        id = statusId,
                        userId = uid,
                        username = _myName.value,
                        imageUrl = url,
                        userPhotoUrl = _myPhotoUrl.value,
                        timestamp = System.currentTimeMillis()
                    )
                    db.child("status").child(statusId).setValue(status)
                }
            } catch (_: Exception) {}
        }
    }

    fun deleteStatus(statusId: String) {
        db.child("status").child(statusId).removeValue()
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

    // ---------------------------
    // Search / chat ops / block / calls (mantido)
    // ---------------------------
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

        db.child("chats").child(me).child(friendId).get().addOnSuccessListener { snapshot ->
            val summary = snapshot.getValue(ChatSummary::class.java)
            if (summary?.isGroup == true) {
                db.child("chats").child(me).child(friendId).updateChildren(mapOf("lastMessage" to "Conversa excluída", "hasUnread" to false))
            } else {
                db.child("chats").child(me).child(friendId).removeValue()
            }
        }
    }

    fun clearChat(friendId: String, isGroup: Boolean) {
        val me = safeMe()
        if (me.isEmpty()) return

        if (isGroup) {
            db.child("chats").child(me).child(friendId).updateChildren(
                mapOf("lastMessage" to "Conversa limpa", "timestamp" to System.currentTimeMillis(), "hasUnread" to false)
            )
        } else {
            db.child("messages/${chatKey(me, friendId)}").removeValue()

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
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("blocks").child(username).addValueEventListener(blockedListener!!)
    }

    fun deleteMessage(messageId: String, friendId: String, isGroup: Boolean) {
        val me = safeMe()
        if (me.isEmpty()) return

        val path = if (isGroup) "group_messages/$friendId" else "messages/${chatKey(me, friendId)}"

        db.child(path).child(messageId).removeValue().addOnSuccessListener {
            db.child(path).orderByChild("timestamp").limitToLast(1).get().addOnSuccessListener { snapshot ->
                val lastMsg = snapshot.children.firstOrNull()?.getValue(Message::class.java)
                val meRef = db.child("chats").child(me).child(friendId)
                val frRef = db.child("chats").child(friendId).child(me)

                if (lastMsg != null) {
                    val updatedText = when {
                        lastMsg.audioUrl != null -> "🎤 Áudio"
                        lastMsg.imageUrl != null -> "📷 Imagem"
                        lastMsg.stickerUrl != null -> "Sticker"
                        else -> lastMsg.text
                    }
                    meRef.child("lastMessage").setValue(updatedText)
                    meRef.child("timestamp").setValue(lastMsg.timestamp)
                    if (!isGroup) {
                        frRef.child("lastMessage").setValue(updatedText)
                        frRef.child("timestamp").setValue(lastMsg.timestamp)
                    }
                } else {
                    meRef.child("lastMessage").setValue("Conversa vazia")
                    if (!isGroup) frRef.child("lastMessage").setValue("Conversa vazia")
                }
            }
        }
    }

    fun startCall(isVideo: Boolean, isGroup: Boolean, customRoomId: String) {
        val me = safeMe()
        val target = safeTarget()
        if (me.isEmpty() || target.isEmpty()) return

        viewModelScope.launch(errorHandler) {
            try {
                if (!isGroup && _blockedUsers.value.contains(target)) return@launch

                val roomId = customRoomId
                val callData = mapOf(
                    "callerId" to me,
                    "receiverId" to target,
                    "status" to "RINGING",
                    "isVideo" to isVideo,
                    "timestamp" to ServerValue.TIMESTAMP
                )
                db.child("calls").child(roomId).setValue(callData)

                val msgId = db.push().key ?: return@launch
                val msg = Message(
                    id = msgId,
                    senderId = me,
                    receiverId = target,
                    text = if (isVideo) "Chamada de vídeo" else "Chamada de áudio",
                    timestamp = System.currentTimeMillis(),
                    isGroup = isGroup,
                    senderName = _myName.value,
                    senderPhotoUrl = _myPhotoUrl.value,
                    callRoomId = roomId,
                    callType = if (isVideo) "VIDEO" else "AUDIO",
                    callStatus = "STARTING",
                    isCall = true
                )

                db.child("call_notifications").child(target).setValue(msg)
                db.child(messagePath(me, target, isGroup)).child(msgId).setValue(msg)
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
}
