package dev.lumo.gallery

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import dev.lumo.gallery.core.ui.theme.ShiguangTheme
import dev.lumo.gallery.navigation.AppRoot

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ShiguangTheme {
                AppRoot()
            }
        }
    }
}
