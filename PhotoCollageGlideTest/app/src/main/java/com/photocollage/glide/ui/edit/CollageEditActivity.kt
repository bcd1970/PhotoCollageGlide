package com.photocollage.glide.ui.edit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.photocollage.glide.R
import com.photocollage.glide.data.MediaRepository
import kotlinx.coroutines.launch

class CollageEditActivity : AppCompatActivity() {
    
    companion object {
        private const val EXTRA_PHOTO_IDS = "extra_photo_ids"
        
        fun newIntent(context: Context, photoIds: LongArray): Intent {
            return Intent(context, CollageEditActivity::class.java).apply {
                putExtra(EXTRA_PHOTO_IDS, photoIds)
            }
        }
    }
    
    private lateinit var mediaRepository: MediaRepository
    private var currentPhotoIds: LongArray = longArrayOf()
    private var currentPhotoIndex = 0
    private val photoEditorViews = mutableListOf<PhotoEditorView>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        currentPhotoIds = intent.getLongArrayExtra(EXTRA_PHOTO_IDS) ?: longArrayOf()
        mediaRepository = MediaRepository(this)
        
        setupCollageLayout()
        loadPhotosForCollage()
    }
    
    private fun setupCollageLayout() {
        // Create root layout for collage editor
        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(getColor(android.R.color.black))
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
        
        // Create collage container based on number of photos
        val collageContainer = createCollageContainer()
        
        // Create editor controls overlay
        val controlsOverlay = createCollageControls()
        
        // Add views to root layout
        rootLayout.addView(collageContainer)
        rootLayout.addView(controlsOverlay)
        
        setContentView(rootLayout)
    }
    
    private fun createCollageContainer(): ViewGroup {
        val photoCount = currentPhotoIds.size
        
        return when {
            photoCount <= 2 -> createTwoPhotoLayout()
            photoCount <= 4 -> createFourPhotoLayout()
            else -> createGridLayout()
        }
    }
    
    private fun createTwoPhotoLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            // Create two photo editor views
            repeat(minOf(2, currentPhotoIds.size)) { index ->
                val photoEditorView = PhotoEditorView(this@CollageEditActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1.0f // Equal weight
                    ).apply {
                        if (index > 0) topMargin = 4
                    }
                }
                
                photoEditorViews.add(photoEditorView)
                addView(photoEditorView)
            }
        }
    }
    
    private fun createFourPhotoLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            // Create two rows
            repeat(2) { row ->
                val rowLayout = LinearLayout(this@CollageEditActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1.0f // Equal weight
                    ).apply {
                        if (row > 0) topMargin = 4
                    }
                }
                
                // Add photos to this row (max 2 per row)
                val photosInRow = minOf(2, currentPhotoIds.size - (row * 2))
                repeat(photosInRow) { col ->
                    val photoIndex = (row * 2) + col
                    if (photoIndex < currentPhotoIds.size) {
                        val photoEditorView = PhotoEditorView(this@CollageEditActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                1.0f // Equal weight
                            ).apply {
                                if (col > 0) leftMargin = 4
                            }
                        }
                        
                        photoEditorViews.add(photoEditorView)
                        rowLayout.addView(photoEditorView)
                    }
                }
                
                addView(rowLayout)
            }
        }
    }
    
    private fun createGridLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            // Calculate grid dimensions
            val photoCount = currentPhotoIds.size
            val cols = when {
                photoCount <= 6 -> 2
                photoCount <= 9 -> 3
                else -> 4
            }
            val rows = (photoCount + cols - 1) / cols
            
            // Create grid rows
            repeat(rows) { row ->
                val rowLayout = LinearLayout(this@CollageEditActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1.0f // Equal weight
                    ).apply {
                        if (row > 0) topMargin = 4
                    }
                }
                
                // Add photos to this row
                repeat(cols) { col ->
                    val photoIndex = (row * cols) + col
                    if (photoIndex < currentPhotoIds.size) {
                        val photoEditorView = PhotoEditorView(this@CollageEditActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                1.0f // Equal weight
                            ).apply {
                                if (col > 0) leftMargin = 4
                            }
                        }
                        
                        photoEditorViews.add(photoEditorView)
                        rowLayout.addView(photoEditorView)
                    }
                }
                
                addView(rowLayout)
            }
        }
    }
    
    private fun createCollageControls(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(getColor(android.R.color.transparent))
            
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = 32
            }
            
            // Reset all button
            val resetAllButton = Button(this@CollageEditActivity).apply {
                text = "Reset All"
                setOnClickListener {
                    photoEditorViews.forEach { it.resetImageTransformation() }
                }
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 16
                }
                this.layoutParams = layoutParams
            }
            
            // Layout selection button
            val layoutButton = Button(this@CollageEditActivity).apply {
                text = "Layout"
                setOnClickListener {
                    // TODO: Implement layout selection dialog
                    Toast.makeText(this@CollageEditActivity, "Layout selection coming soon", Toast.LENGTH_SHORT).show()
                }
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 16
                }
                this.layoutParams = layoutParams
            }
            
            // Back button
            val backButton = Button(this@CollageEditActivity).apply {
                text = "Back to Gallery"
                setOnClickListener {
                    finish()
                }
            }
            
            // Add buttons to layout
            addView(resetAllButton)
            addView(layoutButton)
            addView(backButton)
        }
    }
    
    private fun loadPhotosForCollage() {
        if (currentPhotoIds.isEmpty()) {
            Toast.makeText(this, "No photos selected for collage", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        lifecycleScope.launch {
            try {
                val photos = mediaRepository.loadAllPhotos()
                
                currentPhotoIds.forEachIndexed { index, photoId ->
                    val photo = photos.find { it.id == photoId }
                    if (photo != null && index < photoEditorViews.size) {
                        photoEditorViews[index].loadImage(photo.uri)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@CollageEditActivity, "Error loading photos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // PhotoEditorViews will handle their own cleanup in onDetachedFromWindow
        photoEditorViews.clear()
    }
}