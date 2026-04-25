package com.gnomebazzite.launcher.ui.common

import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gnomebazzite.launcher.R
import com.gnomebazzite.launcher.data.AppInfo

class AppGridAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppGridAdapter.VH>(DIFF) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivAppIcon)
        val name: TextView  = view.findViewById(R.id.tvAppName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_grid, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = getItem(position)
        holder.icon.setImageDrawable(app.icon)
        holder.name.text = app.label
        holder.itemView.setOnClickListener { onAppClick(app) }
        holder.itemView.setOnLongClickListener { onAppLongClick(app); true }

        // Hover effect
        holder.itemView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120)
                        .setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
                }
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

class DashAdapter(
    private val onAppClick: (AppInfo) -> Unit
) : ListAdapter<AppInfo, DashAdapter.VH>(DIFF) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivDashIcon)
        val dot: View = view.findViewById(R.id.dashActiveDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_dash_icon, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = getItem(position)
        holder.icon.setImageDrawable(app.icon)
        holder.dot.visibility = View.VISIBLE // toutes les apps épinglées sont "ouvertes"
        holder.itemView.setOnClickListener { onAppClick(app) }
        holder.itemView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.animate().translationY(-6f).scaleX(1.1f).scaleY(1.1f).setDuration(100).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().translationY(0f).scaleX(1f).scaleY(1f).setDuration(150).start()
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
