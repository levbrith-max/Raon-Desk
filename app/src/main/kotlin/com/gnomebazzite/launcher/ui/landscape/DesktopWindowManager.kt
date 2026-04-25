package com.gnomebazzite.launcher.ui.landscape

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.gnomebazzite.launcher.R

/**
 * Gère la couche de fenêtres flottantes sur le bureau.
 * Permet d'ouvrir, fermer, et empiler des DraggableWindowView.
 */
class DesktopWindowManager(
    private val context: Context,
    private val desktopLayer: FrameLayout   // FrameLayout dans lequel les fenêtres flottent
) {
    private val windows = mutableListOf<DraggableWindowView>()
    private var nextZOrder = 1

    // ─────────────────────────────────────────
    // Ouvrir une nouvelle fenêtre
    // ─────────────────────────────────────────

    fun openWindow(
        title: String,
        contentView: View? = null,
        x: Float = 80f + windows.size * 24f,
        y: Float = 60f + windows.size * 20f,
        width: Int = 420,
        height: Int = 300
    ): DraggableWindowView {
        val win = DraggableWindowView(context).apply {
            windowTitle = title
            layoutParams = FrameLayout.LayoutParams(width, height)
            this.x = x
            this.y = y
            elevation = (nextZOrder++ * 2f)

            onFocus = {
                this.elevation = (nextZOrder++ * 2f)
            }
            onClose = {
                windows.remove(this)
            }
        }

        contentView?.let { win.setContent(it) } ?: run {
            // Contenu de démo si rien fourni
            win.setContent(buildPlaceholderContent(title))
        }

        desktopLayer.addView(win)
        windows.add(win)

        // Animation d'ouverture
        win.alpha = 0f
        win.scaleX = 0.88f
        win.scaleY = 0.88f
        win.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(220)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.0f))
            .start()

        return win
    }

    // ─────────────────────────────────────────
    // Fermer toutes les fenêtres
    // ─────────────────────────────────────────

    fun closeAll() {
        windows.toList().forEach { win ->
            win.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f)
                .setDuration(150)
                .withEndAction { desktopLayer.removeView(win) }
                .start()
        }
        windows.clear()
    }

    // ─────────────────────────────────────────
    // Liste des fenêtres ouvertes
    // ─────────────────────────────────────────

    fun getWindows(): List<DraggableWindowView> = windows.toList()

    fun getCount() = windows.size

    // ─────────────────────────────────────────
    // Contenu placeholder
    // ─────────────────────────────────────────

    private fun buildPlaceholderContent(title: String): View {
        return TextView(context).apply {
            text = "📂  $title\n\nContenu à venir…"
            setTextColor(Color.parseColor("#AAFFFFFF"))
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            setPadding(16, 16, 16, 16)
        }
    }

    // ─────────────────────────────────────────
    // Terminal intégré (démo)
    // ─────────────────────────────────────────

    fun openTerminal(): DraggableWindowView {
        val tv = TextView(context).apply {
            text = "user@bazzite ~ $ neofetch\n  ██████  bazzite\n OS: Bazzite (Fedora 41)\n DE: GNOME 47\n WM: Mutter (Wayland)\n\nuser@bazzite ~ $ █"
            setTextColor(Color.parseColor("#7EE8A2"))
            setBackgroundColor(Color.parseColor("#0D0D1A"))
            textSize = 10f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(12, 12, 12, 12)
        }
        return openWindow("Terminal — bash", tv, x = 40f, y = 50f, width = 460, height = 280)
    }

    fun openFiles(): DraggableWindowView {
        return openWindow("Fichiers", null, x = 120f, y = 40f, width = 380, height = 260)
    }

    fun openSettings(): DraggableWindowView {
        return openWindow("Paramètres", null, x = 200f, y = 80f, width = 340, height = 280)
    }
}
