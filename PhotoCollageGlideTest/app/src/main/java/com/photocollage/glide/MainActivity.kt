package com.photocollage.glide

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.photocollage.glide.databinding.ActivityMainBinding
import com.photocollage.glide.ui.gallery.PhotoAdapter
import com.photocollage.glide.ui.gallery.AlbumAdapter
import com.photocollage.glide.ui.gallery.SpaceItemDecoration
import com.photocollage.glide.data.MediaRepository
import com.photocollage.glide.data.AlbumModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var photoAdapter: PhotoAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var mediaRepository: MediaRepository
    
    // Navigation states
    private enum class NavigationState {
        ALL_PHOTOS,
        ALBUMS,
        ALBUM_PHOTOS
    }
    
    private var currentState = NavigationState.ALL_PHOTOS
    private var currentAlbum: AlbumModel? = null
    
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
            
            // Apply padding to bottom bar to lift it above navigation bar
            binding.bottomBar.updatePadding(bottom = insets.bottom)
            
            windowInsets
        }
        
        // Initialize components
        mediaRepository = MediaRepository(this)
        photoAdapter = PhotoAdapter()
        albumAdapter = AlbumAdapter { album ->
            // Handle album click
            currentAlbum = album
            navigateToAlbumPhotos(album)
        }
        
        // Setup RecyclerView immediately for instant UI
        setupRecyclerView()
        
        // Check permissions and load photos
        if (hasMediaPermission()) {
            loadAllPhotos()
        } else {
            requestMediaPermission()
        }
        
        // Setup UI interactions
        setupBottomBar()
        setupThemeSwitcher()
    }
    
    private fun setupThemeSwitcher() {
        updateBottomBarTheme() // Set initial theme
        
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
            
            // Update colors immediately without recreating activity
            updateBottomBarTheme()
        }
    }
    
    private fun updateBottomBarTheme() {
        val isDarkMode = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES ||
                        (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM &&
                         resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)
        
        val cardView = binding.bottomBar.getChildAt(0) as? androidx.cardview.widget.CardView
        
        // Find button card views
        val navButtonCard = binding.navigationButton.parent as? androidx.cardview.widget.CardView
        val backButtonCard = binding.backButtonCard as? androidx.cardview.widget.CardView
        
        if (isDarkMode) {
            // Dark theme colors - PURE BLACK background with elevated card
            cardView?.setCardBackgroundColor(android.graphics.Color.parseColor("#1A1A1A")) // Slightly lighter than pure black
            cardView?.cardElevation = 32f // Force high elevation
            binding.viewTitle.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            
            // Vibrant button colors for dark mode
            navButtonCard?.setCardBackgroundColor(android.graphics.Color.parseColor("#00E5FF"))
            binding.navigationButton.setTextColor(android.graphics.Color.parseColor("#000000"))
            
            backButtonCard?.setCardBackgroundColor(android.graphics.Color.parseColor("#FF4444"))
            binding.backButton.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            
            binding.themeSwitcher.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#00E5FF"))
            
            // Don't change the main background - let it use theme colors
        } else {
            // Light theme colors
            cardView?.setCardBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"))
            binding.viewTitle.setTextColor(android.graphics.Color.parseColor("#212121"))
            
            navButtonCard?.setCardBackgroundColor(android.graphics.Color.parseColor("#2196F3"))
            binding.navigationButton.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            
            backButtonCard?.setCardBackgroundColor(android.graphics.Color.parseColor("#FF5722"))
            binding.backButton.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            
            binding.themeSwitcher.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#00ACC1"))
            
            // Don't change the main background - let it use theme colors
        }
    }
    
    private fun setupRecyclerView() {
        binding.recyclerView.apply {
            // Fixed 3 columns grid layout
            layoutManager = GridLayoutManager(this@MainActivity, 3).apply {
                initialPrefetchItemCount = 6
            }
            
            // Start with photo adapter
            adapter = photoAdapter
            
            // Performance optimizations
            setHasFixedSize(true)
            setItemViewCacheSize(30)
            recycledViewPool.setMaxRecycledViews(0, 50)
            
            // Disable animations for maximum speed
            itemAnimator = null
            
            // Add spacing between items
            if (itemDecorationCount == 0) {
                addItemDecoration(SpaceItemDecoration(1))
            }
            
            // Improve scrolling performance
            isNestedScrollingEnabled = false
            
            // Drawing cache for smoother scrolling
            isDrawingCacheEnabled = true
            drawingCacheQuality = View.DRAWING_CACHE_QUALITY_HIGH
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
                    photoAdapter.submitList(photos)
                    binding.viewTitle.text = "All Photos (${photos.size})"
                    // Force scroll to top after photos are loaded
                    binding.recyclerView.scrollToPosition(0)
                    (binding.recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(0, 0)
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
        // Load albums exactly the same way as All Photos
        binding.loadingProgress.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val photos = withContext(Dispatchers.IO) {
                    mediaRepository.loadAlbumPhotos(album.id)
                }
                
                withContext(Dispatchers.Main) {
                    binding.loadingProgress.visibility = View.GONE
                    photoAdapter.submitList(photos)
                    binding.viewTitle.text = "${album.name} (${photos.size})"
                    // Scroll to top to show album photos from beginning
                    binding.recyclerView.scrollToPosition(0)
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
    
    
    
    private fun setupBottomBar() {
        // Navigation button
        binding.navigationButton.setOnClickListener {
            when (currentState) {
                NavigationState.ALL_PHOTOS -> navigateToAlbums()
                NavigationState.ALBUMS -> navigateToAllPhotos()
                NavigationState.ALBUM_PHOTOS -> navigateToAlbums()
            }
        }
        
        // Back button
        binding.backButton.setOnClickListener {
            navigateToAlbums()
        }
    }
    
    private fun navigateToAllPhotos() {
        currentState = NavigationState.ALL_PHOTOS
        currentAlbum = null
        
        // Clear the adapter's data first to force a complete reset
        photoAdapter.submitList(null)
        
        // Set 3 columns for photos
        val layoutManager = binding.recyclerView.layoutManager as? GridLayoutManager
        if (layoutManager != null) {
            layoutManager.spanCount = 3
            // Force scroll to top immediately
            layoutManager.scrollToPositionWithOffset(0, 0)
        } else {
            // Create new layout manager if needed
            binding.recyclerView.layoutManager = GridLayoutManager(this@MainActivity, 3).apply {
                initialPrefetchItemCount = 6
            }
        }
        
        // Set the adapter
        binding.recyclerView.adapter = photoAdapter
        
        // Update UI
        binding.navigationButton.text = "📁 Albums"
        binding.backButtonCard.visibility = View.GONE
        
        // Scroll to top immediately
        binding.recyclerView.scrollToPosition(0)
        
        // Load all photos - this will repopulate the adapter
        loadAllPhotos()
        
        // Force another scroll after data is loaded
        binding.recyclerView.postDelayed({
            binding.recyclerView.scrollToPosition(0)
            (binding.recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(0, 0)
        }, 200)
    }
    
    private fun navigateToAlbums() {
        currentState = NavigationState.ALBUMS
        currentAlbum = null
        
        // Set 2 columns for albums (they need more space)
        (binding.recyclerView.layoutManager as GridLayoutManager).spanCount = 2
        
        // Update UI
        binding.recyclerView.adapter = albumAdapter
        binding.navigationButton.text = "🖼️ All Photos"
        binding.backButtonCard.visibility = View.GONE
        
        // Load albums and cache all photos for instant album viewing
        loadAlbums()
        
        // Preload all photos and their images in background
        lifecycleScope.launch {
            // Cache all photos for instant album viewing
            withContext(Dispatchers.IO) {
                mediaRepository.loadAndCacheAllPhotos()
            }
        }
    }
    
    
    private fun navigateToAlbumPhotos(album: AlbumModel) {
        currentState = NavigationState.ALBUM_PHOTOS
        
        // Set 3 columns for photos
        (binding.recyclerView.layoutManager as GridLayoutManager).spanCount = 3
        
        // Update UI
        binding.recyclerView.adapter = photoAdapter
        binding.navigationButton.text = "📁 Albums"
        binding.backButtonCard.visibility = View.VISIBLE
        
        // Load album photos
        loadAlbumPhotos(album)
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
            } else {
                Toast.makeText(
                    this,
                    "Permission required to show photos",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    override fun onBackPressed() {
        when (currentState) {
            NavigationState.ALBUM_PHOTOS -> navigateToAlbums()
            NavigationState.ALBUMS -> navigateToAllPhotos()
            else -> super.onBackPressed()
        }
    }
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
}