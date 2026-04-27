package com.gnomebazzite.launcher.ui.landscape

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.*
import android.widget.*
import com.gnomebazzite.launcher.R

class DraggableWindowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    companion object {
        private const val MIN_W = 280
        private const val MIN_H = 200
        private const val RESIZE_HIT = 48
    }

    private val titleBar: LinearLayout
    private val tvTitle: TextView
    private val btnClose: View
    private val btnMax: View
    private val btnMin: View
    private val contentSlot: FrameLayout
    private val resizeHandle: View

    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartViewX = 0f
    private var dragStartViewY = 0f
    private var isDragging = false

    private var resizeStartRawX = 0f
    private var resizeStartRawY = 0f
    private var resizeStartW = 0
    private var resizeStartH = 0
    private var isResizing = false

    private var isMaximized = false
    private var savedX = 0f; private var savedY = 0f
    private var savedW = 0;  private var savedH = 0

    var onClose: (() -> Unit)? = null
    var onFocus: (() -> Unit)? = null

    var windowTitle: String = "Fenêtre"
        set(v) { field = v; tvTitle.text = v }

    init {
        setWillNotDraw(false)
        elevation = 8f
        clipToOutline = true
        background = context.getDrawable(R.drawable.window_background)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        val dp = context.resources.displayMetrics.density

        titleBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (26 * dp).toInt()
            )
            setBackgroundColor(Color.parseColor("#FF252840"))
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
        }

        val btnSize = (11 * dp).toInt()

        btnClose = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                setMargins(0, 0, (5 * dp).toInt(), 0)
            }
            background = context.getDrawable(R.drawable.window_btn_close)
        }
        btnMin = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                setMargins(0, 0, (5 * dp).toInt(), 0)
            }
            background = context.getDrawable(R.drawable.window_btn_min)
        }
        btnMax = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
            background = context.getDrawable(R.drawable.window_btn_max)
        }

        tvTitle = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER
            text = "Fenêtre"
            setTextColor(Color.parseColor("#66C8C0FF"))
            textSize = 10f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        titleBar.addView(btnClose)
        titleBar.addView(btnMin)
        titleBar.addView(btnMax)
        titleBar.addView(tvTitle)

        contentSlot = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        root.addView(titleBar)
        root.addView(contentSlot)
        addView(root)

        resizeHandle = View(context).apply {
            layoutParams = LayoutParams(RESIZE_HIT, RESIZE_HIT, Gravity.BOTTOM or Gravity.END)
            background = context.getDrawable(R.drawable.resize_handle)
            elevation = 12f
        }
        addView(resizeHandle)

        setupListeners()
    }

    fun setContent(v: View) {
        contentSlot.removeAllViews()
        contentSlot.addView(v, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    // FIX BUG 1 : méthode animateOpen ajoutée
    fun animateOpen() {
        alpha = 0f
        scaleX = 0.85f
        scaleY = 0.85f
        animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(200)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.1f))
            .start()
    }

    private fun setupListeners() {
        btnClose.setOnClickListener {
            animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(160)
                .withEndAction {
                    onClose?.invoke()
                    (parent as? ViewGroup)?.removeView(this)
                }.start()
        }
        btnMax.setOnClickListener { toggleMaximize() }
        btnMin.setOnClickListener {
            animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(180)
                .withEndAction { visibility = View.GONE }.start()
        }

        titleBar.setOnTouchListener { _, event -> handleDragTouch(event) }
        resizeHandle.setOnTouchListener { _, event -> handleResizeTouch(event) }

        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                bringToFront()
                onFocus?.invoke()
            }
            false
        }
    }

    private fun handleDragTouch(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                bringToFront(); onFocus?.invoke()
                isDragging = true
                dragStartRawX = event.rawX; dragStartRawY = event.rawY
                dragStartViewX = x; dragStartViewY = y
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false
                val parent = parent as? ViewGroup ?: return false
                x = (dragStartViewX + event.rawX - dragStartRawX)
                    .coerceIn(0f, (parent.width - width).toFloat())
                y = (dragStartViewY + event.rawY - dragStartRawY)
                    .coerceIn(0f, (parent.height - height).toFloat())
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false; true
            }
            else -> false
        }
    }

    private fun handleResizeTouch(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                bringToFront(); onFocus?.invoke()
                isResizing = true
                resizeStartRawX = event.rawX; resizeStartRawY = event.rawY
                resizeStartW = width; resizeStartH = height
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isResizing) return false
                val parent = parent as? ViewGroup ?: return false
                val newW = (resizeStartW + (event.rawX - resizeStartRawX).toInt())
                    .coerceIn(MIN_W, parent.width - x.toInt())
                val newH = (resizeStartH + (event.rawY - resizeStartRawY).toInt())
                    .coerceIn(MIN_H, parent.height - y.toInt())
                layoutParams = layoutParams.apply { width = newW; height = newH }
                requestLayout()
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isResizing = false; true
            }
            else -> false
        }
    }

    private fun toggleMaximize() {
        val parent = parent as? ViewGroup ?: return
        if (!isMaximized) {
            savedX = x; savedY = y; savedW = width; savedH = height
            animateBounds(0f, 0f, parent.width, parent.height)
            isMaximized = true
        } else {
            animateBounds(savedX, savedY, savedW, savedH)
            isMaximized = false
        }
    }

    private fun animateBounds(tx: Float, ty: Float, tw: Int, th: Int) {
        animate().x(tx).y(ty).setDuration(220).start()
        ValueAnimator.ofInt(width, tw).apply {
            duration = 220
            addUpdateListener {
                layoutParams = layoutParams.also { p -> p.width = it.animatedValue as Int }
                requestLayout()
            }
        }.start()
        ValueAnimator.ofInt(height, th).apply {
            duration = 220
            addUpdateListener {
                layoutParams = layoutParams.also { p -> p.height = it.animatedValue as Int }
                requestLayout()
            }
        }.start()
    }
}
