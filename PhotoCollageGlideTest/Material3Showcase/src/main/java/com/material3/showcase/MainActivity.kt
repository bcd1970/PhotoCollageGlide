package com.material3.showcase

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Directly launch the ExpandingButtonsActivity to show dots
        val intent = Intent(this, ExpandingButtonsActivity::class.java)
        startActivity(intent)
        finish() // Close MainActivity
    }
}