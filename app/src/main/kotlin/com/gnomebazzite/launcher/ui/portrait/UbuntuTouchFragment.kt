package com.gnomebazzite.launcher.ui.portrait

import android.content.Context
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
    private lateinit var drawerAdapter: AppGridAdapter
    private val widgetViews = mutableListOf<View>()

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() { updateClock(); clockHandler.postDelayed(this, 30_000) }
    }

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

    // ══════════════════════════════════════════
    // TOP BAR
    // ══════════════════════════════════════════
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

    // ══════════════════════════════════════════
    // SIDE LAUNCHER
    // ══════════════════════════════════════════
    private fun setupSideLauncher() {
        binding.btnSideHome.isSelected = true
        binding.btnSideFolder.setOnClickListener {
            if (binding.containerPortraitDrawer.visibility == View.VISIBLE) closeDrawer()
            else openDrawer()
        }
        binding.btnSideStore.setOnClickListener {
            startActivity(Intent(requireContext(), AppStoreActivity::class.java))
        }
        binding.btnSideSettings.setOnClickListener {
            try { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
            catch (e: Exception) { }
        }
    }

    // ══════════════════════════════════════════
    // APP DRAWER OVERLAY
    // ══════════════════════════════════════════
    private fun setupDrawerOverlay() {
        drawerAdapter = AppGridAdapter(
            onAppClick = { app -> vm.launchApp(requireContext(), app); closeDrawer() },
            onAppLongClick = { app ->
                vm.pinApp(app)
                Toast.makeText(requireContext(), "${app.label} épinglé", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvPortraitDrawerGrid.apply {
            layoutManager = GridLayoutManager(context, 4)
            adapter = drawerAdapter
        }
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
                binding.etPortraitDrawerSearch.setText("")
            }.start()
        binding.btnSideFolder.isSelected = false
    }

    // ══════════════════════════════════════════
    // WIDGETS
    // ══════════════════════════════════════════
    private fun setupWidgetZone() {
        binding.widgetCanvas.setOnLongClickListener {
            showAddWidgetMenu(); true
        }
        addWidget(0) // horloge par défaut
    }

    private fun showAddWidgetMenu() {
        val items = arrayOf("🕐  Horloge", "📅  Calendrier", "🔋  Batterie", "📝  Note rapide")
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Ajouter un widget")
            .setItems(items) { _, which -> addWidget(which) }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun addWidget(type: Int) {
        val dp = resources.displayMetrics.density
        val widget = createWidget(requireContext(), type, dp) ?: return
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = (16 * dp).toInt() + (widgetViews.size * 12)
            topMargin  = (16 * dp).toInt() + (widgetViews.size * 12)
        }
        widget.layoutParams = lp
        widget.elevation = 4 * dp
        makeDraggable(widget)
        binding.widgetCanvas.addView(widget)
        widgetViews.add(widget)
        widget.alpha = 0f; widget.scaleX = 0.7f; widget.scaleY = 0.7f
        widget.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220)
            .setInterpolator(OvershootInterpolator(1.2f)).start()
    }

    private fun createWidget(ctx: Context, type: Int, dp: Float): View? {
        return when (type) {
            0 -> createClockWidget(ctx, dp)
            1 -> createCalendarWidget(ctx, dp)
            2 -> createBatteryWidget(ctx, dp)
            3 -> createNoteWidget(ctx, dp)
            else -> null
        }
    }

    private fun buildCard(ctx: Context, w: Int, h: Int, dp: Float): FrameLayout {
        return FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(w, h)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#E6252840"))
                cornerRadius = 12 * dp
                setStroke((1 * dp).toInt(), Color.parseColor("#22FFFFFF"))
            }
            clipToOutline = true
        }
    }

    private fun buildCloseBtn(ctx: Context, parent: ViewGroup, dp: Float): View {
        val size = (18 * dp).toInt()
        return TextView(ctx).apply {
            text = "✕"; textSize = 9f
            setTextColor(Color.parseColor("#66FF5F57"))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END).apply {
                setMargins(0, (3*dp).toInt(), (3*dp).toInt(), 0)
            }
            setOnClickListener {
                parent.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(150)
                    .withEndAction { (parent.parent as? ViewGroup)?.removeView(parent) }.start()
            }
        }
    }

    private fun createClockWidget(ctx: Context, dp: Float): View {
        val card = buildCard(ctx, (160*dp).toInt(), (80*dp).toInt(), dp)
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        val timeTv = TextView(ctx).apply {
            textSize = 28f; setTextColor(Color.parseColor("#FFE8E8FF"))
            typeface = android.graphics.Typeface.MONOSPACE; gravity = Gravity.CENTER
        }
        val dateTv = TextView(ctx).apply {
            textSize = 10f; setTextColor(Color.parseColor("#88C8C0FF")); gravity = Gravity.CENTER
        }
        val h = Handler(Looper.getMainLooper())
        val r = object : Runnable {
            override fun run() {
                timeTv.text = SimpleDateFormat("HH:mm", Locale.FRENCH).format(Date())
                dateTv.text = SimpleDateFormat("EEE d MMM", Locale.FRENCH).format(Date())
                h.postDelayed(this, 10_000)
            }
        }
        card.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { h.post(r) }
            override fun onViewDetachedFromWindow(v: View) { h.removeCallbacks(r) }
        })
        col.addView(timeTv); col.addView(dateTv)
        card.addView(col); card.addView(buildCloseBtn(ctx, card, dp))
        return card
    }

    private fun createCalendarWidget(ctx: Context, dp: Float): View {
        val card = buildCard(ctx, (160*dp).toInt(), (100*dp).toInt(), dp)
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        val now = Date()
        val monthTv = TextView(ctx).apply {
            text = SimpleDateFormat("MMMM", Locale.FRENCH).format(now).uppercase()
            textSize = 10f; setTextColor(Color.parseColor("#7C6FF7")); gravity = Gravity.CENTER
        }
        val dayTv = TextView(ctx).apply {
            text = SimpleDateFormat("d", Locale.FRENCH).format(now)
            textSize = 36f; setTextColor(Color.parseColor("#FFE8E8FF")); gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val weekTv = TextView(ctx).apply {
            text = SimpleDateFormat("EEEE", Locale.FRENCH).format(now)
            textSize = 10f; setTextColor(Color.parseColor("#88C8C0FF")); gravity = Gravity.CENTER
        }
        col.addView(monthTv); col.addView(dayTv); col.addView(weekTv)
        card.addView(col); card.addView(buildCloseBtn(ctx, card, dp))
        return card
    }

    private fun createBatteryWidget(ctx: Context, dp: Float): View {
        val card = buildCard(ctx, (140*dp).toInt(), (70*dp).toInt(), dp)
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setPadding((10*dp).toInt(), (6*dp).toInt(), (10*dp).toInt(), (6*dp).toInt())
        }
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        val level = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 80
        val charging = bm?.isCharging ?: false
        val pct = TextView(ctx).apply {
            text = "${if (charging) "⚡" else "🔋"} $level%"
            textSize = 20f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor(if (level > 20) "#FFE8E8FF" else "#FFFF5F57"))
        }
        val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = level
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (6*dp).toInt()).apply { topMargin=(4*dp).toInt() }
            progressDrawable.setTint(Color.parseColor(if (level > 20) "#FF28C840" else "#FFFF5F57"))
        }
        col.addView(pct); col.addView(bar)
        card.addView(col); card.addView(buildCloseBtn(ctx, card, dp))
        return card
    }

    private fun createNoteWidget(ctx: Context, dp: Float): View {
        val card = buildCard(ctx, (180*dp).toInt(), (120*dp).toInt(), dp)
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#FF2A2C40"))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (26*dp).toInt(), Gravity.TOP)
            setPadding((8*dp).toInt(), 0, (4*dp).toInt(), 0)
        }
        val headerTv = TextView(ctx).apply {
            text = "📝 Note"; textSize = 10f; setTextColor(Color.parseColor("#AAC8C0FF"))
        }
        header.addView(headerTv)
        val et = EditText(ctx).apply {
            hint = "Saisir une note…"; setHintTextColor(Color.parseColor("#44C8C0FF"))
            setTextColor(Color.parseColor("#FFE8E8FF")); background = null; textSize = 11f
            gravity = Gravity.TOP
            setPadding((8*dp).toInt(), (30*dp).toInt(), (8*dp).toInt(), (8*dp).toInt())
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            isSingleLine = false
            setOnTouchListener { v, _ -> v.parent.requestDisallowInterceptTouchEvent(true); false }
        }
        card.addView(et); card.addView(header); card.addView(buildCloseBtn(ctx, card, dp))
        return card
    }

    private fun makeDraggable(v: View) {
        var dX = 0f; var dY = 0f
        v.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX; dY = view.y - event.rawY
                    view.animate().scaleX(1.05f).scaleY(1.05f).setDuration(80).start()
                    view.bringToFront(); true
                }
                MotionEvent.ACTION_MOVE -> {
                    val p = view.parent as? ViewGroup ?: return@setOnTouchListener false
                    view.x = (event.rawX + dX).coerceIn(0f, (p.width - view.width).toFloat())
                    view.y = (event.rawY + dY).coerceIn(0f, (p.height - view.height).toFloat()); true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start(); true
                }
                else -> false
            }
        }
    }

    // ══════════════════════════════════════════
    // WALLPAPER
    // ══════════════════════════════════════════
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
        try { binding.ivPortraitWallpaper.setImageURI(Uri.parse(uriStr)) } catch (e: Exception) { }
    }

    // ══════════════════════════════════════════
    // OBSERVERS
    // ══════════════════════════════════════════
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            vm.installedApps.collectLatest { apps -> drawerAdapter.submitList(apps) }
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
                layoutParams = LinearLayout.LayoutParams(s, s).apply { setMargins(4, 3, 4, 3) }
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
