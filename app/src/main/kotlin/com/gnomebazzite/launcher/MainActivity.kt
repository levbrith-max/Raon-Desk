package com.gnomebazzite.launcher

import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.gnomebazzite.launcher.databinding.ActivityMainBinding
import com.gnomebazzite.launcher.manager.LauncherViewModel
import com.gnomebazzite.launcher.ui.landscape.GnomeDesktopFragment
import com.gnomebazzite.launcher.ui.portrait.UbuntuTouchFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Plein écran immersif — on masque status bar et nav bar
        setupImmersiveMode()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Charger les apps installées
        viewModel.loadApps(this)

        // Observer l'état du drawer pour le back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.isDrawerOpen.value) {
                    viewModel.setDrawerOpen(false)
                }
                // Ne pas quitter le launcher (c'est le HOME)
            }
        })

        // Afficher le bon fragment selon l'orientation
        if (savedInstanceState == null) {
            loadFragmentForOrientation(resources.configuration.orientation)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        loadFragmentForOrientation(newConfig.orientation)
    }

    private fun loadFragmentForOrientation(orientation: Int) {
        val fragment: Fragment = when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> GnomeDesktopFragment()
            else -> UbuntuTouchFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, fragment)
            .commit()
    }

    override fun onResume() {
        super.onResume()
        setupImmersiveMode()
        viewModel.loadApps(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupImmersiveMode()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_HOME -> {
                viewModel.setDrawerOpen(false)
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (viewModel.isDrawerOpen.value) {
                    viewModel.setDrawerOpen(false)
                    true
                } else false
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun setupImmersiveMode() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }
}
