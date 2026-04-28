package com.gnomebazzite.launcher.ui.files

import android.content.Context
import android.graphics.Color
import android.os.Environment
import android.view.*
import android.widget.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Explorateur de fichiers interactif style macOS/GNOME Nautilus.
 * Utilisé comme contenu d'une DraggableWindowView.
 * Navigue dans le stockage réel de l'appareil Android.
 */
class FileBrowserWindow(context: Context) : LinearLayout(context) {

    private val toolbar: LinearLayout
    private val tvPath: TextView
    private val btnBack: TextView
    private val btnForward: TextView
    private val btnHome: TextView
    private val listView: ListView
    private val sidebar: LinearLayout
    private val emptyView: TextView
    private val statusBar: TextView

    private val history = mutableListOf<File>()
    private var historyIndex = -1
    private var currentDir: File = Environment.getExternalStorageDirectory()

    private val dp = context.resources.displayMetrics.density

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#FF1C1E2E"))

        // ── TOOLBAR ──
        toolbar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#FF252840"))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (32 * dp).toInt())
            setPadding((6 * dp).toInt(), 0, (6 * dp).toInt(), 0)
        }

        btnBack = makeToolBtn(context, "←") { navigateBack() }
        btnForward = makeToolBtn(context, "→") { navigateForward() }
        btnHome = makeToolBtn(context, "🏠") { navigateTo(Environment.getExternalStorageDirectory()) }

        tvPath = TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
            }
            setTextColor(Color.parseColor("#88C8C0FF"))
            textSize = 10f
            ellipsize = android.text.TextUtils.TruncateAt.START
            isSingleLine = true
        }

        val btnUp = makeToolBtn(context, "⬆") {
            currentDir.parentFile?.let { navigateTo(it) }
        }
        val btnRefresh = makeToolBtn(context, "↻") { refresh() }

        toolbar.addView(btnBack); toolbar.addView(btnForward)
        toolbar.addView(tvPath); toolbar.addView(btnUp); toolbar.addView(btnRefresh)
        toolbar.addView(btnHome)
        addView(toolbar)

        // ── CONTENU : sidebar + liste ──
        val contentRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }

        // Sidebar
        sidebar = buildSidebar(context)
        contentRow.addView(sidebar)

        // Séparateur
        val sep = View(context).apply {
            layoutParams = LayoutParams(1, LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#22FFFFFF"))
        }
        contentRow.addView(sep)

        // Liste des fichiers
        val listContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        }
        listView = ListView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            divider = null; dividerHeight = 0
            setBackgroundColor(Color.TRANSPARENT)
        }
        emptyView = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER
            text = "Dossier vide"
            setTextColor(Color.parseColor("#44C8C0FF"))
            textSize = 12f
        }
        listContainer.addView(listView)
        listContainer.addView(emptyView)
        contentRow.addView(listContainer)
        addView(contentRow)

        // ── STATUS BAR ──
        statusBar = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (22 * dp).toInt())
            setBackgroundColor(Color.parseColor("#FF1a1c2a"))
            setTextColor(Color.parseColor("#55C8C0FF"))
            textSize = 9f
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
        }
        addView(statusBar)

        // Initialiser
        navigateTo(currentDir)
    }

    // ──────────────────────────────────────────
    // NAVIGATION
    // ──────────────────────────────────────────

    private fun navigateTo(dir: File) {
        if (!dir.exists() || !dir.isDirectory) return
        // Tronquer l'historique avant si on est au milieu
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        history.add(dir)
        historyIndex = history.size - 1
        currentDir = dir
        refresh()
    }

    private fun navigateBack() {
        if (historyIndex > 0) {
            historyIndex--
            currentDir = history[historyIndex]
            refresh()
        }
    }

    private fun navigateForward() {
        if (historyIndex < history.size - 1) {
            historyIndex++
            currentDir = history[historyIndex]
            refresh()
        }
    }

    private fun refresh() {
        tvPath.text = currentDir.absolutePath

        val entries = try {
            currentDir.listFiles()
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val adapter = FileListAdapter(context, entries)
        listView.adapter = adapter
        emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE

        listView.setOnItemClickListener { _, _, position, _ ->
            val file = entries[position]
            if (file.isDirectory) {
                navigateTo(file)
            } else {
                openFile(file)
            }
        }

        // Status bar
        val count = entries.size
        val hidden = entries.count { it.name.startsWith(".") }
        statusBar.text = "$count éléments${if (hidden > 0) " ($hidden cachés)" else ""} — ${currentDir.name}"

        // Boutons nav state
        btnBack.alpha = if (historyIndex > 0) 1f else 0.3f
        btnForward.alpha = if (historyIndex < history.size - 1) 1f else 0.3f
    }

    private fun openFile(file: File) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.provider", file
            )
            intent.setDataAndType(uri, getMimeType(file))
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Impossible d'ouvrir : ${file.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "jpg", "jpeg", "png", "gif", "webp" -> "image/*"
            "mp4", "mkv", "avi", "mov" -> "video/*"
            "mp3", "flac", "ogg", "wav" -> "audio/*"
            "pdf" -> "application/pdf"
            "apk" -> "application/vnd.android.package-archive"
            "txt", "md", "log" -> "text/plain"
            "zip" -> "application/zip"
            else -> "*/*"
        }
    }

    // ──────────────────────────────────────────
    // SIDEBAR
    // ──────────────────────────────────────────
    private fun buildSidebar(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams((110 * dp).toInt(), LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#FF1a1c2a"))
            setPadding(0, (6 * dp).toInt(), 0, 0)

            val shortcuts = listOf(
                "🏠" to "Accueil" to Environment.getExternalStorageDirectory(),
                "📥" to "Télécharg." to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "🖼️" to "Images" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "🎵" to "Musique" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "🎬" to "Vidéos" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "📄" to "Documents" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            )

            shortcuts.forEach { (iconLabel, dir) ->
                val (icon, label) = iconLabel
                addView(buildSidebarItem(context, icon, label, dir))
            }
        }
    }

    private fun buildSidebarItem(context: Context, icon: String, label: String, dir: File): View {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * dp).toInt(), (5 * dp).toInt(), (6 * dp).toInt(), (5 * dp).toInt())
            isClickable = true; isFocusable = true
            background = android.util.TypedValue().let { tv ->
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                context.getDrawable(tv.resourceId)
            }

            val iconTv = TextView(context).apply {
                text = icon; textSize = 13f
                layoutParams = LayoutParams((22 * dp).toInt(), LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, (5 * dp).toInt(), 0)
                }
            }
            val labelTv = TextView(context).apply {
                text = label; textSize = 11f
                setTextColor(Color.parseColor("#AAC8C0FF"))
                isSingleLine = true
            }
            addView(iconTv); addView(labelTv)
            setOnClickListener { navigateTo(dir) }
        }
    }

    // ──────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────
    private fun makeToolBtn(context: Context, text: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text; textSize = 13f
            setTextColor(Color.parseColor("#AAFFFFFF"))
            gravity = Gravity.CENTER
            layoutParams = LayoutParams((28 * dp).toInt(), (28 * dp).toInt())
            isClickable = true; isFocusable = true
            setOnClickListener { onClick() }
        }
    }
}

// ──────────────────────────────────────────
// ADAPTER
// ──────────────────────────────────────────
private class FileListAdapter(context: Context, private val files: List<File>)
    : android.widget.ArrayAdapter<File>(context, 0, files) {

    private val dp = context.resources.displayMetrics.density
    private val dateFmt = SimpleDateFormat("dd/MM/yy HH:mm", Locale.FRENCH)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val file = files[position]
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * dp).toInt(), (5 * dp).toInt(), (8 * dp).toInt(), (5 * dp).toInt())
            minimumHeight = (36 * dp).toInt()
        }

        // Icône
        val icon = TextView(context).apply {
            text = getIcon(file); textSize = 16f
            layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, (8 * dp).toInt(), 0)
            }
        }

        // Nom
        val name = TextView(context).apply {
            text = file.name; textSize = 11f
            setTextColor(if (file.isDirectory) Color.parseColor("#FFE8E8FF") else Color.parseColor("#BBE8E8FF"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        }

        // Taille / date
        val meta = TextView(context).apply {
            text = if (file.isDirectory) dateFmt.format(Date(file.lastModified()))
                   else formatSize(file.length())
            textSize = 9f; setTextColor(Color.parseColor("#44C8C0FF"))
        }

        row.addView(icon); row.addView(name); row.addView(meta)

        // Hover effect
        row.setBackgroundColor(Color.TRANSPARENT)
        row.isClickable = true; row.isFocusable = true
        row.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.setBackgroundColor(Color.parseColor("#11FFFFFF"))
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.setBackgroundColor(Color.TRANSPARENT)
            }
            false
        }
        return row
    }

    private fun getIcon(file: File): String = when {
        file.isDirectory -> "📁"
        else -> when (file.extension.lowercase()) {
            "jpg","jpeg","png","gif","webp","bmp" -> "🖼️"
            "mp4","mkv","avi","mov","wmv" -> "🎬"
            "mp3","flac","ogg","wav","aac","m4a" -> "🎵"
            "pdf" -> "📕"
            "apk" -> "📦"
            "zip","rar","tar","gz","7z" -> "🗜️"
            "txt","md","log","cfg","ini" -> "📄"
            "kt","java","py","js","html","css","xml","json" -> "💻"
            else -> "📎"
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes o"
        bytes < 1024 * 1024 -> "${bytes / 1024} Ko"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} Mo"
        else -> "${bytes / (1024 * 1024 * 1024)} Go"
    }
}
