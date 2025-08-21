package com.material3.showcase.components

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.material3.showcase.databinding.ActivityTopappbarBinding

class TopAppBarActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityTopappbarBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityTopappbarBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Handle system bars
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top)
            windowInsets
        }
        
        // Setup collapsing toolbar
        binding.collapsingToolbar.title = "Collapsing Toolbars"
        
        // Setup toolbar
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Handle back navigation
        binding.topAppBar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }
}