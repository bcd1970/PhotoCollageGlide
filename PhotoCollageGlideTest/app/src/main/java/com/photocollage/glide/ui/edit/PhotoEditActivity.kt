package com.photocollage.glide.ui.edit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.photocollage.glide.R

class PhotoEditActivity : AppCompatActivity() {
    
    companion object {
        private const val EXTRA_PHOTO_IDS = "extra_photo_ids"
        
        fun newIntent(context: Context, photoIds: LongArray): Intent {
            return Intent(context, PhotoEditActivity::class.java).apply {
                putExtra(EXTRA_PHOTO_IDS, photoIds)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        val photoIds = intent.getLongArrayExtra(EXTRA_PHOTO_IDS) ?: longArrayOf()
        
        // Create programmatic layout (following Android standards from CLAUDE.md)
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(getColor(android.R.color.background_dark))
        }
        
        // Handle system bars
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = insets.left,
                top = insets.top,
                right = insets.right,
                bottom = insets.bottom
            )
            windowInsets
        }
        
        // Title
        val titleText = TextView(this).apply {
            text = "Photo Edit Mode"
            textSize = 24f
            setTextColor(getColor(android.R.color.white))
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 16)
        }
        
        // Info text
        val infoText = TextView(this).apply {
            text = "Selected ${photoIds.size} photo(s)\n\nThis is a placeholder screen.\nPhoto editing features will be implemented here."
            textSize = 16f
            setTextColor(getColor(android.R.color.white))
            gravity = Gravity.CENTER
            setPadding(32, 16, 32, 32)
        }
        
        // Back button
        val backButton = Button(this).apply {
            text = "Back to Gallery"
            setOnClickListener {
                finish()
            }
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 32
            }
            this.layoutParams = layoutParams
        }
        
        // Add views to layout
        rootLayout.addView(titleText)
        rootLayout.addView(infoText)
        rootLayout.addView(backButton)
        
        setContentView(rootLayout)
    }
}