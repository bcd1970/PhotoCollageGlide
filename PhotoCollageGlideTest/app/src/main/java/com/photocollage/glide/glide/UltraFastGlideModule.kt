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
        // Ultra-aggressive memory cache - 250MB for zero loading delays
        val memoryCacheSizeBytes = 1024L * 1024L * 250L // 250MB
        builder.setMemoryCache(LruResourceCache(memoryCacheSizeBytes))
        
        // Massive bitmap pool for instant recycling - 200MB
        val bitmapPoolSizeBytes = 1024L * 1024L * 200L // 200MB
        builder.setBitmapPool(LruBitmapPool(bitmapPoolSizeBytes))
        
        // Enormous disk cache - 1GB for storing all images
        val diskCacheSizeBytes = 1024L * 1024L * 1024L // 1GB
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, diskCacheSizeBytes))
        
        // Maximum thread count for parallel processing
        builder.setSourceExecutor(GlideExecutor.newSourceBuilder()
            .setThreadCount(8) // Max threads for loading
            .setName("ultra-source")
            .build())
            
        builder.setDiskCacheExecutor(GlideExecutor.newDiskCacheBuilder()
            .setThreadCount(6) // Max threads for disk cache
            .setName("ultra-disk")
            .build())
        
        // Ultra-fast default options
        val defaultOptions = RequestOptions()
            .format(DecodeFormat.PREFER_RGB_565) // 50% memory reduction, faster decode
            .disallowHardwareConfig() // More predictable performance
            .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache everything
            .override(200, 200) // Fixed size = no resize calculations
            .encodeFormat(Bitmap.CompressFormat.JPEG)
            .encodeQuality(75) // Smaller files, faster loading
            .dontAnimate() // No animations = instant display
            .skipMemoryCache(false) // Always use memory cache
        
        builder.setDefaultRequestOptions(defaultOptions)
        
        // Minimal logging for max performance
        builder.setLogLevel(android.util.Log.ERROR)
    }
    
    override fun isManifestParsingEnabled(): Boolean = false
}