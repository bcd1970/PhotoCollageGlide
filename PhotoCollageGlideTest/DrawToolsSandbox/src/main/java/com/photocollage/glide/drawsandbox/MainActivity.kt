package com.photocollage.glide.drawsandbox

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.photocollage.glide.drawsandbox.databinding.ActivityMainBinding
// Shapes-focused sandbox; Curve/Line/Brush tools removed

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    companion object { private const val TAG = "DrawSmooth" }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Force dark mode for this sandbox
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Bottom bar: only Eraser
        binding.bottomBar.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.action_eraser -> {
                    binding.drawingSurface.clearAll()
                    true
                }
                else -> true // no-op for now
            }
        }

        // Initial state: select eraser
        binding.bottomBar.selectedItemId = R.id.action_eraser
        // Text tool removed in reset; no IME proxy wiring
    }
}
