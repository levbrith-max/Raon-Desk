package com.gnomebazzite.launcher.ui.landscape

import android.animation.*
import android.content.Context
import android.graphics.Color
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gnomebazzite.launcher.R
import com.gnomebazzite.launcher.data.AppInfo
import com.gnomebazzite.launcher.ui.common.AppGridAdapter

/**
 * Gère l'effet "Overview" style Android Recents / GNOME Activities :
 *
 * Quand on appuie sur le bouton ⠿ dans la taskbar :
 *  1. Le bureau (desktopContainer) se dézoom (scale 0.78) + monte légèrement
 *  2. Un overlay sombre apparaît
 *  3. Une vignette représentant le bureau actuel s'affiche à gauche
 *  4. La grille d'applications apparaît au centre/droite
 *  5. Cliquer une app → rectangle "preview" s'ouvre à droite, zoom-in vers l'app
 *  6. Cliquer en dehors → referme l'overview (re-zoom)
 */
class OverviewController(
    private val context: Context,
    private val rootContainer: FrameLayout,    // conteneur racine du fragment
    private val desktopContainer: ViewGroup,   // zone bureau avec fenêtres
    private val taskbarView: View,             // la taskbar (reste visible)
    private val onLaunchApp: (AppInfo) -> Unit
) {

    private var isOpen = false
    private var overlayView: View? = null

    // Durées d'animation
    private val ANIM_DURATION = 280L
    private val STAGGER_DELAY = 22L

    // ──────────────────────────────────────────
    // OUVRIR l'overview
    // ──────────────────────────────────────────

    fun open(apps: List<AppInfo>) {
        if (isOpen) return
        isOpen = true

        // 1. Créer et ajouter l'overlay
        val overlay = buildOverlay(apps)
        overlayView = overlay

        // Insérer SOUS la taskbar (la taskbar reste au premier plan)
        val taskbarIndex = rootContainer.indexOfChild(taskbarView)
        rootContainer.addView(overlay, if (taskbarIndex >= 0) taskbarIndex else rootContainer.childCount)

        // 2. Animer le bureau → dézoom
        desktopContainer.animate()
            .scaleX(0.78f).scaleY(0.78f)
            .translationY(-40f)
            .setDuration(ANIM_DURATION)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .start()

        // 3. Fade in overlay
        overlay.alpha = 0f
        overlay.animate().alpha(1f)
            .setDuration(ANIM_DURATION)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // 4. Animer les éléments internes en cascade
        val content = overlay.findViewWithTag<ViewGroup>("overview_content")
        content?.let { animateChildrenIn(it) }
    }

    // ──────────────────────────────────────────
    // FERMER l'overview
    // ──────────────────────────────────────────

    fun close(onEnd: (() -> Unit)? = null) {
        if (!isOpen) return
        isOpen = false

        // Re-zoom le bureau
        desktopContainer.animate()
            .scaleX(1f).scaleY(1f)
            .translationY(0f)
            .setDuration(ANIM_DURATION)
            .setInterpolator(DecelerateInterpolator(1.4f))
            .start()

        // Fade out overlay
        overlayView?.animate()
            ?.alpha(0f)
            ?.setDuration(ANIM_DURATION - 40)
            ?.setInterpolator(AccelerateInterpolator())
            ?.withEndAction {
                rootContainer.removeView(overlayView)
                overlayView = null
                onEnd?.invoke()
            }?.start()
    }

    fun isOverviewOpen() = isOpen

    // ──────────────────────────────────────────
    // CONSTRUCTION DE L'OVERLAY
    // ──────────────────────────────────────────

    private fun buildOverlay(apps: List<AppInfo>): View {
        val overlay = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            // Fond semi-transparent qui laisse voir le bureau dézoomé
            setBackgroundColor(Color.parseColor("#CC0D0E1A"))
            elevation = 20f
        }

        // ── Conteneur principal (tout sauf taskbar) ──
        val content = LinearLayout(context).apply {
            tag = "overview_content"
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                // Laisser de la place pour la taskbar en bas
                bottomMargin = context.resources.getDimensionPixelSize(R.dimen.taskbar_height)
            }
            setPadding(24, 32, 24, 16)
        }

        // ── Vignette du bureau actuel ──
        val desktopThumb = buildDesktopThumbnail()

        // ── Séparateur ──
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                setMargins(20, 48, 20, 48)
            }
            setBackgroundColor(Color.parseColor("#33FFFFFF"))
        }

        // ── Panneau droite : grille apps + preview ──
        val rightPanel = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }

        val appGrid = buildAppGrid(apps, rightPanel)
        rightPanel.addView(appGrid)

        content.addView(desktopThumb)
        content.addView(divider)
        content.addView(rightPanel)

        overlay.addView(content)

        // Fermer en cliquant sur le fond
        overlay.setOnClickListener { close() }
        content.setOnClickListener { /* absorber */ }

        return overlay
    }

    // ── Vignette bureau ──
    private fun buildDesktopThumbnail(): View {
        val dp = context.resources.displayMetrics.density
        val thumbW = (220 * dp).toInt()

        val container = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(thumbW, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                setMargins(0, 24, 0, 24)
            }
        }

        // Fond du thumb
        val thumbBg = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            background = context.getDrawable(R.drawable.window_background_thumb)
            elevation = 4f
        }

        // Label
        val label = TextView(context).apply {
            text = "Bureau actuel"
            setTextColor(Color.parseColor("#CCFFFFFF"))
            textSize = 11f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = (8 * density(context)).toInt() }
        }

        // Indicateur "actif"
        val activeIndicator = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, (3 * density(context)).toInt(), Gravity.BOTTOM
            )
            setBackgroundColor(Color.parseColor("#7C6FF7"))
        }

        container.addView(thumbBg)
        container.addView(label)
        container.addView(activeIndicator)
        return container
    }

    // ── Grille d'apps ──
    private fun buildAppGrid(apps: List<AppInfo>, parentFrame: FrameLayout): View {
        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Barre de recherche
        val searchBar = buildSearchBar(apps, parentFrame)
        mainLayout.addView(searchBar)

        // RecyclerView des apps
        val rv = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply { topMargin = (10 * density(context)).toInt() }
            layoutManager = GridLayoutManager(context, 6)
            clipToPadding = false
            setPadding(0, 8, 0, 8)
        }

        val adapter = AppGridAdapter(
            onAppClick = { app -> showAppPreview(app, parentFrame) },
            onAppLongClick = {}
        )
        adapter.submitList(apps)
        rv.adapter = adapter
        mainLayout.addView(rv)

        return mainLayout
    }

    private fun buildSearchBar(apps: List<AppInfo>, parentFrame: FrameLayout): View {
        val dp = density(context)
        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (36 * dp).toInt()
            )
            background = context.getDrawable(R.drawable.search_bar_bg)

            val icon = TextView(context).apply {
                text = "🔍"
                textSize = 14f
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    (36 * dp).toInt(), FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            val hint = TextView(context).apply {
                text = "Rechercher des applications…"
                setTextColor(Color.parseColor("#66C8B6FF"))
                textSize = 12f
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply { leftMargin = (40 * dp).toInt() }
            }
            addView(icon); addView(hint)
        }
    }

    // ──────────────────────────────────────────
    // PREVIEW D'UNE APP (rectangle à droite → zoom)
    // ──────────────────────────────────────────

    private fun showAppPreview(app: AppInfo, parentFrame: FrameLayout) {
        // Supprimer un éventuel preview précédent
        parentFrame.findViewWithTag<View>("app_preview")?.let { old ->
            parentFrame.removeView(old)
        }

        val dp = density(context)

        // Rectangle preview
        val preview = FrameLayout(context).apply {
            tag = "app_preview"
            layoutParams = FrameLayout.LayoutParams(
                (200 * dp).toInt(), FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.END
            ).apply { setMargins(8, 24, 0, 24) }
            background = context.getDrawable(R.drawable.window_background_thumb)
            elevation = 8f
            alpha = 0f
            scaleX = 0.88f
            scaleY = 0.88f
        }

        // Icône + nom de l'app dans le preview
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        val iconView = ImageView(context).apply {
            setImageDrawable(app.icon)
            layoutParams = LinearLayout.LayoutParams((56 * dp).toInt(), (56 * dp).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = (12 * dp).toInt()
            }
        }
        val nameView = TextView(context).apply {
            text = app.label
            setTextColor(Color.WHITE); textSize = 12f; gravity = Gravity.CENTER
        }
        val launchBtn = TextView(context).apply {
            text = "Ouvrir →"
            setTextColor(Color.parseColor("#7C6FF7"))
            textSize = 11f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (16 * dp).toInt() }
        }

        inner.addView(iconView); inner.addView(nameView); inner.addView(launchBtn)
        preview.addView(inner)
        parentFrame.addView(preview)

        // Animation d'apparition : slide depuis droite + zoom in
        preview.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(240)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()

        // Clic sur le preview → zoom vers l'app (ferme overview + lance l'app)
        preview.setOnClickListener {
            // Zoom-in animation
            preview.animate()
                .scaleX(1.15f).scaleY(1.15f).alpha(0f)
                .setDuration(200)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    close {
                        onLaunchApp(app)
                    }
                }.start()
        }
    }

    // ──────────────────────────────────────────
    // Animation en cascade des enfants
    // ──────────────────────────────────────────

    private fun animateChildrenIn(parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            child.alpha = 0f
            child.translationY = 30f
            child.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay(i * STAGGER_DELAY)
                .setDuration(280)
                .setInterpolator(DecelerateInterpolator(1.4f))
                .start()
        }
    }

    private fun density(ctx: Context) = ctx.resources.displayMetrics.density
}
