package com.photocollage.glide

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.photocollage.glide.R
import com.photocollage.glide.databinding.ActivityMainBinding
import com.photocollage.glide.ui.gallery.UltraFastPhotoAdapter
import com.photocollage.glide.ui.gallery.SinglePhotoAdapter
import com.photocollage.glide.ui.gallery.AlbumAdapter
import com.photocollage.glide.ui.gallery.SelectedPhotosAdapter
import com.photocollage.glide.ui.gallery.SpaceItemDecoration
import com.photocollage.glide.data.MediaRepository
import com.photocollage.glide.data.AlbumModel
import com.photocollage.glide.data.PhotoModel
import com.photocollage.glide.selection.SelectionManager
import com.photocollage.glide.ui.edit.PhotoEditActivity
import com.photocollage.glide.ui.edit.CollageEditActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var photoAdapter: UltraFastPhotoAdapter
    private lateinit var singlePhotoAdapter: SinglePhotoAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var selectedPhotosAdapter: SelectedPhotosAdapter
    private lateinit var mediaRepository: MediaRepository
    private lateinit var selectionManager: SelectionManager
    
    // View modes
    private enum class ViewMode {
        GRID,
        SINGLE_PHOTO,
        ALBUMS
    }
    
    // Navigation states  
    private enum class NavigationState {
        ALL_PHOTOS,
        ALBUMS,
        ALBUM_PHOTOS
    }
    
    private var currentViewMode = ViewMode.GRID
    private var currentState = NavigationState.ALL_PHOTOS
    private var currentAlbum: AlbumModel? = null
    private var currentPhotos: List<PhotoModel> = emptyList()
    
    // Separate position tracking for different contexts
    private var allPhotosPosition = 0
    private var albumPhotosPosition = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Load saved theme preference
        val sharedPref = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("dark_mode", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Instant UI - no delays, no splash screen
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Handle system bars properly
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Apply padding to bottom navigation to lift it above navigation bar
            binding.bottomNavigation.updatePadding(bottom = insets.bottom)
            
            windowInsets
        }
        
        // Initialize components
        mediaRepository = MediaRepository(this)
        selectionManager = SelectionManager.getInstance()
        selectedPhotosAdapter = SelectedPhotosAdapter()
        
        photoAdapter = UltraFastPhotoAdapter().apply {
            // Set photo click listener to track position for smooth single view navigation
            onPhotoClick = { position, photo ->
                // Update the correct position variable based on current navigation state
                when (currentState) {
                    NavigationState.ALL_PHOTOS -> allPhotosPosition = position
                    NavigationState.ALBUM_PHOTOS -> albumPhotosPosition = position
                    NavigationState.ALBUMS -> { /* No action needed for album list view */ }
                }
                // Optional: Switch to single view on photo click
                // switchToViewMode(ViewMode.SINGLE_PHOTO)
            }
        }
        singlePhotoAdapter = SinglePhotoAdapter().apply {
            // Set photo click listener to track position for smooth navigation
            onPhotoClick = { position, photo ->
                // Update the correct position variable based on current navigation state
                when (currentState) {
                    NavigationState.ALL_PHOTOS -> allPhotosPosition = position
                    NavigationState.ALBUM_PHOTOS -> albumPhotosPosition = position
                    NavigationState.ALBUMS -> { /* No action needed for album list view */ }
                }
            }
        }
        albumAdapter = AlbumAdapter { album ->
            // Handle album click
            currentAlbum = album
            navigateToAlbumPhotos(album)
        }
        
        // Setup RecyclerView immediately for instant UI
        setupRecyclerView()
        
        // Check permissions and load photos
        if (hasMediaPermission()) {
            // Set initial navigation context
            photoAdapter.setNavigationContext(UltraFastPhotoAdapter.NavigationContext.ALL_PHOTOS)
            loadAllPhotos()
            // Preload all photos to cache for instant album loading
            preloadPhotosCache()
        } else {
            requestMediaPermission()
        }
        
        // Setup UI interactions
        setupBottomNavigation()
        setupSinglePhotoViewPager()
        setupThemeSwitcher()
        setupSelectedPhotosStrip()
        setupEditCollageFab()
        setupSelectionObserver()
    }
    
    private fun setupThemeSwitcher() {
        binding.themeSwitcher.setOnClickListener {
            val sharedPref = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
            val currentNightMode = AppCompatDelegate.getDefaultNightMode()
            
            if (currentNightMode == AppCompatDelegate.MODE_NIGHT_YES) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                sharedPref.edit().putBoolean("dark_mode", false).apply()
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                sharedPref.edit().putBoolean("dark_mode", true).apply()
            }
        }
    }
    
    
    private fun setupRecyclerView() {
        binding.recyclerView.apply {
            // Fixed 3 columns grid layout with ultra-aggressive prefetching
            layoutManager = GridLayoutManager(this@MainActivity, 3).apply {
                initialPrefetchItemCount = 15 // Ultra-high prefetch for Glide
            }
            
            // Start with photo adapter
            adapter = photoAdapter
            
            // Ultra-aggressive performance optimizations for Glide
            setHasFixedSize(true)
            setItemViewCacheSize(100) // Massive cache for Glide
            recycledViewPool.setMaxRecycledViews(0, 200) // Huge pool
            
            // Disable animations for maximum speed
            itemAnimator = null
            
            // Add spacing between items
            if (itemDecorationCount == 0) {
                addItemDecoration(SpaceItemDecoration(1))
            }
            
            // Improve scrolling performance
            isNestedScrollingEnabled = false
        }
    }
    
    private fun loadAllPhotos() {
        binding.loadingProgress.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val photos = withContext(Dispatchers.IO) {
                    mediaRepository.loadAllPhotos()
                }
                
                withContext(Dispatchers.Main) {
                    binding.loadingProgress.visibility = View.GONE
                    currentPhotos = photos
                    
                    // Stop any ongoing scroll first
                    binding.recyclerView.stopScroll()
                    
                    // Submit data and wait for layout
                    photoAdapter.submitList(photos) {
                        // This callback runs after the list is updated
                        binding.recyclerView.post {
                            // Force scroll to top after data is fully loaded
                            binding.recyclerView.scrollToPosition(0)
                            (binding.recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(0, 0)
                        }
                    }
                    
                    binding.viewTitle.text = "All Photos (${photos.size})"
                    
                    // Setup ultra-aggressive Glide preloading AFTER data is loaded
                    photoAdapter.setupWithRecyclerView(binding.recyclerView)
                    
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.loadingProgress.visibility = View.GONE
                    Toast.makeText(
                        this@MainActivity,
                        "Error loading photos: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun loadAlbums() {
        binding.loadingProgress.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val albums = withContext(Dispatchers.IO) {
                    mediaRepository.loadAlbums()
                }
                
                withContext(Dispatchers.Main) {
                    binding.loadingProgress.visibility = View.GONE
                    albumAdapter.submitList(albums)
                    binding.viewTitle.text = "Albums (${albums.size})"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.loadingProgress.visibility = View.GONE
                    Toast.makeText(
                        this@MainActivity,
                        "Error loading albums: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun loadAlbumPhotos(album: AlbumModel) {
        binding.loadingProgress.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val photos = withContext(Dispatchers.IO) {
                    mediaRepository.loadAlbumPhotos(album.id)
                }
                
                withContext(Dispatchers.Main) {
                    binding.loadingProgress.visibility = View.GONE
                    currentPhotos = photos
                    albumPhotosPosition = 0 // Reset album photos position when loading new album
                    
                    // Stop any ongoing scroll first
                    binding.recyclerView.stopScroll()
                    
                    // Submit data and wait for layout
                    photoAdapter.submitList(photos) {
                        // This callback runs after the list is updated
                        binding.recyclerView.post {
                            // Force scroll to top after data is fully loaded
                            binding.recyclerView.scrollToPosition(0)
                            (binding.recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(0, 0)
                        }
                    }
                    
                    binding.viewTitle.text = "${album.name} (${photos.size})"
                    
                    // Setup ultra-aggressive Glide preloading for album photos too
                    photoAdapter.setupWithRecyclerView(binding.recyclerView)
                    
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.loadingProgress.visibility = View.GONE
                    Toast.makeText(
                        this@MainActivity,
                        "Error loading album photos: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun setupBottomNavigation() {
        // Set initial selection to grid view
        binding.bottomNavigation.selectedItemId = R.id.nav_grid
        
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_grid -> {
                    switchToViewMode(ViewMode.GRID)
                    true
                }
                R.id.nav_single -> {
                    switchToViewMode(ViewMode.SINGLE_PHOTO)
                    true
                }
                R.id.nav_albums -> {
                    when (currentState) {
                        NavigationState.ALL_PHOTOS, NavigationState.ALBUM_PHOTOS -> navigateToAlbums()
                        NavigationState.ALBUMS -> navigateToAllPhotos()
                    }
                    true
                }
                R.id.nav_all_photos -> {
                    navigateToAllPhotos()
                    true
                }
                else -> false
            }
        }
        
        // Set initial navigation visibility
        updateBottomNavigationVisibility()
    }
    
    private fun setupSinglePhotoViewPager() {
        binding.singlePhotoViewPager.adapter = singlePhotoAdapter
        
        // Simplified native-style configuration
        binding.singlePhotoViewPager.offscreenPageLimit = 1 // Minimal preloading for smooth scrolling
        binding.singlePhotoViewPager.isUserInputEnabled = true
        
        // Listen for page changes to update current position
        binding.singlePhotoViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                val stateStr = when (state) {
                    ViewPager2.SCROLL_STATE_IDLE -> "IDLE"
                    ViewPager2.SCROLL_STATE_DRAGGING -> "DRAGGING"
                    ViewPager2.SCROLL_STATE_SETTLING -> "SETTLING"
                    else -> "UNKNOWN"
                }
                Log.d("MainActivity", "ViewPager2 scroll state: $stateStr")
            }

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val oldPosition = when (currentState) {
                    NavigationState.ALL_PHOTOS -> allPhotosPosition
                    NavigationState.ALBUM_PHOTOS -> albumPhotosPosition
                    NavigationState.ALBUMS -> 0
                }
                Log.d("MainActivity", "ViewPager2 page selected: $position (was: $oldPosition)")
                
                // Update the correct position variable based on current state
                when (currentState) {
                    NavigationState.ALL_PHOTOS -> allPhotosPosition = position
                    NavigationState.ALBUM_PHOTOS -> albumPhotosPosition = position
                    NavigationState.ALBUMS -> { /* No action needed */ }
                }
            }
        })
    }
    
    private fun switchToViewMode(mode: ViewMode) {
        if (currentState == NavigationState.ALBUMS) {
            // Can't switch view modes in albums view
            return
        }
        
        currentViewMode = mode
        
        when (mode) {
            ViewMode.GRID -> {
                binding.recyclerView.visibility = View.VISIBLE
                binding.singlePhotoViewPager.visibility = View.GONE
            }
            ViewMode.SINGLE_PHOTO -> {
                binding.recyclerView.visibility = View.GONE
                binding.singlePhotoViewPager.visibility = View.VISIBLE
                
                // Get the correct position based on current navigation state
                val correctPosition = when (currentState) {
                    NavigationState.ALL_PHOTOS -> allPhotosPosition
                    NavigationState.ALBUM_PHOTOS -> albumPhotosPosition
                    NavigationState.ALBUMS -> 0 // Fallback, shouldn't happen
                }
                
                // Update single photo adapter with current photos and position
                singlePhotoAdapter.submitList(currentPhotos) {
                    // Callback after list is updated - ensures smooth positioning
                    if (currentPhotos.isNotEmpty() && correctPosition < currentPhotos.size) {
                        binding.singlePhotoViewPager.post {
                            binding.singlePhotoViewPager.setCurrentItem(correctPosition, false)
                        }
                    }
                }
            }
            ViewMode.ALBUMS -> {
                // This is handled by navigation methods
            }
        }
        
        // Update navigation visibility after mode change
        updateBottomNavigationVisibility()
    }
    
    private fun updateBottomNavigationVisibility() {
        val menu = binding.bottomNavigation.menu
        
        when (currentState) {
            NavigationState.ALL_PHOTOS -> {
                // In all photos view - hide current mode button, show others
                menu.findItem(R.id.nav_grid).isVisible = (currentViewMode != ViewMode.GRID)
                menu.findItem(R.id.nav_single).isVisible = (currentViewMode != ViewMode.SINGLE_PHOTO)
                menu.findItem(R.id.nav_albums).isVisible = true
                menu.findItem(R.id.nav_all_photos).isVisible = false // Already in all photos
            }
            
            NavigationState.ALBUMS -> {
                // Viewing album list - only show All Photos button
                menu.findItem(R.id.nav_grid).isVisible = false
                menu.findItem(R.id.nav_single).isVisible = false
                menu.findItem(R.id.nav_albums).isVisible = false
                menu.findItem(R.id.nav_all_photos).isVisible = true
            }
            
            NavigationState.ALBUM_PHOTOS -> {
                // Viewing photos from selected album - show mode buttons + albums + all photos
                menu.findItem(R.id.nav_grid).isVisible = (currentViewMode != ViewMode.GRID)
                menu.findItem(R.id.nav_single).isVisible = (currentViewMode != ViewMode.SINGLE_PHOTO)
                menu.findItem(R.id.nav_albums).isVisible = true
                menu.findItem(R.id.nav_all_photos).isVisible = true
            }
        }
    }
    
    
    
    private fun navigateToAllPhotos() {
        // Prepare adapter for context transition with smart cache management
        // Don't clear cache when transitioning from album to all photos to preserve performance
        val shouldClearCache = false
        photoAdapter.prepareForContextTransition(
            UltraFastPhotoAdapter.NavigationContext.ALL_PHOTOS,
            shouldClearCache
        )
        
        currentState = NavigationState.ALL_PHOTOS
        currentAlbum = null
        currentViewMode = ViewMode.GRID
        allPhotosPosition = 0 // Reset all photos position when navigating to all photos
        
        // Show grid view, hide single photo view
        binding.recyclerView.visibility = View.VISIBLE
        binding.singlePhotoViewPager.visibility = View.GONE
        
        // Stop any ongoing scroll immediately
        binding.recyclerView.stopScroll()
        
        // Set 3 columns for photos
        val layoutManager = binding.recyclerView.layoutManager as? GridLayoutManager
        if (layoutManager != null) {
            layoutManager.spanCount = 3
        } else {
            // Create new layout manager if needed
            binding.recyclerView.layoutManager = GridLayoutManager(this@MainActivity, 3).apply {
                initialPrefetchItemCount = 15
            }
        }
        
        // Set the adapter
        binding.recyclerView.adapter = photoAdapter
        
        // Load all photos - this will handle scrolling to top after data loads
        loadAllPhotos()
        
        // Update navigation visibility
        updateBottomNavigationVisibility()
    }
    
    private fun navigateToAlbums() {
        currentState = NavigationState.ALBUMS
        currentAlbum = null
        currentViewMode = ViewMode.ALBUMS
        
        // Show grid view, hide single photo view
        binding.recyclerView.visibility = View.VISIBLE
        binding.singlePhotoViewPager.visibility = View.GONE
        
        // Set 2 columns for albums (they need more space)
        (binding.recyclerView.layoutManager as GridLayoutManager).spanCount = 2
        
        // Update UI
        binding.recyclerView.adapter = albumAdapter
        
        // Load albums and cache all photos for instant album viewing
        loadAlbums()
        
        // Preload all photos and their images in background
        lifecycleScope.launch {
            // Cache all photos for instant album viewing
            withContext(Dispatchers.IO) {
                mediaRepository.loadAndCacheAllPhotos()
            }
        }
        
        // Update navigation visibility
        updateBottomNavigationVisibility()
    }
    
    
    private fun navigateToAlbumPhotos(album: AlbumModel) {
        // Prepare adapter for context transition
        val shouldClearCache = (currentState == NavigationState.ALL_PHOTOS)
        photoAdapter.prepareForContextTransition(
            UltraFastPhotoAdapter.NavigationContext.ALBUM_PHOTOS,
            shouldClearCache
        )
        
        currentState = NavigationState.ALBUM_PHOTOS
        currentViewMode = ViewMode.GRID
        currentAlbum = album
        albumPhotosPosition = 0 // Reset album photos position when navigating to new album
        
        // Show grid view, hide single photo view
        binding.recyclerView.visibility = View.VISIBLE
        binding.singlePhotoViewPager.visibility = View.GONE
        
        // Stop any ongoing scroll
        binding.recyclerView.stopScroll()
        
        // Set 3 columns for photos
        (binding.recyclerView.layoutManager as GridLayoutManager).spanCount = 3
        
        // Update UI
        binding.recyclerView.adapter = photoAdapter
        
        // Try to load from cache first for instant display
        val cachedPhotos = mediaRepository.getCachedAlbumPhotos(album.id)
        if (cachedPhotos != null && cachedPhotos.isNotEmpty()) {
            // Instant loading from cache
            currentPhotos = cachedPhotos
            albumPhotosPosition = 0 // Reset position for cached album photos
            photoAdapter.submitList(cachedPhotos) {
                binding.recyclerView.post {
                    binding.recyclerView.scrollToPosition(0)
                    (binding.recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(0, 0)
                }
            }
            binding.viewTitle.text = "${album.name} (${cachedPhotos.size})"
            photoAdapter.setupWithRecyclerView(binding.recyclerView)
        } else {
            // Fallback to async loading if cache not ready
            loadAlbumPhotos(album)
        }
        
        // Update navigation visibility
        updateBottomNavigationVisibility()
    }
    
    private fun hasMediaPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestMediaPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        ActivityCompat.requestPermissions(
            this,
            arrayOf(permission),
            PERMISSION_REQUEST_CODE
        )
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadAllPhotos()
                // Also preload cache when permission granted
                preloadPhotosCache()
            } else {
                Toast.makeText(
                    this,
                    "Permission required to show photos",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun preloadPhotosCache() {
        // Preload photos cache in background for instant album loading
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                mediaRepository.loadAndCacheAllPhotos()
            }
        }
    }
    

    override fun onDestroy() {
        super.onDestroy()
        // Clean up background threads and caches
        photoAdapter.cleanup()
        singlePhotoAdapter.cleanup()
    }

    override fun onBackPressed() {
        when (currentState) {
            NavigationState.ALBUM_PHOTOS -> navigateToAlbums()
            NavigationState.ALBUMS -> navigateToAllPhotos()
            else -> super.onBackPressed()
        }
    }
    
    private fun setupSelectedPhotosStrip() {
        binding.selectedPhotosStrip.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = selectedPhotosAdapter
            setHasFixedSize(true)
        }
    }
    
    private fun setupEditCollageFab() {
        binding.editCollageFab.setOnClickListener {
            val selectedPhotos = selectionManager.getSelectedPhotoModels()
            val photoIds = selectedPhotos.map { it.id }.toLongArray()
            
            when (selectionManager.selectionMode.value) {
                SelectionManager.SelectionMode.EDIT -> {
                    startActivity(PhotoEditActivity.newIntent(this, photoIds))
                }
                SelectionManager.SelectionMode.COLLAGE -> {
                    startActivity(CollageEditActivity.newIntent(this, photoIds))
                }
                SelectionManager.SelectionMode.NONE -> {
                    // Should not happen when FAB is visible
                }
            }
        }
    }
    
    private fun setupSelectionObserver() {
        lifecycleScope.launch {
            selectionManager.selectedPhotos.collect { selectedPhotoIds ->
                // Update selected photos strip
                val selectedPhotoModels = selectionManager.getSelectedPhotoModels()
                selectedPhotosAdapter.submitList(selectedPhotoModels)
                
                // Show/hide thumbnail strip with animation
                if (selectedPhotoIds.isNotEmpty()) {
                    if (binding.selectedPhotosStrip.visibility == View.GONE) {
                        binding.selectedPhotosStrip.visibility = View.VISIBLE
                        binding.selectedPhotosStrip.alpha = 0f
                        binding.selectedPhotosStrip.animate()
                            .alpha(1f)
                            .setDuration(200)
                            .start()
                    }
                } else {
                    if (binding.selectedPhotosStrip.visibility == View.VISIBLE) {
                        binding.selectedPhotosStrip.animate()
                            .alpha(0f)
                            .setDuration(200)
                            .withEndAction {
                                binding.selectedPhotosStrip.visibility = View.GONE
                            }
                            .start()
                    }
                }
                
                // Update all photo items to show/hide checkboxes in grid view
                photoAdapter.notifyItemRangeChanged(0, photoAdapter.itemCount, "selection_changed")
                
                // Update all photo items in single photo view
                singlePhotoAdapter.notifyItemRangeChanged(0, singlePhotoAdapter.itemCount, "selection_changed")
            }
        }
        
        lifecycleScope.launch {
            selectionManager.selectionMode.collect { mode ->
                // Show/hide FAB and update icon
                when (mode) {
                    SelectionManager.SelectionMode.NONE -> {
                        binding.editCollageFab.visibility = View.GONE
                    }
                    SelectionManager.SelectionMode.EDIT -> {
                        binding.editCollageFab.visibility = View.VISIBLE
                        binding.editCollageFab.setImageResource(R.drawable.ic_edit)
                    }
                    SelectionManager.SelectionMode.COLLAGE -> {
                        binding.editCollageFab.visibility = View.VISIBLE
                        binding.editCollageFab.setImageResource(R.drawable.ic_collage)
                    }
                }
            }
        }
    }
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
}