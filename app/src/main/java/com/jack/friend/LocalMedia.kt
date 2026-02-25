package com.jack.friend

import android.net.Uri

data class LocalMedia(
    val uri: Uri,
    val isVideo: Boolean,
    val duration: Long = 0,
    val dateAdded: Long = 0
)
