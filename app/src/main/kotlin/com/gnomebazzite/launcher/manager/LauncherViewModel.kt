package com.gnomebazzite.launcher.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gnomebazzite.launcher.data.AppInfo
import com.gnomebazzite.launcher.data.BuiltinApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel : ViewModel() {

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _pinnedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val pinnedApps: StateFlow<List<AppInfo>> = _pinnedApps.asStateFlow()

    private val _builtinApps = MutableStateFlow<List<BuiltinApp>>(emptyList())
    val builtinApps: StateFlow<List<BuiltinApp>> = _builtinApps.asStateFlow()

    private val _isDrawerOpen = MutableStateFlow(false)
    val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredApps get() = if (_searchQuery.value.isBlank()) _installedApps.value
        else _installedApps.value.filter {
            it.label.contains(_searchQuery.value, ignoreCase = true)
        }

    private val defaultPinnedPackages = listOf(
        "com.android.chrome",
        "com.android.settings",
        "com.android.vending",
        "com.google.android.apps.photos",
        "com.google.android.apps.messaging"
    )

    fun loadApps(context: Context) {
        viewModelScope.launch {
            val apps = queryInstalledApps(context)
            _installedApps.value = apps.sortedBy { it.label.lowercase() }

            // Pinned = apps par défaut présentes + les premières
            val pinned = apps.filter { app ->
                defaultPinnedPackages.any { app.packageName.contains(it.substringAfterLast('.')) }
            }.take(6).ifEmpty { apps.take(5) }
            _pinnedApps.value = pinned

            // Charger le catalogue des apps intégrées
            _builtinApps.value = BuiltinAppManager.loadCatalog(context)
        }
    }

    private suspend fun queryInstalledApps(context: Context): List<AppInfo> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
            resolveInfos
                .filter { it.activityInfo.packageName != context.packageName }
                .map { ri ->
                    AppInfo(
                        label = ri.loadLabel(pm).toString(),
                        packageName = ri.activityInfo.packageName,
                        activityName = ri.activityInfo.name,
                        icon = ri.loadIcon(pm)
                    )
                }
        }

    fun setDrawerOpen(open: Boolean) { _isDrawerOpen.value = open }
    fun toggleDrawer() { _isDrawerOpen.value = !_isDrawerOpen.value }

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun pinApp(app: AppInfo) {
        val current = _pinnedApps.value.toMutableList()
        if (!current.any { it.packageName == app.packageName }) {
            current.add(app.copy(isPinned = true))
            _pinnedApps.value = current
        }
    }

    fun unpinApp(packageName: String) {
        _pinnedApps.value = _pinnedApps.value.filter { it.packageName != packageName }
    }

    fun launchApp(context: Context, app: AppInfo) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            setDrawerOpen(false)
        } catch (e: Exception) { /* ignore */ }
    }

    fun refreshBuiltinApps(context: Context) {
        viewModelScope.launch {
            _builtinApps.value = BuiltinAppManager.loadCatalog(context)
        }
    }
}
