package com.gnomebazzite.launcher.ui.common

import android.graphics.Color
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gnomebazzite.launcher.R
import com.gnomebazzite.launcher.data.AppInfo
import com.gnomebazzite.launcher.data.BuiltinApp

// Item wrapper pour les deux types d'apps
sealed class AppGridItem {
    data class Android(val app: AppInfo) : AppGridItem()
    data class Builtin(val app: BuiltinApp) : AppGridItem()
}

class AppGridAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo) -> Unit,
    private val onBuiltinAppClick: ((BuiltinApp) -> Unit)? = null
) : RecyclerView.Adapter<AppGridAdapter.VH>() {

    private var items: List<AppGridItem> = emptyList()

    fun submitCombined(androidApps: List<AppInfo>, builtinApps: List<BuiltinApp>) {
        items = builtinApps.map { AppGridItem.Builtin(it) } +
                androidApps.map { AppGridItem.Android(it) }
        notifyDataSetChanged()
    }

    fun submitList(androidApps: List<AppInfo>) {
        val existing = items.filterIsInstance<AppGridItem.Builtin>()
        items = existing + androidApps.map { AppGridItem.Android(it) }
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivAppIcon)
        val name: TextView  = view.findViewById(R.id.tvAppName)
        val emojiIcon: TextView = view.findViewById(R.id.tvEmojiIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_grid, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        when (val item = items[position]) {
            is AppGridItem.Android -> {
                holder.icon.setImageDrawable(item.app.icon)
                holder.icon.visibility = View.VISIBLE
                holder.emojiIcon.visibility = View.GONE
                holder.name.text = item.app.label

                holder.itemView.setOnClickListener { onAppClick(item.app) }
                holder.itemView.setOnLongClickListener {
                    onAppLongClick(item.app)
                    true
                }
            }
            is AppGridItem.Builtin -> {
                holder.icon.visibility = View.GONE
                holder.emojiIcon.visibility = View.VISIBLE
                holder.emojiIcon.text = item.app.iconEmoji
                try {
                    holder.emojiIcon.setBackgroundColor(Color.parseColor(item.app.iconColor))
                } catch (e: Exception) { /* ignore */ }
                holder.name.text = item.app.name

                holder.itemView.setOnClickListener { onBuiltinAppClick?.invoke(item.app) }
                holder.itemView.setOnLongClickListener { true }
            }
        }

        // Animation tactile
        holder.itemView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120)
                        .setInterpolator(android.view.animation.OvershootInterpolator(2.5f)).start()
            }
            false
        }
    }
}

// Dash adapter
class DashAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo) -> Unit = {}
) : ListAdapter<AppInfo, DashAdapter.VH>(DIFF) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivDashIcon)
        val dot: View       = view.findViewById(R.id.dashActiveDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dash_icon, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = getItem(position)
        holder.icon.setImageDrawable(app.icon)
        holder.dot.visibility = View.VISIBLE
        holder.itemView.setOnClickListener { onAppClick(app) }
        holder.itemView.setOnLongClickListener { onAppLongClick(app); true }
        holder.itemView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().translationY(-6f).scaleX(1.12f).scaleY(1.12f).setDuration(100).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().translationY(0f).scaleX(1f).scaleY(1f).setDuration(150).start()
            }
            false
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(a: AppInfo, b: AppInfo) = a.packageName == b.packageName
            override fun areContentsTheSame(a: AppInfo, b: AppInfo) = a.label == b.label
        }
    }
}
