package com.gnomebazzite.launcher.manager

import android.content.Context
import android.util.Log
import com.gnomebazzite.launcher.data.BuiltinApp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Gestionnaire des applications intégrées compressées dans l'APK.
 *
 * Fonctionnement :
 *  - Les apps sont des fichiers .zip dans assets/builtin_apps/
 *  - Chaque zip contient : manifest.json + icon.png + les fichiers de l'app (HTML/JS/CSS)
 *  - "Installer" = décompresser le zip dans filesDir/installed_apps/<id>/
 *  - "Lancer" = ouvrir WebAppActivity avec le chemin vers index.html
 */
object BuiltinAppManager {

    private const val TAG = "BuiltinAppManager"
    private const val INSTALLED_DIR = "installed_apps"
    private const val PREFS_KEY = "installed_builtin_apps"
    private const val CATALOG_ASSET = "builtin_apps/catalog.json"

    // ──────────────────────────────────────────
    // CATALOGUE
    // ──────────────────────────────────────────

    suspend fun loadCatalog(context: Context): List<BuiltinApp> = withContext(Dispatchers.IO) {
        try {
            val json = context.assets.open(CATALOG_ASSET)
                .bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<BuiltinApp>>() {}.type
            val apps: List<BuiltinApp> = Gson().fromJson(json, type)
            val installedIds = getInstalledIds(context)
            apps.map { app ->
                app.copy(
                    isInstalled = installedIds.contains(app.id),
                    installedPath = if (installedIds.contains(app.id))
                        getInstallDir(context, app.id).absolutePath else null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur chargement catalogue", e)
            emptyList()
        }
    }

    // ──────────────────────────────────────────
    // INSTALLATION (décompression)
    // ──────────────────────────────────────────

    sealed class InstallResult {
        object Success : InstallResult()
        data class Error(val message: String) : InstallResult()
        data class Progress(val percent: Int, val currentFile: String) : InstallResult()
    }

    suspend fun installApp(
        context: Context,
        app: BuiltinApp,
        onProgress: (InstallResult.Progress) -> Unit
    ): InstallResult = withContext(Dispatchers.IO) {
        try {
            val installDir = getInstallDir(context, app.id)
            if (installDir.exists()) installDir.deleteRecursively()
            installDir.mkdirs()

            val assetStream = context.assets.open(app.assetPath)
            val zipStream = ZipInputStream(assetStream.buffered())

            // Compter les entrées pour la progression
            val countStream = ZipInputStream(context.assets.open(app.assetPath).buffered())
            var totalEntries = 0
            while (countStream.nextEntry != null) totalEntries++
            countStream.close()

            var processed = 0
            var entry = zipStream.nextEntry

            while (entry != null) {
                val entryName = entry.name
                val outFile = File(installDir, entryName)

                withContext(Dispatchers.Main) {
                    onProgress(InstallResult.Progress(
                        percent = if (totalEntries > 0) (processed * 100 / totalEntries) else 0,
                        currentFile = entryName
                    ))
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out ->
                        zipStream.copyTo(out)
                    }
                }

                processed++
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
            zipStream.close()

            // Marquer comme installée
            markInstalled(context, app.id)

            Log.d(TAG, "App ${app.id} installée dans ${installDir.absolutePath}")
            InstallResult.Success

        } catch (e: Exception) {
            Log.e(TAG, "Erreur installation ${app.id}", e)
            InstallResult.Error(e.message ?: "Erreur inconnue")
        }
    }

    // ──────────────────────────────────────────
    // DÉSINSTALLATION
    // ──────────────────────────────────────────

    suspend fun uninstallApp(context: Context, appId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            getInstallDir(context, appId).deleteRecursively()
            removeInstalled(context, appId)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erreur désinstallation $appId", e)
            false
        }
    }

    // ──────────────────────────────────────────
    // LANCEMENT
    // ──────────────────────────────────────────

    fun getEntryPointPath(context: Context, app: BuiltinApp): String {
        val dir = getInstallDir(context, app.id)
        return File(dir, app.entryFile).absolutePath
    }

    // ──────────────────────────────────────────
    // UTILITAIRES PRIVÉS
    // ──────────────────────────────────────────

    private fun getInstallDir(context: Context, appId: String): File =
        File(context.filesDir, "$INSTALLED_DIR/$appId")

    private fun getInstalledIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
        return prefs.getStringSet("ids", emptySet()) ?: emptySet()
    }

    private fun markInstalled(context: Context, appId: String) {
        val prefs = context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
        val current = prefs.getStringSet("ids", mutableSetOf())!!.toMutableSet()
        current.add(appId)
        prefs.edit().putStringSet("ids", current).apply()
    }

    private fun removeInstalled(context: Context, appId: String) {
        val prefs = context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
        val current = prefs.getStringSet("ids", mutableSetOf())!!.toMutableSet()
        current.remove(appId)
        prefs.edit().putStringSet("ids", current).apply()
    }

    fun getTotalInstalledCount(context: Context): Int = getInstalledIds(context).size

    suspend fun getInstalledSize(context: Context): Long = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, INSTALLED_DIR)
        if (!dir.exists()) return@withContext 0L
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
