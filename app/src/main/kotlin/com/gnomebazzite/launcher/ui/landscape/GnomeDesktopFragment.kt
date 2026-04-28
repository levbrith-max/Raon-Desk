package com.gnomebazzite.launcher.ui.landscape

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.gnomebazzite.launcher.BaseFragment
import com.gnomebazzite.launcher.R
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

class GnomeDesktopFragment : BaseFragment<FragmentGnomeDesktopBinding>(
    FragmentGnomeDesktopBinding::inflate
) {
    private val vm: LauncherViewModel by activityViewModels()
    private lateinit var winMgr: DesktopWindowManager
    private lateinit var appGridAdapter: AppGridAdapter
    private lateinit var dockAdapter: DashAdapter
    private var builtinApps: List<BuiltinApp> = emptyList()
    private var appGridOpen = false

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() { updateClock(); clockHandler.postDelayed(this, 30_000) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupWindowManager()
        setupTopbar()
        setupDock()
        setupAppGrid()
        loadWallpaper()
        loadBuiltinApps()
        observeViewModel()
        updateClock()
    }

    private fun setupWindowManager() {
        winMgr = DesktopWindowManager(requireContext(), binding.desktopWindowLayer)
        binding.layoutDesktop.setOnClickListener {
            closeAllPopups()
            if (appGridOpen) closeAppGrid()
        }
    }

    private fun setupTopbar() {
        binding.btnBazziteIcon.setOnClickListener {
            if (binding.popupBazziteMenu.visibility == View.VISIBLE) animatePopupOut(binding.popupBazziteMenu)
            else { closeAllPopups(); buildBazziteMenu(); animatePopupIn(binding.popupBazziteMenu) }
        }
        binding.layoutSystray.setOnClickListener {
            if (binding.popupSystray.visibility == View.VISIBLE) animatePopupOut(binding.popupSystray)
            else { closeAllPopups(); animatePopupIn(binding.popupSystray) }
        }
        binding.btnWallpaper.setOnClickListener { pickWallpaper() }
        binding.btnAppStore.setOnClickListener {
            startActivity(Intent(requireContext(), AppStoreActivity::class.java))
        }
        binding.etAppGridSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { filterAppGrid(s?.toString() ?: "") }
        })
    }

    private fun updateClock() {
        val now = Date()
        binding.tvTopbarClock.text =
            "${SimpleDateFormat("EEE d MMM", Locale.FRENCH).format(now)}  " +
            SimpleDateFormat("HH:mm", Locale.FRENCH).format(now)
    }

    // Menu Bazzite identique à la vidéo
    private fun buildBazziteMenu() {
        val list = binding.bazziteMenuList
        list.removeAllViews()
        val items = listOf(
            "⠿" to "App Grid",   "📁" to "Files",
            "🎮" to "Steam",      "🍷" to "Lutris",
            "📊" to "Mission Center", "🖥️" to "Terminal",
            "📦" to "DistroShelf", "🛒" to "Software",
            "🏪" to "Warehouse",  "🔧" to "Extension Manager",
            "🔄" to "System Update", "ℹ️" to "About My System"
        )
        items.forEach { (icon, label) ->
            list.addView(buildMenuRow(icon, label) {
                animatePopupOut(binding.popupBazziteMenu)
                when (label) {
                    "App Grid" -> toggleAppGrid()
                    "Files"    -> openFileBrowser()
                    "Terminal" -> winMgr.openTerminal()
                    "Software" -> startActivity(Intent(requireContext(), AppStoreActivity::class.java))
                    else       -> {}
                }
            })
        }
    }

    private fun buildMenuRow(icon: String, label: String, onClick: () -> Unit): View {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding((10*dp).toInt(), (5*dp).toInt(), (16*dp).toInt(), (5*dp).toInt())
            isClickable = true; isFocusable = true
            background = requireContext().getDrawable(R.drawable.popup_row_bg)
        }
        row.addView(TextView(requireContext()).apply {
            text = icon; textSize = 12f
            layoutParams = LinearLayout.LayoutParams((22*dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0,0,(8*dp).toInt(),0) }
        })
        row.addView(TextView(requireContext()).apply {
            text = label; textSize = 11f; setTextColor(Color.parseColor("#DDFFFFFF"))
        })
        row.setOnClickListener { onClick() }
        return row
    }

    private fun setupDock() {
        dockAdapter = DashAdapter(
            onAppClick = { app -> vm.launchApp(requireContext(), app); closeAppGrid() },
            onAppLongClick = { app ->
                vm.unpinApp(app.packageName)
                Toast.makeText(requireContext(), "${app.label} retiré", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvDock.apply {
            layoutManager = GridLayoutManager(context, 1, GridLayoutManager.HORIZONTAL, false)
            adapter = dockAdapter
        }
        binding.btnDockFiles.setOnClickListener { closeAppGrid(); openFileBrowser() }
        binding.btnGridClose.setOnClickListener { closeAppGrid() }
    }

    private fun setupAppGrid() {
        appGridAdapter = AppGridAdapter(
            onAppClick = { app -> vm.launchApp(requireContext(), app); closeAppGrid() },
            onAppLongClick = { app ->
                vm.pinApp(app)
                Toast.makeText(requireContext(), "${app.label} épinglé", Toast.LENGTH_SHORT).show()
            },
            onBuiltinAppClick = { app -> closeAppGrid(); launchBuiltinApp(app) }
        )
        binding.rvAppGrid.apply {
            layoutManager = GridLayoutManager(context, 8)
            adapter = appGridAdapter
        }
        binding.appGridBg.setOnClickListener { closeAppGrid() }
        binding.btnGridNextPage.setOnClickListener {
            binding.rvAppGrid.smoothScrollBy(0, binding.rvAppGrid.height)
        }
        binding.btnGridPrevPage.setOnClickListener {
            binding.rvAppGrid.smoothScrollBy(0, -binding.rvAppGrid.height)
        }
    }

    fun toggleAppGrid() { if (appGridOpen) closeAppGrid() else openAppGrid() }

    private fun openAppGrid() {
        if (appGridOpen) return
        appGridOpen = true; closeAllPopups()
        binding.appGridOverlay.visibility = View.VISIBLE
        binding.appGridBg.alpha = 0f
        binding.appGridBg.animate().alpha(1f).setDuration(240).start()
        binding.workspaceThumbnails.apply { translationY = -40f; alpha = 0f
            animate().translationY(0f).alpha(1f).setDuration(260).setInterpolator(DecelerateInterpolator(1.5f)).start() }
        binding.rvAppGrid.apply { alpha = 0f; translationY = 30f
            animate().alpha(1f).translationY(0f).setDuration(280).setStartDelay(80).setInterpolator(DecelerateInterpolator(1.4f)).start() }
        binding.dockContainer.apply { translationY = 60f; alpha = 0f
            animate().translationY(0f).alpha(1f).setDuration(260).setStartDelay(60).setInterpolator(DecelerateInterpolator(1.4f)).start() }
        binding.rvAppGrid.postDelayed({
            val lm = binding.rvAppGrid.layoutManager as? GridLayoutManager ?: return@postDelayed
            for (i in 0 until lm.childCount) {
                lm.getChildAt(i)?.let { child ->
                    child.alpha = 0f; child.scaleX = 0.6f; child.scaleY = 0.6f
                    child.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setStartDelay((i * 14L).coerceAtMost(240L))
                        .setDuration(200).setInterpolator(OvershootInterpolator(1.1f)).start()
                }
            }
        }, 100)
        binding.tvTopbarClock.visibility = View.GONE
        binding.tvCurrentApp.visibility = View.GONE
        binding.searchBarContainer.visibility = View.VISIBLE
        binding.searchBarContainer.alpha = 0f
        binding.searchBarContainer.animate().alpha(1f).setDuration(200).start()
        binding.etAppGridSearch.setText("")
        refreshAppGrid()
    }

    private fun closeAppGrid() {
        if (!appGridOpen) return
        appGridOpen = false
        binding.appGridBg.animate().alpha(0f).setDuration(180).start()
        binding.rvAppGrid.animate().alpha(0f).translationY(20f).setDuration(180).start()
        binding.dockContainer.animate().alpha(0f).translationY(30f).setDuration(160).start()
        binding.workspaceThumbnails.animate().alpha(0f).translationY(-20f).setDuration(160)
            .withEndAction {
                binding.appGridOverlay.visibility = View.GONE
                binding.rvAppGrid.translationY = 0f
                binding.dockContainer.translationY = 0f
                binding.workspaceThumbnails.translationY = 0f
            }.start()
        binding.searchBarContainer.animate().alpha(0f).setDuration(150)
            .withEndAction {
                binding.searchBarContainer.visibility = View.GONE
                binding.tvTopbarClock.visibility = View.VISIBLE
                binding.tvCurrentApp.visibility = View.VISIBLE
            }.start()
        binding.etAppGridSearch.setText("")
    }

    private fun refreshAppGrid() {
        appGridAdapter.submitCombined(vm.installedApps.value, builtinApps.filter { it.isInstalled })
    }

    private fun filterAppGrid(q: String) {
        val a = if (q.isBlank()) vm.installedApps.value else vm.installedApps.value.filter { it.label.contains(q, ignoreCase = true) }
        val b = builtinApps.filter { it.isInstalled && (q.isBlank() || it.name.contains(q, ignoreCase = true)) }
        appGridAdapter.submitCombined(a, b)
    }

    private fun openFileBrowser() {
        winMgr.openCustomWindow("📁  Fichiers", FileBrowserWindow(requireContext()), x=60f, y=30f, w=520, h=320)
    }

    private fun loadBuiltinApps() {
        viewLifecycleOwner.lifecycleScope.launch {
            builtinApps = BuiltinAppManager.loadCatalog(requireContext())
        }
    }

    private fun launchBuiltinApp(app: BuiltinApp) {
        if (!app.isInstalled) { startActivity(Intent(requireContext(), AppStoreActivity::class.java)); return }
        val path = BuiltinAppManager.getEntryPointPath(requireContext(), app)
        val wv = android.webkit.WebView(requireContext()).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            @Suppress("DEPRECATION") settings.allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION") settings.allowUniversalAccessFromFileURLs = true
            loadUrl("file://$path")
        }
        winMgr.openCustomWindow("${app.iconEmoji}  ${app.name}", wv,
            x=50f+winMgr.getCount()*18f, y=30f+winMgr.getCount()*14f, w=480, h=300)
    }

    private fun animatePopupIn(v: View) {
        v.visibility = View.VISIBLE; v.alpha=0f; v.scaleX=0.92f; v.scaleY=0.92f; v.translationY=-8f
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
    }
    private fun animatePopupOut(v: View) {
        v.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).translationY(-8f).setDuration(150)
            .withEndAction { v.visibility = View.GONE }.start()
    }
    private fun closeAllPopups() {
        listOf(binding.popupBazziteMenu, binding.popupSystray).forEach { if (it.visibility == View.VISIBLE) animatePopupOut(it) }
    }

    private fun pickWallpaper() {
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(Intent.ACTION_PICK).apply { type="image/*" }, 1001)
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode==1001 && resultCode==android.app.Activity.RESULT_OK) {
            val uri = data?.data ?: return
            requireContext().getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
                .edit().putString("wallpaper_uri", uri.toString()).apply()
            binding.ivWallpaper.setImageURI(uri)
        }
    }
    private fun loadWallpaper() {
        val prefs = requireContext().getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        val uriStr = prefs.getString("wallpaper_uri", null) ?: return
        try { binding.ivWallpaper.setImageURI(Uri.parse(uriStr)) } catch (e: Exception) {}
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            vm.installedApps.collectLatest { if (appGridOpen) refreshAppGrid() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.pinnedApps.collectLatest { dockAdapter.submitList(it) }
        }
    }

    override fun onResume() { super.onResume(); clockHandler.post(clockRunnable); loadBuiltinApps() }
    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockRunnable)
        if (appGridOpen) closeAppGrid()
        closeAllPopups()
    }
}
