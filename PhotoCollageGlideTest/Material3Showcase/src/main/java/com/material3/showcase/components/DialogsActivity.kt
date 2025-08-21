package com.material3.showcase.components

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.material3.showcase.R

class DialogsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = "Material 3 Dialogs"
    }
}