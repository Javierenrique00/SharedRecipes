package com.co.recipes

import android.os.Bundle
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The Compose UI itself lives in :shared so Android needs no Compose setup.
        startSharedRecipes()
    }
}

