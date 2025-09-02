package com.photocollage.glide.ui.edit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import android.graphics.PointF
import android.graphics.RectF
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.photocollage.glide.R
import com.photocollage.glide.data.MediaRepository
import com.photocollage.glide.data.PhotoModel
import kotlinx.coroutines.launch

/**
 * Unified photo editor activity that handles both single photo and collage editing.
 * Automatically adapts UI and functionality based on the number of photos provided.
 */
class UnifiedEditActivity : AppCompatActivity() {
    
    companion object {
        private const val EXTRA_PHOTO_IDS = "extra_photo_ids"
        
        fun newIntent(context: Context, photoIds: LongArray): Intent {
            return Intent(context, UnifiedEditActivity::class.java).apply {
                putExtra(EXTRA_PHOTO_IDS, photoIds)
            }
        }
    }
    
    private lateinit var unifiedEditorView: UnifiedPhotoEditorView
    private lateinit var mediaRepository: MediaRepository
    private var currentPhotoIds: LongArray = longArrayOf()
    private var editorMode = UnifiedPhotoEditorView.EditorMode.SINGLE
    
    // UI components
    private lateinit var controlsBar: LinearLayout
    private lateinit var resetButton: Button
    private lateinit var layoutButton: Button
    private lateinit var backButton: Button
    // Navigation overlays
    private var collageNavigationOverlay: CollageNavigationOverlay? = null
    private var photoNavigationOverlay: PhotoNavigationOverlay? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        currentPhotoIds = intent.getLongArrayExtra(EXTRA_PHOTO_IDS) ?: longArrayOf()
        mediaRepository = MediaRepository(this)
        
        // Determine mode based on photo count
        editorMode = if (currentPhotoIds.size == 1) {
            UnifiedPhotoEditorView.EditorMode.SINGLE
        } else {
            UnifiedPhotoEditorView.EditorMode.COLLAGE
        }
        
        setupLayout()
        loadPhotos()
    }
    
    private fun setupLayout() {
        // Create root constraint layout
        val rootLayout = ConstraintLayout(this).apply {
            setBackgroundColor(getColor(android.R.color.black))
        }
        
        // Handle system bars properly
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
        
        // Create editor controls bar first
        controlsBar = createEditorControlsBar()
        controlsBar.id = View.generateViewId()
        
        // Create navigation overlay based on editor mode
        when (editorMode) {
            UnifiedPhotoEditorView.EditorMode.COLLAGE -> {
                collageNavigationOverlay = CollageNavigationOverlay(this).apply {
                    id = View.generateViewId()
                    layoutParams = ConstraintLayout.LayoutParams(
                        0, // match_constraint (full width)
                        ConstraintLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                        endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                        bottomToTop = controlsBar.id
                        marginStart = 16
                        marginEnd = 16
                        bottomMargin = 8
                    }
                }
            }
            UnifiedPhotoEditorView.EditorMode.SINGLE -> {
                photoNavigationOverlay = PhotoNavigationOverlay(this).apply {
                    id = View.generateViewId()
                    layoutParams = ConstraintLayout.LayoutParams(
                        ConstraintLayout.LayoutParams.WRAP_CONTENT, // Fixed size for overlay
                        ConstraintLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        // Position in bottom-right corner, overlaying the photo editor
                        bottomToTop = controlsBar.id
                        endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                        marginEnd = 16
                        bottomMargin = 16
                    }
                    // Initially visible but transparent - alpha animation will control opacity
                    visibility = View.VISIBLE
                }
            }
        }
        
        // Create UnifiedPhotoEditorView constrained based on mode
        val (topConstraintId, bottomConstraintId) = when (editorMode) {
            UnifiedPhotoEditorView.EditorMode.COLLAGE -> {
                // For collage: between top and navigation overlay (which is above controls)
                Pair(ConstraintLayout.LayoutParams.PARENT_ID, collageNavigationOverlay?.id ?: controlsBar.id)
            }
            UnifiedPhotoEditorView.EditorMode.SINGLE -> {
                // For single photo: full height from top to controls (overlay will be on top)
                Pair(ConstraintLayout.LayoutParams.PARENT_ID, controlsBar.id)
            }
        }
        unifiedEditorView = UnifiedPhotoEditorView(this).apply {
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(
                0, // match_constraint
                0  // match_constraint
            ).apply {
                topToTop = topConstraintId
                bottomToTop = bottomConstraintId
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                topMargin = 0
            }
        }
        
        // Add views to root layout
        rootLayout.addView(unifiedEditorView)
        collageNavigationOverlay?.let { rootLayout.addView(it) }
        photoNavigationOverlay?.let { rootLayout.addView(it) }
        rootLayout.addView(controlsBar)
        
        setContentView(rootLayout)
    }
    
    private fun createEditorControlsBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(getColor(android.R.color.transparent))
            
            layoutParams = ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                bottomMargin = 32
            }
            
            // Create buttons based on mode
            createModeSpecificButtons()
        }
    }
    
    private fun LinearLayout.createModeSpecificButtons() {
        // Reset button (common to both modes)
        resetButton = Button(this@UnifiedEditActivity).apply {
            text = when (editorMode) {
                UnifiedPhotoEditorView.EditorMode.SINGLE -> "Reset"
                UnifiedPhotoEditorView.EditorMode.COLLAGE -> "Reset All"
            }
            setOnClickListener {
                unifiedEditorView.resetTransformations()
                Toast.makeText(this@UnifiedEditActivity, "Reset to initial state", Toast.LENGTH_SHORT).show()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 16
            }
        }
        
        // Mode-specific middle buttons
        when (editorMode) {
            UnifiedPhotoEditorView.EditorMode.SINGLE -> {
                // For single photo: Add navigation buttons if multiple photos (like current PhotoEditActivity)
                if (currentPhotoIds.size > 1) {
                    val prevButton = Button(this@UnifiedEditActivity).apply {
                        text = "Previous"
                        isEnabled = false // TODO: Implement navigation
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            marginEnd = 16
                        }
                    }
                    
                    val nextButton = Button(this@UnifiedEditActivity).apply {
                        text = "Next"
                        isEnabled = false // TODO: Implement navigation
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            marginEnd = 16
                        }
                    }
                    
                    addView(prevButton)
                    addView(nextButton)
                }
            }
            
            UnifiedPhotoEditorView.EditorMode.COLLAGE -> {
                // For collage: Add layout selection button
                layoutButton = Button(this@UnifiedEditActivity).apply {
                    text = "Layout"
                    setOnClickListener {
                        // TODO: Implement layout selection dialog
                        Toast.makeText(this@UnifiedEditActivity, "Layout selection coming soon", Toast.LENGTH_SHORT).show()
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = 16
                    }
                }
                addView(layoutButton)
            }
        }
        
        // Back button (common to both modes)
        backButton = Button(this@UnifiedEditActivity).apply {
            text = "Back to Gallery"
            setOnClickListener {
                finish()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Add common buttons
        addView(resetButton)
        addView(backButton)
    }
    
    private fun loadPhotos() {
        if (currentPhotoIds.isEmpty()) {
            Toast.makeText(this, "No photos selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        lifecycleScope.launch {
            try {
                val photos = mediaRepository.loadAllPhotos()
                val selectedPhotos = mutableListOf<PhotoModel>()
                for (photoId in currentPhotoIds) {
                    val photo = photos.find { it.id == photoId }
                    if (photo != null) {
                        selectedPhotos.add(photo)
                    }
                }
                
                if (selectedPhotos.isEmpty()) {
                    Toast.makeText(this@UnifiedEditActivity, "No valid photos found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }
                
                // Load photos into unified editor view
                val photoUris = selectedPhotos.map { it.uri }
                unifiedEditorView.setPhotos(photoUris)
                
                // Set up navigation overlay based on editor mode
                when (editorMode) {
                    UnifiedPhotoEditorView.EditorMode.COLLAGE -> {
                        if (collageNavigationOverlay != null) {
                            setupCollageNavigationOverlay(photoUris)
                        }
                    }
                    UnifiedPhotoEditorView.EditorMode.SINGLE -> {
                        if (photoNavigationOverlay != null && photoUris.isNotEmpty()) {
                            setupPhotoNavigationOverlay(photoUris[0])
                        }
                    }
                }
                
                // Update title based on mode
                title = when (editorMode) {
                    UnifiedPhotoEditorView.EditorMode.SINGLE -> "Photo Editor"
                    UnifiedPhotoEditorView.EditorMode.COLLAGE -> "Collage Editor (${selectedPhotos.size} photos)"
                }
                
            } catch (e: Exception) {
                Toast.makeText(this@UnifiedEditActivity, "Error loading photos: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    
    /**
     * Set up navigation overlay for collage mode
     */
    private fun setupCollageNavigationOverlay(photoUris: List<android.net.Uri>) {
        collageNavigationOverlay?.let { overlay ->
            android.util.Log.d("UnifiedEditActivity", "Navigation overlay exists, setting up...")
            
            // Recursive function to retry until dimensions are available
            fun trySetupWithRetry(retryCount: Int = 0) {
                unifiedEditorView.post {
                    val collageDimensions = unifiedEditorView.getCollageDimensions()
                    
                    if (collageDimensions.x > 0 && collageDimensions.y > 0) {
                        // Set photos and dimensions in overlay
                        overlay.setPhotos(photoUris, collageDimensions.x, collageDimensions.y)
                        
                        // Set up viewport change listener
                        unifiedEditorView.setViewportChangeListener { viewportBounds, isVisible ->
                            viewportBounds?.let { bounds ->
                                overlay.updateViewport(bounds, isVisible)
                            }
                        }
                        
                        // Set up navigation click listener
                        overlay.setOnNavigationClickListener { collageX, collageY ->
                            unifiedEditorView.navigateToPoint(collageX, collageY)
                        }
                        
                        // Initial viewport update
                        val initialViewport = unifiedEditorView.getViewportBounds()
                        if (initialViewport != null) {
                            overlay.updateViewport(initialViewport, true)
                        }
                    } else if (retryCount < 10) {
                        // Retry after 100ms if dimensions not ready and we haven't exceeded max retries
                        unifiedEditorView.postDelayed({
                            trySetupWithRetry(retryCount + 1)
                        }, 100)
                    } else {
                    }
                }
            }
            
            trySetupWithRetry()
        }
    }
    
    /**
     * Set up navigation overlay for single photo mode
     */
    private fun setupPhotoNavigationOverlay(photoUri: android.net.Uri) {
        photoNavigationOverlay?.let { overlay ->
            android.util.Log.d("UnifiedEditActivity", "PhotoNavigationOverlay exists, setting up...")
            
            // For single photo mode, we need to get the image dimensions
            // This will be handled by the UnifiedPhotoEditorView when the photo loads
            unifiedEditorView.setPhotoNavigationOverlay(overlay, photoUri)
        }
    }
    
    /**
     * Enable debug mode for the editor view
     */
    fun setDebugMode(enabled: Boolean) {
        if (::unifiedEditorView.isInitialized) {
            unifiedEditorView.setDebugMode(enabled)
        }
    }
    
    /**
     * Get current editor mode
     */
    fun getEditorMode(): UnifiedPhotoEditorView.EditorMode = editorMode
    
    /**
     * Get current photo URIs
     */
    fun getCurrentPhotoUris(): List<android.net.Uri> {
        return if (::unifiedEditorView.isInitialized) {
            unifiedEditorView.getCurrentPhotoUris()
        } else {
            emptyList()
        }
    }
    
    /**
     * Check if photos are loaded
     */
    fun arePhotosLoaded(): Boolean {
        return if (::unifiedEditorView.isInitialized) {
            unifiedEditorView.arePhotosLoaded()
        } else {
            false
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Check if we need to setup navigation overlay again
        if (::unifiedEditorView.isInitialized) {
            val photoUris = unifiedEditorView.getCurrentPhotoUris()
            if (photoUris.isNotEmpty()) {
                when (editorMode) {
                    UnifiedPhotoEditorView.EditorMode.COLLAGE -> {
                        if (collageNavigationOverlay != null) {
                            setupCollageNavigationOverlay(photoUris)
                        }
                    }
                    UnifiedPhotoEditorView.EditorMode.SINGLE -> {
                        if (photoNavigationOverlay != null) {
                            setupPhotoNavigationOverlay(photoUris[0])
                        }
                    }
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // UnifiedPhotoEditorView will handle its own cleanup in onDetachedFromWindow
    }
    
    override fun onBackPressed() {
        // Add confirmation dialog if transformations have been made
        if (::unifiedEditorView.isInitialized) {
            // TODO: Check if transformations have been made
            // For now, just go back
            super.onBackPressed()
        } else {
            super.onBackPressed()
        }
    }
}