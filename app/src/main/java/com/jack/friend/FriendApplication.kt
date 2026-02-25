package com.jack.friend

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.cloudinary.android.MediaManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class FriendApplication : Application(), Application.ActivityLifecycleCallbacks, ImageLoaderFactory {
    
    private var activityCount = 0
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground

    companion object {
        lateinit var instance: FriendApplication
            private set
        var isAppInForeground: Boolean = false
        var currentOpenedChatId: String? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        registerActivityLifecycleCallbacks(this)
        
        val config = mapOf(
            "cloud_name" to "dagdvifyz",
            "api_key" to "515648516698279",
            "api_secret" to "CKubGcQuYFyGat2n5I0Q0eZi-QQ"
        )
        MediaManager.init(this, config)
    }

    fun clearAppData() {
        try {
            // Limpa SharedPreferences conhecidas
            val prefs = listOf("friend_prefs", "security_prefs", "ui_prefs", "recent_emojis_prefs")
            prefs.forEach { name ->
                getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
            }

            // Limpa Cache (arquivos de áudio, imagens temporárias)
            cacheDir.deleteRecursively()
            
            // Limpa arquivos internos se houver
            filesDir.deleteRecursively()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    override fun onActivityStarted(activity: Activity) {
        activityCount++
        if (activityCount == 1) {
            isAppInForeground = true
            _isForeground.value = true
        }
    }

    override fun onActivityStopped(activity: Activity) {
        activityCount--
        if (activityCount == 0) {
            isAppInForeground = false
            _isForeground.value = false
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
