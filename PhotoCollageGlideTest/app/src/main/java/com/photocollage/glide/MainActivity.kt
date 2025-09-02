package com.photocollage.glide

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
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
import com.photocollage.glide.ui.edit.UnifiedEditActivity
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
    
    // Separate position tracking for different contexts (single photo pager)
    private var allPhotosPosition = 0
    private var albumPhotosPosition = 0

    // Grid scroll state preservation (position + offset)
    private var allPhotosGridPos = 0
    private var allPhotosGridOffset = 0
    private var albumPhotosGridPos = 0
    private var albumPhotosGridOffset = 0
    private var albumsGridPos = 0
    private var albumsGridOffset = 0

    // Pending scroll restore request after data loads
    private var pendingScrollPos: Int? = null
    private var pendingScrollOffset: Int = 0
    
    
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
        // Ensure bottom navigation labels can wrap and show album name when needed
        updateAlbumsMenuTitle(null)
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
        // Photos grid
        binding.recyclerView.apply {
            // Fixed 3 columns grid layout with balanced prefetching
            layoutManager = GridLayoutManager(this@MainActivity, 3).apply {
                initialPrefetchItemCount = 9
            }
            
            // Start with photo adapter
            adapter = photoAdapter
            
            // Ultra-aggressive performance optimizations for Glide
            setHasFixedSize(true)
            setItemViewCacheSize(30)
            recycledViewPool.setMaxRecycledViews(0, 60)
            
            // Disable animations for maximum speed
            itemAnimator = null
            
            // Add spacing between items
            if (itemDecorationCount == 0) {
                addItemDecoration(SpaceItemDecoration(1))
            }
            
            // Improve scrolling performance
            isNestedScrollingEnabled = false
        }
        // Single RecyclerView is used for both photos and albums
    }

    private fun saveCurrentGridScroll() {
        val lm = binding.recyclerView.layoutManager as? GridLayoutManager ?: return
        val pos = lm.findFirstVisibleItemPosition()
        val firstView = binding.recyclerView.getChildAt(0)
        val offset = firstView?.top ?: 0
        when (currentState) {
            NavigationState.ALL_PHOTOS -> { allPhotosGridPos = pos; allPhotosGridOffset = offset }
            NavigationState.ALBUM_PHOTOS -> { albumPhotosGridPos = pos; albumPhotosGridOffset = offset }
            NavigationState.ALBUMS -> { albumsGridPos = pos; albumsGridOffset = offset }
        }
    }

    private fun requestRestoreGridScrollFor(state: NavigationState) {
        when (state) {
            NavigationState.ALL_PHOTOS -> { pendingScrollPos = allPhotosGridPos; pendingScrollOffset = allPhotosGridOffset }
            NavigationState.ALBUM_PHOTOS -> { pendingScrollPos = albumPhotosGridPos; pendingScrollOffset = albumPhotosGridOffset }
            NavigationState.ALBUMS -> { pendingScrollPos = albumsGridPos; pendingScrollOffset = albumsGridOffset }
        }
    }

    private fun applyPendingScrollIfAny() {
        val pos = pendingScrollPos ?: return
        val lm = binding.recyclerView.layoutManager as? GridLayoutManager ?: return
        binding.recyclerView.post {
            lm.scrollToPositionWithOffset(pos, pendingScrollOffset)
        }
        pendingScrollPos = null
        pendingScrollOffset = 0
    }

    // Removed transition overlay logic for simplicity
    
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
                    
                    // Submit data and restore scroll if requested
                    photoAdapter.submitList(photos) {
                        applyPendingScrollIfAny()
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
                    albumAdapter.submitList(albums) {
                        applyPendingScrollIfAny()
                    }
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
                    
                    // Submit data and restore scroll if requested
                    photoAdapter.submitList(photos) {
                        applyPendingScrollIfAny()
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
            }

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val oldPosition = when (currentState) {
                    NavigationState.ALL_PHOTOS -> allPhotosPosition
                    NavigationState.ALBUM_PHOTOS -> albumPhotosPosition
                    NavigationState.ALBUMS -> 0
                }
                // Reset zoom on the page we just left
                val internalRv = try { binding.singlePhotoViewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView } catch (_: Throwable) { null }
                val oldVh = internalRv?.findViewHolderForAdapterPosition(oldPosition) as? com.photocollage.glide.ui.gallery.SinglePhotoAdapter.SinglePhotoViewHolder
                oldVh?.resetZoomToFit()
                
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
        // Save current scroll from previous state
        saveCurrentGridScroll()
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
        // Restore previous grid scroll position when returning to all photos
        requestRestoreGridScrollFor(NavigationState.ALL_PHOTOS)
        
        // Show grid view, hide single photo view
        binding.recyclerView.visibility = View.VISIBLE
        binding.singlePhotoViewPager.visibility = View.GONE

        // Ensure photos grid config
        val lmAll = binding.recyclerView.layoutManager as? GridLayoutManager
        if (lmAll != null) {
            lmAll.spanCount = 3
        } else {
            binding.recyclerView.layoutManager = GridLayoutManager(this@MainActivity, 3).apply {
                initialPrefetchItemCount = 9
            }
        }

        // Set adapter to photos
        binding.recyclerView.adapter = photoAdapter
        
        // Set 3 columns for photos
        val layoutManager = binding.recyclerView.layoutManager as? GridLayoutManager
        if (layoutManager != null) {
            layoutManager.spanCount = 3
        } else {
            // Create new layout manager if needed
            binding.recyclerView.layoutManager = GridLayoutManager(this@MainActivity, 3).apply {
                initialPrefetchItemCount = 9
            }
        }
        
        // Reset Albums label title when returning to all photos
        updateAlbumsMenuTitle(null)

        // Load all photos and restore previous scroll when ready
        loadAllPhotos()
        
        // Update navigation visibility
        updateBottomNavigationVisibility()
    }
    
    private fun navigateToAlbums() {
        // Save current scroll before switching
        saveCurrentGridScroll()
        currentState = NavigationState.ALBUMS
        currentAlbum = null
        currentViewMode = ViewMode.ALBUMS
        
        // Show grid view, hide single photo view
        binding.recyclerView.visibility = View.VISIBLE
        binding.singlePhotoViewPager.visibility = View.GONE

        // Configure grid for albums (2 columns)
        val lmAlbums = binding.recyclerView.layoutManager as? GridLayoutManager
        if (lmAlbums != null) {
            lmAlbums.spanCount = 2
        } else {
            binding.recyclerView.layoutManager = GridLayoutManager(this@MainActivity, 2).apply {
                initialPrefetchItemCount = 8
            }
        }

        // Switch adapter to albums
        binding.recyclerView.adapter = albumAdapter
        
        // Reset Albums label title
        updateAlbumsMenuTitle(null)

        // Request restore of previous albums scroll
        requestRestoreGridScrollFor(NavigationState.ALBUMS)

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
        // Save current scroll before switching
        saveCurrentGridScroll()

        // Prepare adapter for context transition
        val shouldClearCache = (currentState == NavigationState.ALL_PHOTOS)
        photoAdapter.prepareForContextTransition(
            UltraFastPhotoAdapter.NavigationContext.ALBUM_PHOTOS,
            shouldClearCache
        )
        
        currentState = NavigationState.ALBUM_PHOTOS
        currentViewMode = ViewMode.GRID
        currentAlbum = album
        // Restore previous album grid scroll if available
        requestRestoreGridScrollFor(NavigationState.ALBUM_PHOTOS)
        
        // Show grid view, hide single photo view
        binding.recyclerView.visibility = View.VISIBLE
        binding.singlePhotoViewPager.visibility = View.GONE
        
        // Configure grid for photos (3 columns)
        val lmPhotos = binding.recyclerView.layoutManager as? GridLayoutManager
        if (lmPhotos != null) {
            lmPhotos.spanCount = 3
        } else {
            binding.recyclerView.layoutManager = GridLayoutManager(this@MainActivity, 3).apply {
                initialPrefetchItemCount = 9
            }
        }

        // Ensure photo adapter is set
        binding.recyclerView.adapter = photoAdapter
        
        // Update Albums item label to include album name under the icon
        updateAlbumsMenuTitle(album.name)
        
        // Try to load from cache first for instant display
        val cachedPhotos = mediaRepository.getCachedAlbumPhotos(album.id)
        if (cachedPhotos != null && cachedPhotos.isNotEmpty()) {
            // Instant loading from cache
            currentPhotos = cachedPhotos
            photoAdapter.submitList(cachedPhotos) {
                applyPendingScrollIfAny()
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

    private fun updateAlbumsMenuTitle(albumName: String?) {
        val maxAlbumLen = 28
        val safeName = albumName?.trim()?.take(maxAlbumLen)
        val title = if (safeName.isNullOrEmpty()) {
            getString(R.string.albums_label_default)
        } else {
            safeName
        }
        val item = binding.bottomNavigation.menu.findItem(R.id.nav_albums)
        item.title = title

        // Allow label to span two lines when album name is long
        makeBottomNavigationLabelsMultiline()
    }

    private fun makeBottomNavigationLabelsMultiline() {
        val menuView = binding.bottomNavigation.getChildAt(0) as? android.view.ViewGroup ?: return
        val largeIds = intArrayOf(
            com.google.android.material.R.id.navigation_bar_item_large_label_view
        )
        val smallIds = intArrayOf(
            com.google.android.material.R.id.navigation_bar_item_small_label_view
        )
        for (i in 0 until menuView.childCount) {
            val itemView = menuView.getChildAt(i)
            for (id in largeIds) {
                val tv = itemView.findViewById<TextView?>(id)
                tv?.let {
                    it.isSingleLine = false
                    it.maxLines = 2
                    it.ellipsize = android.text.TextUtils.TruncateAt.END
                    it.textAlignment = View.TEXT_ALIGNMENT_CENTER
                }
            }
            for (id in smallIds) {
                val tv = itemView.findViewById<TextView?>(id)
                tv?.let {
                    it.isSingleLine = false
                    it.maxLines = 2
                    it.ellipsize = android.text.TextUtils.TruncateAt.END
                    it.textAlignment = View.TEXT_ALIGNMENT_CENTER
                }
            }
        }
        menuView.requestLayout()
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
                    startActivity(UnifiedEditActivity.newIntent(this, photoIds))
                }
                SelectionManager.SelectionMode.COLLAGE -> {
                    startActivity(UnifiedEditActivity.newIntent(this, photoIds))
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
