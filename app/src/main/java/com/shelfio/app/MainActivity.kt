package com.shelfio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.shelfio.app.ui.navigation.ShelfioNavHost
import com.shelfio.app.ui.theme.ShelfioTheme

/** Única actividad de la app: pinta todas las pantallas con Compose a través de ShelfioNavHost. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShelfioTheme {
                ShelfioNavHost()
            }
        }
    }
}
