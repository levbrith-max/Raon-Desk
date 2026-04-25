package com.gnomebazzite.launcher.data

import android.graphics.drawable.Drawable
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable?,
    val isPinned: Boolean = false,
    val isSystem: Boolean = false
)

@Parcelize
data class BuiltinApp(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val author: String,
    val sizeKb: Int,
    val category: String,
    val assetPath: String,       // chemin dans assets/builtin_apps/
    val iconEmoji: String,
    val iconColor: String,       // hex color
    val entryFile: String = "index.html",
    var isInstalled: Boolean = false,
    var installedPath: String? = null
) : Parcelable

enum class AppCategory(val label: String) {
    UTILITIES("Utilitaires"),
    GAMES("Jeux"),
    PRODUCTIVITY("Productivité"),
    MEDIA("Médias"),
    SYSTEM("Système"),
    DEVELOPMENT("Développement")
}
