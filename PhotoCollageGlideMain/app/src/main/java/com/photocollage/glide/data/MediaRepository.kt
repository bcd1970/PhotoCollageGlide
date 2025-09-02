package com.photocollage.glide.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepository(private val context: Context) {
    
    private val contentResolver: ContentResolver = context.contentResolver
    
    // Cache for instant album photo loading
    private var cachedPhotos: List<PhotoModel>? = null
    
    suspend fun loadAlbums(): List<AlbumModel> = withContext(Dispatchers.IO) {
        val albums = mutableMapOf<String, AlbumModel>()
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            
            while (cursor.moveToNext()) {
                val bucketId = cursor.getString(bucketIdColumn) ?: continue
                val bucketName = cursor.getString(bucketNameColumn) ?: "Unknown"
                
                if (!albums.containsKey(bucketId)) {
                    // First photo in this album becomes the cover
                    val photoId = cursor.getLong(idColumn)
                    val coverUri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        photoId.toString()
                    )
                    
                    albums[bucketId] = AlbumModel(
                        id = bucketId,
                        name = bucketName,
                        coverUri = coverUri,
                        photoCount = 1
                    )
                } else {
                    // Increment photo count
                    albums[bucketId] = albums[bucketId]!!.copy(
                        photoCount = albums[bucketId]!!.photoCount + 1
                    )
                }
            }
        }
        
        albums.values.toList().sortedByDescending { it.photoCount }
    }
    
    suspend fun loadAndCacheAllPhotos(): List<PhotoModel> = withContext(Dispatchers.IO) {
        // Load and cache all photos if not already cached
        if (cachedPhotos == null) {
            cachedPhotos = loadAllPhotosInternal()
        }
        cachedPhotos ?: emptyList()
    }
    
    suspend fun loadAlbumPhotos(bucketId: String): List<PhotoModel> = withContext(Dispatchers.IO) {
        // First try to use cached photos for instant loading
        cachedPhotos?.let { photos ->
            return@withContext photos.filter { it.bucketId == bucketId }
        }
        
        // Fallback to loading from MediaStore if cache is not available
        loadAlbumPhotosFromMediaStore(bucketId)
    }
    
    private fun loadAlbumPhotosFromMediaStore(bucketId: String): List<PhotoModel> {
        val photos = mutableListOf<PhotoModel>()
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        
        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(bucketId)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val displayName = cursor.getString(nameColumn) ?: "Unknown"
                val dateAdded = cursor.getLong(dateColumn)
                val size = cursor.getLong(sizeColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val albumId = cursor.getString(bucketIdColumn)
                val bucketName = cursor.getString(bucketNameColumn)
                
                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                
                val photo = PhotoModel(
                    id = id,
                    uri = uri,
                    displayName = displayName,
                    dateAdded = dateAdded,
                    size = size,
                    width = width,
                    height = height,
                    bucketId = albumId,
                    bucketName = bucketName
                )
                
                photos.add(photo)
            }
        }
        
        return photos
    }
    
    suspend fun loadAllPhotos(): List<PhotoModel> = withContext(Dispatchers.IO) {
        // Also update cache when loading all photos
        val photos = loadAllPhotosInternal()
        cachedPhotos = photos
        photos
    }
    
    
    private fun loadAllPhotosInternal(): List<PhotoModel> {
        val photos = mutableListOf<PhotoModel>()
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val displayName = cursor.getString(nameColumn) ?: "Unknown"
                val dateAdded = cursor.getLong(dateColumn)
                val size = cursor.getLong(sizeColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val bucketId = cursor.getString(bucketIdColumn)
                val bucketName = cursor.getString(bucketNameColumn)
                
                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                
                val photo = PhotoModel(
                    id = id,
                    uri = uri,
                    displayName = displayName,
                    dateAdded = dateAdded,
                    size = size,
                    width = width,
                    height = height,
                    bucketId = bucketId,
                    bucketName = bucketName
                )
                
                photos.add(photo)
            }
        }
        
        return photos
    }
    
    
    // Synchronous method to get cached album photos instantly
    fun getCachedAlbumPhotos(bucketId: String): List<PhotoModel>? {
        return cachedPhotos?.filter { it.bucketId == bucketId }
    }
    
    fun clearCache() {
        cachedPhotos = null
    }
}