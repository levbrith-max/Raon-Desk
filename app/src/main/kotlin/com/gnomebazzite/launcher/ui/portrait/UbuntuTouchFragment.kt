package com.gnomebazzite.launcher.ui.portrait

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.gnomebazzite.launcher.BaseFragment
import com.gnomebazzite.launcher.R
import com.gnomebazzite.launcher.data.AppInfo
import com.gnomebazzite.launcher.databinding.FragmentUbuntuTouchBinding
import com.gnomebazzite.launcher.manager.LauncherViewModel
import com.gnomebazzite.launcher.ui.common.AppGridAdapter
import com.gnomebazzite.launcher.ui.store.AppStoreActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class UbuntuTouchFragment : BaseFragment<FragmentUbuntuTouchBinding>(
    FragmentUbuntuTouchBinding::inflate
) {
    private val vm: LauncherViewModel by activityViewModels()
    private lateinit var appGridAdapter: AppGridAdapter
    private lateinit var quickGridAdapter: AppGridAdapter

    // Horloge
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            binding.tvPortraitTime.postDelayed(this, 30_000)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSideLauncher()
        setupQuickGrid()
        setupGnomeDrawer()
        setupNotifications()
        observeViewModel()
        updateClock()
    }

    // ─────────────────────────────────────────
    // SIDE LAUNCHER (barre verticale gauche)
    // ─────────────────────────────────────────

    private fun setupSideLauncher() {
        // Accueil — déjà sur l'écran d'accueil donc marqué actif
        binding.btnSideHome.isSelected = true

        // Dossier / App Drawer
        binding.btnSideFolder.setOnClickListener {
            if (binding.containerGnomeDrawer.visibility == View.VISIBLE) {
                closeGnomeDrawer()
            } else {
                openGnomeDrawer()
            }
        }

        // Apps épinglées dans le launcher latéral
        // (remplies dynamiquement depuis pinnedApps)

        // App Store
        binding.btnSideStore.setOnClickListener {
            startActivity(Intent(requireContext(), AppStoreActivity::class.java))
        }

        // Paramètres
        binding.btnSideSettings.setOnClickListener {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            } catch (e: Exception) { }
        }
    }

    // ─────────────────────────────────────────
    // GRILLE RAPIDE (apps visibles sur l'écran d'accueil)
    // ─────────────────────────────────────────

    private fun setupQuickGrid() {
        quickGridAdapter = AppGridAdapter(
            onAppClick = { app -> vm.launchApp(requireContext(), app) },
            onAppLongClick = { }
        )
        binding.rvQuickApps.apply {
            layoutManager = GridLayoutManager(context, 4)
            adapter = quickGridAdapter
        }
    }

    // ─────────────────────────────────────────
    // DRAWER GNOME (ouvert depuis le bouton folder)
    // ─────────────────────────────────────────

    private fun setupGnomeDrawer() {
        appGridAdapter = AppGridAdapter(
            onAppClick = { app ->
                vm.launchApp(requireContext(), app)
                closeGnomeDrawer()
            },
            onAppLongClick = { }
        )
        binding.rvDrawerApps.apply {
            layoutManager = GridLayoutManager(context, 4)
            adapter = appGridAdapter
        }

        // Recherche dans le drawer
        binding.etDrawerSearchPortrait.addTextChangedListener { text ->
            vm.setSearchQuery(text?.toString() ?: "")
            appGridAdapter.submitList(vm.filteredApps)
        }

        // Fermer en cliquant le fond
        binding.drawerScrim.setOnClickListener { closeGnomeDrawer() }
    }

    private fun openGnomeDrawer() {
        binding.containerGnomeDrawer.visibility = View.VISIBLE

        // Scrim : fade in
        binding.drawerScrim.alpha = 0f
        binding.drawerScrim.animate().alpha(1f).setDuration(220).start()

        // Panneau drawer : slide in depuis le bas + fade
        binding.cardPortraitDrawer.translationY = 160f
        binding.cardPortraitDrawer.alpha = 0f
        binding.cardPortraitDrawer.animate()
            .translationY(0f).alpha(1f)
            .setDuration(320)
            .setInterpolator(DecelerateInterpolator(1.7f))
            .start()

        // Animer les icônes en cascade (stagger)
        binding.rvDrawerApps.post {
            val lm = binding.rvDrawerApps.layoutManager as? GridLayoutManager ?: return@post
            for (i in 0 until lm.childCount) {
                lm.getChildAt(i)?.let { child ->
                    child.alpha = 0f; child.scaleX = 0.7f; child.scaleY = 0.7f
                    child.animate()
                        .alpha(1f).scaleX(1f).scaleY(1f)
                        .setStartDelay((i * 20L).coerceAtMost(280L))
                        .setDuration(240)
                        .setInterpolator(OvershootInterpolator(1.3f))
                        .start()
                }
            }
        }

        binding.btnSideFolder.isSelected = true
    }

    private fun closeGnomeDrawer() {
        binding.drawerScrim.animate().alpha(0f).setDuration(180).start()
        binding.cardPortraitDrawer.animate()
            .translationY(120f).alpha(0f)
            .setDuration(220)
            .setInterpolator(AccelerateInterpolator(1.4f))
            .withEndAction {
                binding.containerGnomeDrawer.visibility = View.GONE
                binding.etDrawerSearchPortrait.setText("")
                vm.setSearchQuery("")
            }.start()

        binding.btnSideFolder.isSelected = false
    }

    // ─────────────────────────────────────────
    // NOTIFICATIONS
    // ─────────────────────────────────────────

    private fun setupNotifications() {
        // Données statiques pour la démo
        // En production, écouter NotificationListenerService
    }

    // ─────────────────────────────────────────
    // OBSERVERS
    // ─────────────────────────────────────────

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            vm.installedApps.collectLatest { apps ->
                appGridAdapter.submitList(apps)
                // Afficher les 8 premières sur la grille rapide
                quickGridAdapter.submitList(apps.take(8))
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.pinnedApps.collectLatest { pinned ->
                updateSideLauncherPinned(pinned)
            }
        }
    }

    private fun updateSideLauncherPinned(apps: List<AppInfo>) {
        binding.sidelauncherPinnedContainer.removeAllViews()
        val dp = resources.displayMetrics.density
        apps.take(6).forEach { app ->
            val icon = ImageView(requireContext()).apply {
                setImageDrawable(app.icon)
                val size = (38 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(4, 4, 4, 4)
                }
                background = requireContext().getDrawable(R.drawable.launcher_icon_bg)
                clipToOutline = true
                setOnClickListener { vm.launchApp(requireContext(), app) }
            }
            binding.sidelauncherPinnedContainer.addView(icon)
        }
    }

    // ─────────────────────────────────────────
    // HORLOGE
    // ─────────────────────────────────────────

    private fun updateClock() {
        binding.tvPortraitTime.text = SimpleDateFormat("HH:mm", Locale.FRENCH).format(Date())
        binding.tvPortraitDate.text = SimpleDateFormat("EEE d MMM", Locale.FRENCH).format(Date())
    }

    override fun onResume() { super.onResume(); binding.tvPortraitTime.post(clockRunnable) }
    override fun onPause() { super.onPause(); binding.tvPortraitTime.removeCallbacks(clockRunnable) }
}
