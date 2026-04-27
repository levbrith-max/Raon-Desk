package com.gnomebazzite.launcher.ui.portrait

import android.content.Intent
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
import com.gnomebazzite.launcher.data.AppInfo
import com.gnomebazzite.launcher.databinding.FragmentUbuntuTouchBinding
import com.gnomebazzite.launcher.manager.LauncherViewModel
import com.gnomebazzite.launcher.ui.common.AppGridAdapter
import com.gnomebazzite.launcher.ui.store.AppStoreActivity
import com.gnomebazzite.launcher.ui.widget.WidgetFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class UbuntuTouchFragment : BaseFragment<FragmentUbuntuTouchBinding>(
    FragmentUbuntuTouchBinding::inflate
) {
    private val vm: LauncherViewModel by activityViewModels()
    private lateinit var drawerAdapter: AppGridAdapter

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() { updateClock(); clockHandler.postDelayed(this, 30_000) }
    }

    private val widgetViews = mutableListOf<View>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTopbar()
        setupSideLauncher()
        setupDrawerOverlay()
        setupWidgetZone()
        loadWallpaper()
        observeViewModel()
        updateClock()
    }

    // ══════════════════════════════════════════════
    // TOP BAR
    // ══════════════════════════════════════════════
    private fun setupTopbar() {
        binding.btnPortraitWallpaper.setOnClickListener { pickWallpaper() }
        binding.btnPortraitStore.setOnClickListener {
            startActivity(Intent(requireContext(), AppStoreActivity::class.java))
        }
    }

    private fun updateClock() {
        binding.tvPortraitTime.text = SimpleDateFormat("HH:mm", Locale.FRENCH).format(Date())
        binding.tvPortraitDate.text = SimpleDateFormat("EEE d MMM", Locale.FRENCH).format(Date())
    }

    // ══════════════════════════════════════════════
    // SIDE LAUNCHER
    // ══════════════════════════════════════════════
    private fun setupSideLauncher() {
        binding.btnSideHome.isSelected = true

        binding.btnSideFolder.setOnClickListener {
            if (binding.containerPortraitDrawer.visibility == View.VISIBLE)
                closeDrawer() else openDrawer()
        }

        binding.btnSideStore.setOnClickListener {
            startActivity(Intent(requireContext(), AppStoreActivity::class.java))
        }

        binding.btnSideSettings.setOnClickListener {
            try { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
            catch (e: Exception) { }
        }
    }

    // ══════════════════════════════════════════════
    // APP DRAWER OVERLAY
    // ══════════════════════════════════════════════
    private fun setupDrawerOverlay() {
        drawerAdapter = AppGridAdapter(
            onAppClick = { app ->
                vm.launchApp(requireContext(), app)
                closeDrawer()
            },
            onAppLongClick = { app ->
                vm.pinApp(app)
                Toast.makeText(requireContext(), "${app.label} épinglé", Toast.LENGTH_SHORT).show()
            },
            onBuiltinAppClick = { app ->
                closeDrawer()
                Toast.makeText(requireContext(), "Ouvre ${app.name}…", Toast.LENGTH_SHORT).show()
            }
        )

        binding.rvPortraitDrawerGrid.apply {
            layoutManager = GridLayoutManager(context, 4)
            adapter = drawerAdapter
        }

        // FIX BUG 2 : utiliser l'interface TextWatcher directement sans extension function
        binding.etPortraitDrawerSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString() ?: ""
                val filtered = if (q.isBlank()) vm.installedApps.value
                else vm.installedApps.value.filter { it.label.contains(q, ignoreCase = true) }
                drawerAdapter.submitList(filtered)
            }
        })

        binding.portraitDrawerScrim.setOnClickListener { closeDrawer() }
    }

    private fun openDrawer() {
        binding.containerPortraitDrawer.visibility = View.VISIBLE
        binding.portraitDrawerScrim.alpha = 0f
        binding.portraitDrawerScrim.animate().alpha(1f).setDuration(220).start()

        binding.portraitDrawerPanel.translationY = 100f
        binding.portraitDrawerPanel.alpha = 0f
        binding.portraitDrawerPanel.animate()
            .translationY(0f).alpha(1f)
            .setDuration(300).setInterpolator(DecelerateInterpolator(1.8f)).start()

        binding.rvPortraitDrawerGrid.postDelayed({
            val lm = binding.rvPortraitDrawerGrid.layoutManager as? GridLayoutManager ?: return@postDelayed
            for (i in 0 until lm.childCount) {
                lm.getChildAt(i)?.let { child ->
                    child.alpha = 0f; child.scaleX = 0.65f; child.scaleY = 0.65f
                    child.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setStartDelay((i * 22L).coerceAtMost(300L))
                        .setDuration(240).setInterpolator(OvershootInterpolator(1.3f)).start()
                }
            }
        }, 80)

        binding.btnSideFolder.isSelected = true
        drawerAdapter.submitList(vm.installedApps.value)
    }

    private fun closeDrawer() {
        binding.portraitDrawerScrim.animate().alpha(0f).setDuration(180).start()
        binding.portraitDrawerPanel.animate()
            .translationY(80f).alpha(0f).setDuration(220)
            .setInterpolator(AccelerateInterpolator(1.3f))
            .withEndAction {
                binding.containerPortraitDrawer.visibility = View.GONE
                binding.portraitDrawerPanel.translationY = 0f
                // FIX BUG 3 : utiliser setText("") au lieu de .text?.clear()
                binding.etPortraitDrawerSearch.setText("")
            }.start()
        binding.btnSideFolder.isSelected = false
    }

    // ══════════════════════════════════════════════
    // WIDGETS
    // ══════════════════════════════════════════════
    private fun setupWidgetZone() {
        binding.widgetCanvas.setOnLongClickListener {
            showAddWidgetMenu()
            true
        }
        // Horloge par défaut au démarrage
        addWidget(0)
    }

    private fun showAddWidgetMenu() {
        val items = arrayOf(
            "🕐  Horloge digitale",
            "🌤  Météo (démo)",
            "📅  Calendrier",
            "🔋  Batterie",
            "📝  Note rapide"
        )
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Ajouter un widget")
            .setItems(items) { _, which -> addWidget(which) }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun addWidget(type: Int) {
        val widget = WidgetFactory.create(requireContext(), type) ?: return
        val dp = resources.displayMetrics.density
        val lp = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = (20 * dp).toInt() + (widgetViews.size * 12)
            topMargin  = (20 * dp).toInt() + (widgetViews.size * 12)
        }
        widget.layoutParams = lp
        widget.elevation = 4f
        makeDraggable(widget)
        binding.widgetCanvas.addView(widget)
        widgetViews.add(widget)

        widget.alpha = 0f; widget.scaleX = 0.7f; widget.scaleY = 0.7f
        widget.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220)
            .setInterpolator(OvershootInterpolator(1.2f)).start()
    }

    private fun makeDraggable(v: View) {
        var dX = 0f; var dY = 0f
        v.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    view.animate().scaleX(1.05f).scaleY(1.05f).setDuration(80).start()
                    view.bringToFront()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val parent = view.parent as? ViewGroup ?: return@setOnTouchListener false
                    view.x = (event.rawX + dX).coerceIn(0f, (parent.width - view.width).toFloat())
                    view.y = (event.rawY + dY).coerceIn(0f, (parent.height - view.height).toFloat())
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    true
                }
                else -> false
            }
        }
    }

    // ══════════════════════════════════════════════
    // WALLPAPER
    // ══════════════════════════════════════════════
    private fun pickWallpaper() {
        val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, 1002)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1002 && resultCode == android.app.Activity.RESULT_OK) {
            val uri = data?.data ?: return
            requireContext().getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
                .edit().putString("wallpaper_uri", uri.toString()).apply()
            binding.ivPortraitWallpaper.setImageURI(uri)
        }
    }

    private fun loadWallpaper() {
        val prefs = requireContext().getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        val uriStr = prefs.getString("wallpaper_uri", null) ?: return
        try { binding.ivPortraitWallpaper.setImageURI(Uri.parse(uriStr)) }
        catch (e: Exception) { }
    }

    // ══════════════════════════════════════════════
    // OBSERVERS
    // ══════════════════════════════════════════════
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            vm.installedApps.collectLatest { apps ->
                drawerAdapter.submitList(apps)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.pinnedApps.collectLatest { updateSidePinned(it) }
        }
    }

    private fun updateSidePinned(apps: List<AppInfo>) {
        binding.sidelauncherPinnedContainer.removeAllViews()
        val dp = resources.displayMetrics.density
        apps.take(5).forEach { app ->
            val icon = ImageView(requireContext()).apply {
                setImageDrawable(app.icon)
                val s = (36 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s).apply {
                    setMargins(4, 3, 4, 3)
                }
                background = requireContext().getDrawable(R.drawable.launcher_icon_bg)
                clipToOutline = true
                setOnClickListener { vm.launchApp(requireContext(), app) }
                setOnLongClickListener {
                    vm.unpinApp(app.packageName)
                    Toast.makeText(requireContext(), "${app.label} retiré", Toast.LENGTH_SHORT).show()
                    true
                }
            }
            binding.sidelauncherPinnedContainer.addView(icon)
        }
    }

    override fun onResume() { super.onResume(); clockHandler.post(clockRunnable) }
    override fun onPause() { super.onPause(); clockHandler.removeCallbacks(clockRunnable) }
}
