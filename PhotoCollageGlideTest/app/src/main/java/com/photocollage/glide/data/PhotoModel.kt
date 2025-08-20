package com.photocollage.glide.data

import android.net.Uri

data class PhotoModel(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long,
    val size: Long,
    val width: Int = 0,
    val height: Int = 0,
    val bucketId: String? = null,
    val bucketName: String? = null
)