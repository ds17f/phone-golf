package com.phonegolf.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.phonegolf.app.ui.PhoneGolfApp
import com.phonegolf.app.ui.PhoneGolfTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SyncWorker.schedule(this)
        setContent {
            PhoneGolfTheme {
                PhoneGolfApp()
            }
        }
    }
}
