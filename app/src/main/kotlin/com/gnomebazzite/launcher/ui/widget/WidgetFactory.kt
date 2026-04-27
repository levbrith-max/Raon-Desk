package com.gnomebazzite.launcher.ui.portrait

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

object WidgetFactory {

    fun create(context: Context, type: Int): View? = when (type) {
        0 -> createClockWidget(context)
        1 -> createWeatherWidget(context)
        2 -> createCalendarWidget(context)
        3 -> createBatteryWidget(context)
        4 -> createNoteWidget(context)
        else -> null
    }

    // ── Horloge ──
    private fun createClockWidget(context: Context): View {
        val dp = context.resources.displayMetrics.density
        val card = buildCard(context, (160 * dp).toInt(), (80 * dp).toInt())

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val timeTv = TextView(context).apply {
            textSize = 28f
            setTextColor(Color.parseColor("#FFE8E8FF"))
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }
        val dateTv = TextView(context).apply {
            textSize = 10f
            setTextColor(Color.parseColor("#88C8C0FF"))
            gravity = Gravity.CENTER
        }

        // Fermeture du widget
        val closeBtn = buildCloseBtn(context, card)

        container.addView(timeTv); container.addView(dateTv)
        card.addView(container); card.addView(closeBtn)

        // Update en temps réel
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                val now = Date()
                timeTv.text = SimpleDateFormat("HH:mm", Locale.FRENCH).format(now)
                dateTv.text = SimpleDateFormat("EEE d MMM", Locale.FRENCH).format(now)
                handler.postDelayed(this, 10_000)
            }
        }
        card.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { handler.post(runnable) }
            override fun onViewDetachedFromWindow(v: View) { handler.removeCallbacks(runnable) }
        })
        return card
    }

    // ── Météo (démo) ──
    private fun createWeatherWidget(context: Context): View {
        val dp = context.resources.displayMetrics.density
        val card = buildCard(context, (180 * dp).toInt(), (100 * dp).toInt())

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = TextView(context).apply { text = "⛅"; textSize = 32f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0,0,(10*dp).toInt(),0) }
        }
        val temp = TextView(context).apply { text = "18°C"; textSize = 26f; setTextColor(Color.parseColor("#FFE8E8FF")) }
        row.addView(icon); row.addView(temp)

        val city = TextView(context).apply { text = "Lyon · Partiellement nuageux"; textSize = 10f; setTextColor(Color.parseColor("#88C8C0FF")); gravity = Gravity.CENTER }
        val detail = TextView(context).apply { text = "Vent 12km/h · Humidité 65%"; textSize = 9f; setTextColor(Color.parseColor("#55C8C0FF")); gravity = Gravity.CENTER }

        container.addView(row); container.addView(city); container.addView(detail)
        card.addView(container)
        card.addView(buildCloseBtn(context, card))
        return card
    }

    // ── Calendrier ──
    private fun createCalendarWidget(context: Context): View {
        val dp = context.resources.displayMetrics.density
        val card = buildCard(context, (160 * dp).toInt(), (100 * dp).toInt())

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val now = Date()
        val day = SimpleDateFormat("d", Locale.FRENCH).format(now)
        val month = SimpleDateFormat("MMMM", Locale.FRENCH).format(now).uppercase()
        val weekday = SimpleDateFormat("EEEE", Locale.FRENCH).format(now)

        val monthTv = TextView(context).apply { text = month; textSize = 10f; setTextColor(Color.parseColor("#7C6FF7")); gravity = Gravity.CENTER; letterSpacing = 0.1f }
        val dayTv = TextView(context).apply { text = day; textSize = 36f; setTextColor(Color.parseColor("#FFE8E8FF")); gravity = Gravity.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        val weekTv = TextView(context).apply { text = weekday; textSize = 10f; setTextColor(Color.parseColor("#88C8C0FF")); gravity = Gravity.CENTER }

        container.addView(monthTv); container.addView(dayTv); container.addView(weekTv)
        card.addView(container)
        card.addView(buildCloseBtn(context, card))
        return card
    }

    // ── Batterie ──
    private fun createBatteryWidget(context: Context): View {
        val dp = context.resources.displayMetrics.density
        val card = buildCard(context, (140 * dp).toInt(), (70 * dp).toInt())

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setPadding((10*dp).toInt(), (6*dp).toInt(), (10*dp).toInt(), (6*dp).toInt())
        }

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        val level = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 80
        val charging = bm?.isCharging ?: false

        val pct = TextView(context).apply {
            text = "${if (charging) "⚡" else "🔋"} $level%"
            textSize = 20f
            setTextColor(Color.parseColor(if (level > 20) "#FFE8E8FF" else "#FFFF5F57"))
            gravity = Gravity.CENTER
        }
        val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = level
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (6*dp).toInt()).apply { topMargin = (4*dp).toInt() }
            progressDrawable.setTint(Color.parseColor(if (level > 20) "#FF28C840" else "#FFFF5F57"))
        }
        val status = TextView(context).apply {
            text = if (charging) "En charge" else "Sur batterie"
            textSize = 9f; setTextColor(Color.parseColor("#55C8C0FF")); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (3*dp).toInt() }
        }

        container.addView(pct); container.addView(bar); container.addView(status)
        card.addView(container)
        card.addView(buildCloseBtn(context, card))
        return card
    }

    // ── Note rapide ──
    private fun createNoteWidget(context: Context): View {
        val dp = context.resources.displayMetrics.density
        val card = buildCard(context, (180 * dp).toInt(), (120 * dp).toInt())

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#FF2A2C40"))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (26*dp).toInt(), Gravity.TOP)
            setPadding((8*dp).toInt(), 0, (4*dp).toInt(), 0)
        }
        val headerTv = TextView(context).apply { text = "📝 Note"; textSize = 10f; setTextColor(Color.parseColor("#AAC8C0FF")); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        header.addView(headerTv)

        val et = EditText(context).apply {
            hint = "Saisir une note…"
            setHintTextColor(Color.parseColor("#44C8C0FF"))
            setTextColor(Color.parseColor("#FFE8E8FF"))
            background = null
            textSize = 11f
            gravity = Gravity.TOP
            setPadding((8*dp).toInt(), (30*dp).toInt(), (8*dp).toInt(), (8*dp).toInt())
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            isSingleLine = false
        }

        // Empêcher le drag quand on édite le texte
        et.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }

        card.addView(et)
        card.addView(header)
        card.addView(buildCloseBtn(context, card))
        return card
    }

    // ── Helpers ──
    private fun buildCard(context: Context, w: Int, h: Int): FrameLayout {
        val dp = context.resources.displayMetrics.density
        return FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(w, h)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#E6252840"))
                cornerRadius = (12 * dp)
                setStroke((1 * dp).toInt(), Color.parseColor("#22FFFFFF"))
            }
            elevation = 4 * dp
            clipToOutline = true
        }
    }

    private fun buildCloseBtn(context: Context, parent: ViewGroup): View {
        val dp = context.resources.displayMetrics.density
        return TextView(context).apply {
            text = "✕"
            textSize = 9f
            setTextColor(Color.parseColor("#66FF5F57"))
            gravity = Gravity.CENTER
            val size = (18 * dp).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END).apply {
                setMargins(0, (3*dp).toInt(), (3*dp).toInt(), 0)
            }
            setOnClickListener {
                parent.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(150)
                    .withEndAction { (parent.parent as? ViewGroup)?.removeView(parent) }.start()
            }
        }
    }
}
