package com.gnomebazzite.launcher.ui.store

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gnomebazzite.launcher.WebAppActivity
import com.gnomebazzite.launcher.data.BuiltinApp
import com.gnomebazzite.launcher.manager.BuiltinAppManager
import kotlinx.coroutines.launch
import android.content.Intent

class AppStoreActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var adapter: StoreAdapter
    private var apps: MutableList<BuiltinApp> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = buildUI()
        setContentView(root)
        lifecycleScope.launch {
            apps = BuiltinAppManager.loadCatalog(this@AppStoreActivity).toMutableList()
            adapter.submitList(apps.toList())
        }
    }

    private fun buildUI(): View {
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FF050608"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Toolbar
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#E614141F"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (48 * dp).toInt()
            )
            setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
        }
        val backBtn = TextView(this).apply {
            text = "←"; textSize = 18f
            setTextColor(Color.parseColor("#FF7C6FF7"))
            setPadding(0, 0, (16 * dp).toInt(), 0)
            setOnClickListener { finish() }
        }
        val title = TextView(this).apply {
            text = "📦  Bazaar — App Store intégré"
            textSize = 14f
            setTextColor(Color.parseColor("#FFE8E8FF"))
        }
        toolbar.addView(backBtn); toolbar.addView(title)
        root.addView(toolbar)

        val desc = TextView(this).apply {
            text = "Applications intégrées dans le launcher — installer = décompresser."
            textSize = 11f
            setTextColor(Color.parseColor("#88C8C0FF"))
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
        }
        root.addView(desc)

        rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(context)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        adapter = StoreAdapter(
            onInstall = { app -> installApp(app) },
            onOpen = { app -> openApp(app) },
            onUninstall = { app -> uninstallApp(app) }
        )
        rv.adapter = adapter
        root.addView(rv)
        return root
    }

    private fun installApp(app: BuiltinApp) {
        lifecycleScope.launch {
            BuiltinAppManager.installApp(this@AppStoreActivity, app) { progress ->
                adapter.updateProgress(app.id, progress.percent, progress.currentFile)
            }
            apps = BuiltinAppManager.loadCatalog(this@AppStoreActivity).toMutableList()
            adapter.submitList(apps.toList())
            adapter.clearProgress(app.id)
        }
    }

    private fun openApp(app: BuiltinApp) {
        val path = BuiltinAppManager.getEntryPointPath(this, app)
        startActivity(Intent(this, WebAppActivity::class.java).apply {
            putExtra("app", app); putExtra("path", path)
        })
    }

    private fun uninstallApp(app: BuiltinApp) {
        lifecycleScope.launch {
            BuiltinAppManager.uninstallApp(this@AppStoreActivity, app.id)
            apps = BuiltinAppManager.loadCatalog(this@AppStoreActivity).toMutableList()
            adapter.submitList(apps.toList())
        }
    }
}

class StoreAdapter(
    private val onInstall: (BuiltinApp) -> Unit,
    private val onOpen: (BuiltinApp) -> Unit,
    private val onUninstall: (BuiltinApp) -> Unit
) : RecyclerView.Adapter<StoreAdapter.VH>() {

    private var items: List<BuiltinApp> = emptyList()
    private val progressMap = mutableMapOf<String, Pair<Int, String>>()

    fun submitList(list: List<BuiltinApp>) { items = list; notifyDataSetChanged() }
    fun updateProgress(id: String, percent: Int, file: String) {
        progressMap[id] = Pair(percent, file)
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) notifyItemChanged(idx)
    }
    fun clearProgress(id: String) {
        progressMap.remove(id)
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) notifyItemChanged(idx)
    }

    inner class VH(val view: View) : RecyclerView.ViewHolder(view)
    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val dp = parent.context.resources.displayMetrics.density
        val ctx = parent.context

        val card = androidx.cardview.widget.CardView(ctx).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins((12*dp).toInt(), (6*dp).toInt(), (12*dp).toInt(), (6*dp).toInt()) }
            setCardBackgroundColor(Color.parseColor("#19FFFFFF"))
            radius = 14 * dp; cardElevation = 0f
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding((14*dp).toInt(), (12*dp).toInt(), (14*dp).toInt(), (12*dp).toInt())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val icon = TextView(ctx).apply {
            tag = "icon"; textSize = 28f; gravity = android.view.Gravity.CENTER
            // FIX: utiliser setMargins au lieu de setMarginRelative
            layoutParams = LinearLayout.LayoutParams((48*dp).toInt(), (48*dp).toInt()).apply {
                setMargins(0, 0, (12*dp).toInt(), 0)
            }
        }

        val info = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val nameView = TextView(ctx).apply {
            tag="name"; textSize=13f; setTextColor(Color.parseColor("#FFE8E8FF"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val descView = TextView(ctx).apply {
            tag="desc"; textSize=10f; setTextColor(Color.parseColor("#88C8C0FF"))
            maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END
        }
        val metaView = TextView(ctx).apply {
            tag="meta"; textSize=9f; setTextColor(Color.parseColor("#55C8C0FF"))
        }
        val progressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            tag="progress"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (6*dp).toInt()
            ).apply { topMargin = (4*dp).toInt() }
            max=100
            progressDrawable.setTint(Color.parseColor("#FF7C6FF7"))
            visibility = View.GONE
        }
        val progressLabel = TextView(ctx).apply {
            tag="progressLabel"; textSize=9f
            setTextColor(Color.parseColor("#667C6FF7")); visibility=View.GONE
        }
        info.addView(nameView); info.addView(descView); info.addView(metaView)
        info.addView(progressBar); info.addView(progressLabel)

        val btn = TextView(ctx).apply {
            tag="btn"; textSize=11f; gravity=android.view.Gravity.CENTER
            setPadding((14*dp).toInt(), (7*dp).toInt(), (14*dp).toInt(), (7*dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (10*dp).toInt() }
        }

        row.addView(icon); row.addView(info); row.addView(btn)
        card.addView(row)
        return VH(card)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]
        val dp = holder.view.context.resources.displayMetrics.density
        val icon = holder.view.findViewWithTag<TextView>("icon")
        val name = holder.view.findViewWithTag<TextView>("name")
        val desc = holder.view.findViewWithTag<TextView>("desc")
        val meta = holder.view.findViewWithTag<TextView>("meta")
        val progress = holder.view.findViewWithTag<ProgressBar>("progress")
        val progressLabel = holder.view.findViewWithTag<TextView>("progressLabel")
        val btn = holder.view.findViewWithTag<TextView>("btn")

        icon.text = app.iconEmoji
        name.text = app.name
        desc.text = app.description
        meta.text = "${app.version} · ${app.sizeKb}Ko · ${app.category}"

        val prog = progressMap[app.id]
        if (prog != null) {
            progress.visibility = View.VISIBLE; progressLabel.visibility = View.VISIBLE
            progress.progress = prog.first
            progressLabel.text = "Installation… ${prog.first}% — ${prog.second}"
            btn.isEnabled = false; btn.text = "…"; btn.background = null
        } else if (app.isInstalled) {
            progress.visibility = View.GONE; progressLabel.visibility = View.GONE
            btn.text = "Ouvrir"; btn.setTextColor(Color.parseColor("#FF28C840"))
            btn.background = null; btn.isEnabled = true
            btn.setOnClickListener { onOpen(app) }
            btn.setOnLongClickListener {
                android.app.AlertDialog.Builder(holder.view.context)
                    .setTitle("Désinstaller ${app.name} ?")
                    .setPositiveButton("Désinstaller") { _, _ -> onUninstall(app) }
                    .setNegativeButton("Annuler", null).show(); true
            }
        } else {
            progress.visibility = View.GONE; progressLabel.visibility = View.GONE
            btn.text = "Installer\n${app.sizeKb}Ko"
            btn.setTextColor(Color.parseColor("#FFE8E8FF"))
            val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#FF7C6FF7")); cornerRadius = 20 * dp
            }
            btn.background = bgDrawable; btn.isEnabled = true
            btn.setOnClickListener { onInstall(app) }
        }
    }
}
