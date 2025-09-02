package com.photocollage.glide.data

import android.net.Uri

data class AlbumModel(
    val id: String,
    val name: String,
    val coverUri: Uri?,
    val photoCount: Int
)