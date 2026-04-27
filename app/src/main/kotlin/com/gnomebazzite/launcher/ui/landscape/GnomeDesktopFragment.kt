package com.gnomebazzite.launcher.ui.landscape

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.gnomebazzite.launcher.data.BuiltinApp
import com.gnomebazzite.launcher.databinding.FragmentGnomeDesktopBinding
import com.gnomebazzite.launcher.manager.BuiltinAppManager
import com.gnomebazzite.launcher.manager.LauncherViewModel
import com.gnomebazzite.launcher.ui.common.AppGridAdapter
import com.gnomebazzite.launcher.ui.common.DashAdapter
import com.gnomebazzite.launcher.ui.files.FileBrowserWindow
import com.gnomebazzite.launcher.ui.store.AppStoreActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private const val WALLPAPER_PICK = 1001

class GnomeDesktopFragment : BaseFragment<FragmentGnomeDesktopBinding>(
    FragmentGnomeDesktopBinding::inflate
) {
    private val vm: LauncherViewModel by activityViewModels()
    private lateinit var winMgr: DesktopWindowManager
    private lateinit var dashAdapter: DashAdapter
    private lateinit var activitiesAdapter: AppGridAdapter
    private var builtinApps: List<BuiltinApp> = emptyList()

    // State
    private var activitiesOpen = false

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() { updateClock(); clockHandler.postDelayed(this, 30_000) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupWindowManager()
        setupTopbar()
        setupDash()
        setupActivitiesOverlay()
        loadWallpaper()
        loadBuiltinApps()
        observeViewModel()
        updateClock()
    }

    // ══════════════════════════════════════════════
    // WINDOW MANAGER
    // ══════════════════════════════════════════════
    private fun setupWindowManager() {
        winMgr = DesktopWindowManager(requireContext(), binding.desktopWindowLayer)
        // Ne pas ouvrir de fenêtres automatiquement — l'utilisateur les ouvre
        binding.layoutDesktop.setOnClickListener { closeActivities(); closeAllPopups() }
    }

    // ══════════════════════════════════════════════
    // TOPBAR
    // ══════════════════════════════════════════════
    private fun setupTopbar() {
        binding.btnActivities.setOnClickListener { toggleActivities() }
        binding.btnBazziteMenu.setOnClickListener {
            closeActivities(); toggleBazzitePopup()
        }
        binding.layoutSystray.setOnClickListener { toggleSystrayPopup() }
        binding.btnAppStore.setOnClickListener {
            closeActivities(); closeAllPopups()
            startActivity(Intent(requireContext(), AppStoreActivity::class.java))
        }
        binding.btnWallpaper.setOnClickListener { pickWallpaper() }
    }

    private fun updateClock() {
        val now = Date()
        binding.tvTopbarClock.text =
            "${SimpleDateFormat("EEE d MMM", Locale.FRENCH).format(now)}  " +
            SimpleDateFormat("HH:mm", Locale.FRENCH).format(now)
    }

    // ══════════════════════════════════════════════
    // GNOME ACTIVITIES OVERLAY
    // Quand ouvert :
    //   - le bureau se dézoom (scale 0.82)
    //   - overlay sombre apparaît
    //   - barre de recherche en haut au centre
    //   - grille d'apps au centre (7 colonnes)
    //   - vignettes workspace sur la droite
    //   - taskbar reste visible et cliquable en dessous
    // ══════════════════════════════════════════════
    private fun setupActivitiesOverlay() {
        activitiesAdapter = AppGridAdapter(
            onAppClick = { app ->
                closeActivities()
                vm.launchApp(requireContext(), app)
            },
            onAppLongClick = { app ->
                vm.pinApp(app)
                showToast("${app.label} épinglé dans la taskbar")
            },
            onBuiltinAppClick = { app ->
                closeActivities()
                launchBuiltinApp(app)
            }
        )

        binding.rvActivitiesGrid.apply {
            layoutManager = GridLayoutManager(context, 7)
            adapter = activitiesAdapter
        }

        binding.etActivitiesSearch.addTextChangedListener { text ->
            filterActivitiesApps(text?.toString() ?: "")
        }

        // Fermer en cliquant sur le fond de l'overlay
        binding.activitiesOverlayBg.setOnClickListener { closeActivities() }

        // Workspace dots (décoratifs pour l'instant)
        setupWorkspaceDots()
    }

    private fun setupWorkspaceDots() {
        // Géré dans le layout XML
    }

    private fun toggleActivities() {
        if (activitiesOpen) closeActivities() else openActivities()
    }

    private fun openActivities() {
        if (activitiesOpen) return
        activitiesOpen = true
        closeAllPopups()

        // 1. Dézoom du bureau
        binding.desktopWindowLayer.animate()
            .scaleX(0.82f).scaleY(0.82f).translationY(-30f)
            .setDuration(260).setInterpolator(DecelerateInterpolator(1.5f)).start()

        // 2. Afficher l'overlay
        binding.activitiesOverlayContainer.visibility = View.VISIBLE
        binding.activitiesOverlayBg.alpha = 0f
        binding.activitiesOverlayBg.animate().alpha(1f).setDuration(240).start()

        // 3. Slide-in du panneau central depuis le bas
        binding.activitiesCenterPanel.translationY = 60f
        binding.activitiesCenterPanel.alpha = 0f
        binding.activitiesCenterPanel.animate()
            .translationY(0f).alpha(1f)
            .setDuration(280).setInterpolator(DecelerateInterpolator(1.6f)).start()

        // 4. Slide-in du panneau workspaces depuis la droite
        binding.activitiesWorkspacePanel.translationX = 50f
        binding.activitiesWorkspacePanel.alpha = 0f
        binding.activitiesWorkspacePanel.animate()
            .translationX(0f).alpha(1f)
            .setDuration(280).setInterpolator(DecelerateInterpolator(1.4f)).start()

        // 5. Stagger des icônes de la grille
        binding.rvActivitiesGrid.postDelayed({
            val lm = binding.rvActivitiesGrid.layoutManager as? GridLayoutManager ?: return@postDelayed
            for (i in 0 until lm.childCount) {
                lm.getChildAt(i)?.let { child ->
                    child.alpha = 0f; child.scaleX = 0.6f; child.scaleY = 0.6f
                    child.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setStartDelay((i * 16L).coerceAtMost(280L))
                        .setDuration(220).setInterpolator(OvershootInterpolator(1.2f)).start()
                }
            }
        }, 60)

        binding.btnActivities.isSelected = true
        binding.etActivitiesSearch.text?.clear()
        refreshActivitiesGrid()
    }

    fun closeActivities() {
        if (!activitiesOpen) return
        activitiesOpen = false

        // Re-zoom bureau
        binding.desktopWindowLayer.animate()
            .scaleX(1f).scaleY(1f).translationY(0f)
            .setDuration(220).setInterpolator(DecelerateInterpolator()).start()

        binding.activitiesOverlayBg.animate().alpha(0f).setDuration(180).start()
        binding.activitiesCenterPanel.animate().translationY(40f).alpha(0f).setDuration(200).start()
        binding.activitiesWorkspacePanel.animate().translationX(30f).alpha(0f)
            .setDuration(180)
            .withEndAction {
                binding.activitiesOverlayContainer.visibility = View.GONE
                binding.activitiesCenterPanel.translationY = 0f
                binding.activitiesWorkspacePanel.translationX = 0f
            }.start()

        binding.btnActivities.isSelected = false
        binding.etActivitiesSearch.text?.clear()
    }

    private fun refreshActivitiesGrid() {
        // Combiner apps Android installées + apps intégrées installées
        val androidApps = vm.installedApps.value
        activitiesAdapter.submitCombined(androidApps, builtinApps.filter { it.isInstalled })
    }

    private fun filterActivitiesApps(q: String) {
        val androidApps = vm.installedApps.value
        val filtered = if (q.isBlank()) androidApps
        else androidApps.filter { it.label.contains(q, ignoreCase = true) }
        val filteredBuiltin = if (q.isBlank()) builtinApps.filter { it.isInstalled }
        else builtinApps.filter { it.isInstalled && it.name.contains(q, ignoreCase = true) }
        activitiesAdapter.submitCombined(filtered, filteredBuiltin)
    }

    // ══════════════════════════════════════════════
    // DASH (taskbar du bas)
    // ══════════════════════════════════════════════
    private fun setupDash() {
        dashAdapter = DashAdapter(
            onAppClick = { app -> vm.launchApp(requireContext(), app) },
            onAppLongClick = { app ->
                vm.unpinApp(app.packageName)
                showToast("${app.label} retiré de la taskbar")
            }
        )
        binding.rvDash.apply {
            layoutManager = GridLayoutManager(context, 1, GridLayoutManager.HORIZONTAL, false)
            adapter = dashAdapter
        }
        binding.btnOverview.setOnClickListener { toggleActivities() }
        binding.btnFiles.setOnClickListener { openFileBrowser() }
        binding.btnAppStore.setOnClickListener {
            startActivity(Intent(requireContext(), AppStoreActivity::class.java))
        }
    }

    // ══════════════════════════════════════════════
    // EXPLORATEUR DE FICHIERS (fenêtre flottante)
    // ══════════════════════════════════════════════
    private fun openFileBrowser() {
        closeActivities(); closeAllPopups()
        val fbWindow = FileBrowserWindow(requireContext())
        winMgr.openCustomWindow("Fichiers", fbWindow, x = 80f, y = 40f, w = 520, h = 340)
    }

    // ══════════════════════════════════════════════
    // BUILT-IN APPS
    // ══════════════════════════════════════════════
    private fun loadBuiltinApps() {
        viewLifecycleOwner.lifecycleScope.launch {
            builtinApps = BuiltinAppManager.loadCatalog(requireContext())
        }
    }

    private fun launchBuiltinApp(app: BuiltinApp) {
        if (!app.isInstalled) {
            showToast("${app.name} n'est pas installée — ouvrez le Bazaar Store")
            startActivity(Intent(requireContext(), AppStoreActivity::class.java))
            return
        }
        val path = BuiltinAppManager.getEntryPointPath(requireContext(), app)
        val webView = android.webkit.WebView(requireContext()).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = true
            loadUrl("file://$path")
        }
        winMgr.openCustomWindow(
            title = "${app.iconEmoji}  ${app.name}",
            content = webView,
            x = 60f + (winMgr.getCount() * 20f),
            y = 40f + (winMgr.getCount() * 16f),
            w = 480, h = 320
        )
    }

    // ══════════════════════════════════════════════
    // POPUPS TOPBAR
    // ══════════════════════════════════════════════
    private fun toggleBazzitePopup() {
        val popup = binding.popupBazziteMenu
        if (popup.visibility == View.VISIBLE) animatePopupOut(popup)
        else { animatePopupIn(popup); setupBazziteMenuItems() }
    }

    private fun setupBazziteMenuItems() {
        data class Row(val root: View, val icon: TextView?, val label: TextView?, val act: () -> Unit)
        val rows = listOf(
            Row(binding.popupRowGaming.root,  binding.popupRowGaming.ivPopupRowIcon,  binding.popupRowGaming.tvPopupRowLabel)  {},
            Row(binding.popupRowSteam.root,   binding.popupRowSteam.ivPopupRowIcon,   binding.popupRowSteam.tvPopupRowLabel)   {},
            Row(binding.popupRowSettings.root,binding.popupRowSettings.ivPopupRowIcon,binding.popupRowSettings.tvPopupRowLabel) {},
            Row(binding.popupRowStore.root,   binding.popupRowStore.ivPopupRowIcon,   binding.popupRowStore.tvPopupRowLabel)   {
                startActivity(Intent(requireContext(), AppStoreActivity::class.java))
            }
        )
        listOf("🎮","💨","⚙️","📦").zip(
            listOf("Retour Gaming Mode","Lancer Steam","Paramètres système","Bazaar App Store")
        ).forEachIndexed { i, (icon, label) ->
            rows[i].icon?.text = icon; rows[i].label?.text = label
            rows[i].root.setOnClickListener { closeAllPopups(); rows[i].act() }
        }
    }

    private fun toggleSystrayPopup() {
        val popup = binding.popupSystray
        if (popup.visibility == View.VISIBLE) animatePopupOut(popup) else animatePopupIn(popup)
    }

    private fun animatePopupIn(v: View) {
        v.visibility = View.VISIBLE; v.alpha = 0f; v.scaleX = 0.92f; v.scaleY = 0.92f; v.translationY = -8f
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
            .setDuration(200).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun animatePopupOut(v: View) {
        v.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).translationY(-8f)
            .setDuration(150).withEndAction { v.visibility = View.GONE }.start()
    }

    private fun closeAllPopups() {
        listOf(binding.popupBazziteMenu, binding.popupSystray).forEach {
            if (it.visibility == View.VISIBLE) animatePopupOut(it)
        }
    }

    // ══════════════════════════════════════════════
    // WALLPAPER
    // ══════════════════════════════════════════════
    private fun pickWallpaper() {
        val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
        startActivityForResult(intent, WALLPAPER_PICK)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == WALLPAPER_PICK && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            saveWallpaper(uri)
            binding.ivWallpaper.setImageURI(uri)
        }
    }

    private fun saveWallpaper(uri: Uri) {
        val prefs = requireContext().getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("wallpaper_uri", uri.toString()).apply()
    }

    private fun loadWallpaper() {
        val prefs = requireContext().getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        val uriStr = prefs.getString("wallpaper_uri", null) ?: return
        try {
            binding.ivWallpaper.setImageURI(Uri.parse(uriStr))
        } catch (e: Exception) { /* garder le fond par défaut */ }
    }

    // ══════════════════════════════════════════════
    // OBSERVERS
    // ══════════════════════════════════════════════
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            vm.installedApps.collectLatest { apps ->
                activitiesAdapter.submitCombined(apps, builtinApps.filter { it.isInstalled })
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.pinnedApps.collectLatest { pinned -> dashAdapter.submitList(pinned) }
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        clockHandler.post(clockRunnable)
        loadBuiltinApps()
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockRunnable)
        if (activitiesOpen) closeActivities()
    }
}
