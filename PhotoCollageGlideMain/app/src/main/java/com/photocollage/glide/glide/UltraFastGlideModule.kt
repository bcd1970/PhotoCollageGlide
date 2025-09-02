package com.photocollage.glide.glide

import android.content.Context
import android.graphics.Bitmap
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.load.engine.executor.GlideExecutor
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions

@GlideModule
class UltraFastGlideModule : AppGlideModule() {
    
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // Use device-appropriate memory sizing (Samsung Gallery approach)
        val calculator = com.bumptech.glide.load.engine.cache.MemorySizeCalculator.Builder(context)
            .setMemoryCacheScreens(3.0f) // Cache 3 screens worth of images
            .setBitmapPoolScreens(4.0f) // Pool for 4 screens of bitmaps
            .build()
        
        builder.setMemoryCache(LruResourceCache(calculator.memoryCacheSize.toLong()))
        builder.setBitmapPool(LruBitmapPool(calculator.bitmapPoolSize.toLong()))
        
        // Reasonable disk cache - 500MB
        val diskCacheSizeBytes = 1024L * 1024L * 500L // 500MB
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, diskCacheSizeBytes))
        
        // Optimal thread count for photo gallery
        builder.setSourceExecutor(GlideExecutor.newSourceBuilder()
            .setThreadCount(4) // Optimal for image loading
            .setName("photo-source")
            .build())
            
        builder.setDiskCacheExecutor(GlideExecutor.newDiskCacheBuilder()
            .setThreadCount(2) // Background disk operations
            .setName("photo-disk")
            .build())
        
        // NO default options - let each RequestOptions configure individually
        // This prevents conflicts with single photo full-resolution loading
        
        // Debug logging for development
        builder.setLogLevel(android.util.Log.DEBUG)
    }
    
    override fun isManifestParsingEnabled(): Boolean = false
}