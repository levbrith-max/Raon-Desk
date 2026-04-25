package com.gnomebazzite.launcher.ui.landscape

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gnomebazzite.launcher.BaseFragment
import com.gnomebazzite.launcher.R
import com.gnomebazzite.launcher.databinding.FragmentGnomeDesktopBinding
import com.gnomebazzite.launcher.manager.LauncherViewModel
import com.gnomebazzite.launcher.ui.common.AppGridAdapter
import com.gnomebazzite.launcher.ui.common.DashAdapter
import com.gnomebazzite.launcher.ui.store.AppStoreActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class GnomeDesktopFragment : BaseFragment<FragmentGnomeDesktopBinding>(
    FragmentGnomeDesktopBinding::inflate
) {
    private val vm: LauncherViewModel by activityViewModels()
    private lateinit var appGridAdapter: AppGridAdapter
    private lateinit var dashAdapter: DashAdapter
    private lateinit var winMgr: DesktopWindowManager
    private lateinit var overviewCtrl: OverviewController

    private val clockRunnable = object : Runnable {
        override fun run() { updateClock(); binding.tvTopbarClock.postDelayed(this, 30_000) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupWindowManager()
        setupOverviewController()
        setupTopBar()
        setupDash()
        setupAppDrawer()
        observeViewModel()
        updateClock()
    }

    private fun setupWindowManager() {
        winMgr = DesktopWindowManager(requireContext(), binding.desktopWindowLayer)
        winMgr.openTerminal()
        winMgr.openFiles()
        binding.layoutDesktop.setOnClickListener { closeAllPopups() }
    }

    private fun setupOverviewController() {
        overviewCtrl = OverviewController(
            context = requireContext(),
            rootContainer = binding.rootLandscape as android.widget.FrameLayout,
            desktopContainer = binding.desktopWindowLayer,
            taskbarView = binding.taskbarContainer,
            onLaunchApp = { app -> vm.launchApp(requireContext(), app) }
        )
    }

    private fun setupTopBar() {
        binding.btnActivities.setOnClickListener { toggleAppDrawer() }
        binding.btnBazziteMenu.setOnClickListener { toggleBazzitePopup() }
        binding.layoutSystray.setOnClickListener { toggleSystrayPopup() }
        binding.btnAppStore.setOnClickListener {
            startActivity(Intent(requireContext(), AppStoreActivity::class.java))
        }
        binding.btnOverview.setOnClickListener { toggleOverview() }
    }

    private fun toggleOverview() {
        if (overviewCtrl.isOverviewOpen()) {
            overviewCtrl.close()
            binding.btnOverview.setTextColor(resources.getColor(R.color.text_secondary, null))
        } else {
            if (vm.isDrawerOpen.value) closeDrawerWithAnimation()
            closeAllPopups()
            overviewCtrl.open(vm.installedApps.value)
            binding.btnOverview.setTextColor(resources.getColor(R.color.accent_violet, null))
        }
    }

    private fun updateClock() {
        val now = Date()
        binding.tvTopbarClock.text =
            "${SimpleDateFormat("EEE d MMM", Locale.FRENCH).format(now)}  " +
            SimpleDateFormat("HH:mm", Locale.FRENCH).format(now)
    }

    private fun toggleBazzitePopup() {
        val popup = binding.popupBazziteMenu
        if (popup.visibility == View.VISIBLE) { animatePopupOut(popup) }
        else { closeAllPopups(); animatePopupIn(popup); setupBazziteMenuItems() }
    }

    private fun setupBazziteMenuItems() {
        listOf(
            Triple(binding.popupRowGaming,  "🎮", "Retour Gaming Mode"),
            Triple(binding.popupRowSteam,   "💨", "Lancer Steam"),
            Triple(binding.popupRowSettings,"⚙️", "Paramètres système"),
            Triple(binding.popupRowStore,   "📦", "Bazaar App Store")
        ).forEachIndexed { idx, (row, icon, label) ->
            row.findViewById<TextView>(R.id.ivPopupRowIcon)?.text = icon
            row.findViewById<TextView>(R.id.tvPopupRowLabel)?.text = label
            row.setOnClickListener {
                closeAllPopups()
                if (idx == 3) startActivity(Intent(requireContext(), AppStoreActivity::class.java))
            }
        }
    }

    private fun toggleSystrayPopup() {
        val popup = binding.popupSystray
        if (popup.visibility == View.VISIBLE) animatePopupOut(popup)
        else { closeAllPopups(); animatePopupIn(popup) }
    }

    private fun animatePopupIn(v: View) {
        v.visibility = View.VISIBLE; v.alpha = 0f; v.scaleX = 0.92f; v.scaleY = 0.92f; v.translationY = -6f
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
            .setDuration(180).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun animatePopupOut(v: View) {
        v.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).translationY(-6f)
            .setDuration(140).setInterpolator(AccelerateInterpolator())
            .withEndAction { v.visibility = View.GONE }.start()
    }

    private fun closeAllPopups() {
        listOf(binding.popupBazziteMenu, binding.popupSystray).forEach {
            if (it.visibility == View.VISIBLE) animatePopupOut(it)
        }
    }

    private fun setupDash() {
        dashAdapter = DashAdapter { app -> vm.launchApp(requireContext(), app) }
        binding.rvDash.apply {
            layoutManager = GridLayoutManager(context, 1, GridLayoutManager.HORIZONTAL, false)
            adapter = dashAdapter
        }
    }

    private fun setupAppDrawer() {
        appGridAdapter = AppGridAdapter(
            onAppClick = { app -> vm.launchApp(requireContext(), app); closeDrawerWithAnimation() },
            onAppLongClick = {}
        )
        binding.rvAppGrid.apply {
            layoutManager = GridLayoutManager(context, 7)
            adapter = appGridAdapter
            itemAnimator = DrawerItemAnimator()
        }
        binding.etDrawerSearch.addTextChangedListener { text ->
            vm.setSearchQuery(text?.toString() ?: "")
            appGridAdapter.submitList(vm.filteredApps)
        }
        binding.overlayDrawerBackground.setOnClickListener { closeDrawerWithAnimation() }
    }

    private fun toggleAppDrawer() {
        if (vm.isDrawerOpen.value) closeDrawerWithAnimation() else openDrawerWithAnimation()
    }

    private fun openDrawerWithAnimation() {
        vm.setDrawerOpen(true)
        binding.containerDrawer.visibility = View.VISIBLE
        binding.overlayDrawerBackground.alpha = 0f
        binding.overlayDrawerBackground.animate().alpha(1f).setDuration(220)
            .setInterpolator(DecelerateInterpolator()).start()
        binding.cardDrawerContent.translationY = 100f; binding.cardDrawerContent.alpha = 0f
        binding.cardDrawerContent.animate().translationY(0f).alpha(1f).setDuration(300)
            .setInterpolator(DecelerateInterpolator(1.8f)).start()

        // Stagger des icônes
        binding.rvAppGrid.postDelayed({
            val lm = binding.rvAppGrid.layoutManager as? GridLayoutManager ?: return@postDelayed
            for (i in 0 until lm.childCount) {
                lm.getChildAt(i)?.let { child ->
                    child.alpha = 0f; child.scaleX = 0.65f; child.scaleY = 0.65f
                    child.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setStartDelay((i * 20L).coerceAtMost(300L))
                        .setDuration(260).setInterpolator(OvershootInterpolator(1.3f)).start()
                }
            }
        }, 80)
        binding.btnActivities.isSelected = true
    }

    private fun closeDrawerWithAnimation() {
        binding.overlayDrawerBackground.animate().alpha(0f).setDuration(180)
            .setInterpolator(AccelerateInterpolator()).start()
        binding.cardDrawerContent.animate().translationY(80f).alpha(0f).setDuration(210)
            .setInterpolator(AccelerateInterpolator(1.4f))
            .withEndAction {
                binding.containerDrawer.visibility = View.GONE
                binding.cardDrawerContent.translationY = 0f
                vm.setDrawerOpen(false)
                binding.etDrawerSearch.setText("")
                vm.setSearchQuery("")
            }.start()
        binding.btnActivities.isSelected = false
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            vm.installedApps.collectLatest { apps -> appGridAdapter.submitList(apps) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.pinnedApps.collectLatest { pinned -> dashAdapter.submitList(pinned) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.isDrawerOpen.collectLatest { open ->
                if (!open && binding.containerDrawer.visibility == View.VISIBLE) closeDrawerWithAnimation()
            }
        }
    }

    override fun onResume() { super.onResume(); binding.tvTopbarClock.post(clockRunnable) }
    override fun onPause() {
        super.onPause()
        binding.tvTopbarClock.removeCallbacks(clockRunnable)
        if (overviewCtrl.isOverviewOpen()) overviewCtrl.close()
    }
}

class DrawerItemAnimator : androidx.recyclerview.widget.DefaultItemAnimator() {
    override fun animateAdd(holder: RecyclerView.ViewHolder?): Boolean {
        holder?.itemView?.let { v ->
            v.alpha = 0f; v.scaleX = 0.6f; v.scaleY = 0.6f
            v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220)
                .setInterpolator(OvershootInterpolator(1.4f)).start()
        }
        return true
    }
}
