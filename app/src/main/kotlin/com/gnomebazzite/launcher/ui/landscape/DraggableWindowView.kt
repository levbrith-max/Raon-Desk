package com.gnomebazzite.launcher.ui.landscape

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.doOnLayout
import com.gnomebazzite.launcher.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Fenêtre flottante déplaçable + redimensionnable.
 *
 * Structure :
 *  ┌──────────────────────────────────┐
 *  │  [•][•][•]  Titre        [□][×] │  ← TitleBar (drag)
 *  ├──────────────────────────────────┤
 *  │                                  │
 *  │         contentView              │  ← zone de contenu
 *  │                                  │
 *  └──────────────────────────────────┘
 *                                    ↗  resize handle (coin bas-droit)
 */
class DraggableWindowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    // ── Taille minimale ──
    private val minW = 320
    private val minH = 220

    // ── État drag ──
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragStartLeft = 0
    private var dragStartTop = 0

    // ── État resize ──
    private var resizeStartX = 0f
    private var resizeStartY = 0f
    private var resizeStartW = 0
    private var resizeStartH = 0
    private var isResizing = false
    private val resizeHitPx = 36   // zone sensible pour redimensionner

    // ── Vues internes ──
    private val titleBar: View
    private val titleText: TextView
    private val btnClose: View
    private val btnMax: View
    private val btnMin: View
    private val contentSlot: FrameLayout
    private val resizeCorner: View

    // ── Callbacks ──
    var onClose: (() -> Unit)? = null
    var onFocus: (() -> Unit)? = null
    var windowTitle: String = "Fenêtre"
        set(v) { field = v; titleText.text = v }

    // ── Maximisé ──
    private var isMaximized = false
    private var savedX = 0f
    private var savedY = 0f
    private var savedW = 0
    private var savedH = 0

    init {
        elevation = 12f
        clipToOutline = true
        val bg = context.getDrawable(R.drawable.window_background)
        background = bg

        // Inflate la structure interne
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        // ── TitleBar ──
        titleBar = LayoutInflater.from(context)
            .inflate(R.layout.view_window_titlebar, root, false)
        titleText = titleBar.findViewById(R.id.tvWindowTitle)
        btnClose = titleBar.findViewById(R.id.btnWindowClose)
        btnMax   = titleBar.findViewById(R.id.btnWindowMax)
        btnMin   = titleBar.findViewById(R.id.btnWindowMin)

        // ── Content ──
        contentSlot = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        // ── Resize handle (coin bas-droit) ──
        resizeCorner = View(context).apply {
            layoutParams = LayoutParams(resizeHitPx, resizeHitPx, Gravity.BOTTOM or Gravity.END)
            background = context.getDrawable(R.drawable.resize_handle)
            elevation = 14f
        }

        root.addView(titleBar)
        root.addView(contentSlot)
        addView(root)
        addView(resizeCorner)

        setupTitleBarDrag()
        setupResizeDrag()
        setupButtons()

        // Venir au premier plan au toucher
        setOnTouchListener { _, _ -> bringToFront(); onFocus?.invoke(); false }
    }

    // ─────────────────────────────────────
    // Contenu
    // ─────────────────────────────────────

    fun setContent(v: View) {
        contentSlot.removeAllViews()
        contentSlot.addView(v)
    }

    // ─────────────────────────────────────
    // Drag (titlebar)
    // ─────────────────────────────────────

    private fun setupTitleBarDrag() {
        titleBar.setOnTouchListener { _, event ->
            bringToFront(); onFocus?.invoke()
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isResizing) return@setOnTouchListener true
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    dragStartLeft = (layoutParams as? MarginLayoutParams)?.leftMargin ?: x.toInt()
                    dragStartTop  = (layoutParams as? MarginLayoutParams)?.topMargin  ?: y.toInt()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isResizing) return@setOnTouchListener true
                    val dx = event.rawX - dragStartX
                    val dy = event.rawY - dragStartY
                    val parent = parent as? ViewGroup ?: return@setOnTouchListener true
                    val newX = (dragStartLeft + dx).toInt()
                        .coerceIn(0, parent.width - width)
                    val newY = (dragStartTop + dy).toInt()
                        .coerceIn(0, parent.height - height)
                    x = newX.toFloat()
                    y = newY.toFloat()
                    true
                }
                else -> false
            }
        }
    }

    // ─────────────────────────────────────
    // Resize
    // ─────────────────────────────────────

    private fun setupResizeDrag() {
        resizeCorner.setOnTouchListener { _, event ->
            bringToFront(); onFocus?.invoke()
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isResizing = true
                    resizeStartX = event.rawX
                    resizeStartY = event.rawY
                    resizeStartW = width
                    resizeStartH = height
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - resizeStartX).toInt()
                    val dy = (event.rawY - resizeStartY).toInt()
                    val parent = parent as? ViewGroup ?: return@setOnTouchListener true
                    val newW = max(minW, min(resizeStartW + dx, parent.width - x.toInt()))
                    val newH = max(minH, min(resizeStartH + dy, parent.height - y.toInt()))
                    layoutParams = layoutParams.apply {
                        width = newW; height = newH
                    }
                    requestLayout()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isResizing = false; true
                }
                else -> false
            }
        }
    }

    // ─────────────────────────────────────
    // Boutons titlebar
    // ─────────────────────────────────────

    private fun setupButtons() {
        btnClose.setOnClickListener {
            animate().alpha(0f).scaleX(0.85f).scaleY(0.85f)
                .setDuration(180)
                .withEndAction { onClose?.invoke(); (parent as? ViewGroup)?.removeView(this) }
                .start()
        }
        btnMax.setOnClickListener { toggleMaximize() }
        btnMin.setOnClickListener {
            // Réduire : simple shrink animé (pas de taskbar impl)
            animate().scaleX(0.0f).scaleY(0.0f).alpha(0f)
                .setDuration(200)
                .withEndAction { visibility = View.GONE }
                .start()
        }
    }

    // ─────────────────────────────────────
    // Maximize / restore
    // ─────────────────────────────────────

    private fun toggleMaximize() {
        val parent = parent as? ViewGroup ?: return
        if (!isMaximized) {
            savedX = x; savedY = y
            savedW = width; savedH = height
            animateTo(0f, 0f, parent.width, parent.height)
            isMaximized = true
        } else {
            animateTo(savedX, savedY, savedW, savedH)
            isMaximized = false
        }
    }

    private fun animateTo(tx: Float, ty: Float, tw: Int, th: Int) {
        // Animer position
        animate().x(tx).y(ty).setDuration(250).start()
        // Animer taille via ValueAnimator
        val wAnim = ValueAnimator.ofInt(width, tw).apply {
            duration = 250
            addUpdateListener { layoutParams = layoutParams.also { p -> p.width = it.animatedValue as Int }; requestLayout() }
        }
        val hAnim = ValueAnimator.ofInt(height, th).apply {
            duration = 250
            addUpdateListener { layoutParams = layoutParams.also { p -> p.height = it.animatedValue as Int }; requestLayout() }
        }
        wAnim.start(); hAnim.start()
    }

    // ─────────────────────────────────────
    // Overview thumbnail (pour l'effet dézoom)
    // ─────────────────────────────────────

    /** Crée une version miniature de cette fenêtre pour l'affichage overview */
    fun createThumbnailView(context: Context): View {
        return FrameLayout(context).apply {
            background = this@DraggableWindowView.background
            val mini = TextView(context).apply {
                text = windowTitle
                setTextColor(Color.WHITE)
                textSize = 10f
                gravity = Gravity.CENTER
            }
            addView(mini, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            elevation = 6f
            clipToOutline = true
        }
    }
}
