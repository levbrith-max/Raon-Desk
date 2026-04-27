package com.gnomebazzite.launcher.ui.landscape

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

class DesktopWindowManager(
    private val context: Context,
    private val desktopLayer: FrameLayout
) {
    private val windows = mutableListOf<DraggableWindowView>()
    private var nextZ = 10

    // ── Ouvrir une fenêtre avec un contenu View ──
    fun openCustomWindow(
        title: String,
        content: View,
        x: Float = 60f + windows.size * 22f,
        y: Float = 40f + windows.size * 18f,
        w: Int = 440,
        h: Int = 300
    ): DraggableWindowView {
        val win = createWindow(title, x, y, w, h)
        win.setContent(content)
        return win
    }

    // ── Ouvrir terminal de démo ──
    fun openTerminal(): DraggableWindowView {
        val tv = TextView(context).apply {
            text = "user@bazzite ~ $ neofetch\n  ██████  bazzite\n OS: Bazzite 40\n DE: GNOME 46\n WM: Mutter\n\nuser@bazzite ~ $ █"
            setTextColor(Color.parseColor("#7EE8A2"))
            setBackgroundColor(Color.parseColor("#0D0D1A"))
            textSize = 10f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(12, 12, 12, 12)
        }
        return openCustomWindow("Terminal — bash", tv, x = 40f, y = 30f, w = 400, h = 240)
    }

    fun openFiles(): DraggableWindowView {
        val placeholder = TextView(context).apply {
            text = "📁  Fichiers\n\nCliquer 📁 dans la taskbar\npour ouvrir le gestionnaire."
            setTextColor(Color.parseColor("#AAC8C0FF"))
            textSize = 12f
            gravity = android.view.Gravity.CENTER
        }
        return openCustomWindow("Fichiers", placeholder, x = 120f, y = 50f, w = 360, h = 240)
    }

    // ── Interne ──
    private fun createWindow(title: String, x: Float, y: Float, w: Int, h: Int): DraggableWindowView {
        val win = DraggableWindowView(context).apply {
            windowTitle = title
            layoutParams = FrameLayout.LayoutParams(w, h)
            this.x = x; this.y = y
            elevation = (nextZ++ * 2f)

            onFocus = { this.elevation = (nextZ++ * 2f) }
            onClose = { windows.remove(this) }
        }
        desktopLayer.addView(win)
        windows.add(win)
        win.animateOpen()
        return win
    }

    fun closeAll() {
        windows.toList().forEach { win ->
            win.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(150)
                .withEndAction { desktopLayer.removeView(win) }.start()
        }
        windows.clear()
    }

    fun getCount() = windows.size
    fun getWindows() = windows.toList()
}
