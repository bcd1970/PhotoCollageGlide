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
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions

@GlideModule
class AppGlideModule : AppGlideModule() {
    
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // Aggressive memory cache - 150MB for instant image display
        val memoryCacheSizeBytes = 1024L * 1024L * 150L // 150MB
        builder.setMemoryCache(LruResourceCache(memoryCacheSizeBytes))
        
        // Large bitmap pool for recycling - 100MB
        val bitmapPoolSizeBytes = 1024L * 1024L * 100L // 100MB
        builder.setBitmapPool(LruBitmapPool(bitmapPoolSizeBytes))
        
        // Huge disk cache - 500MB for storing thousands of thumbnails
        val diskCacheSizeBytes = 500L * 1024L * 1024L // 500MB
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, diskCacheSizeBytes))
        
        // Default options for all requests
        val defaultOptions = RequestOptions()
            .format(DecodeFormat.PREFER_RGB_565) // 50% memory reduction
            .disallowHardwareConfig() // More compatible
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // Cache processed images
            .override(200, 200) // Default size for grid items
            .encodeFormat(Bitmap.CompressFormat.JPEG)
            .encodeQuality(85) // Good quality/size balance
        
        builder.setDefaultRequestOptions(defaultOptions)
        
        // Log level for debugging (remove in production)
        builder.setLogLevel(android.util.Log.ERROR)
    }
    
    // Disable manifest parsing for faster startup
    override fun isManifestParsingEnabled(): Boolean = false
}