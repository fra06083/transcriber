package com.android.transcriber.domain.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "AppUpdateManager"
private const val GITHUB_REPO = "fra06083/transcriber"
private const val PREFS_NAME = "transcriber_settings"
private const val KEY_AUTO_CHECK = "auto_update_check_enabled"

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val changelog: String,
    val downloadUrl: String,
    val publishedAt: String,
    val isUpdateAvailable: Boolean,
    val currentVersion: String
)

class AppUpdateManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Auto-check preference (disabled by default)
    fun isAutoCheckEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_CHECK, false)
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK, enabled).apply()
    }

    fun getCurrentVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.2"
        } catch (_: Exception) {
            "1.2"
        }
    }

    // Check latest release via GitHub API
    suspend fun checkLatestRelease(): Result<AppReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val apiUrl = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
            val url = URL(apiUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", "TranscriberApp")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("GitHub server error ($responseCode)")
            }

            val responseText = connection.inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            }

            val json = JSONObject(responseText)
            val tagName = json.optString("tag_name", "").trim()
            val remoteVersion = tagName.removePrefix("v").removePrefix("V").trim()
            val title = json.optString("name", tagName).trim()
            val changelog = json.optString("body", "").trim()
            val publishedAt = json.optString("published_at", "").trim()

            // Look for APK release asset
            var downloadUrl = ""
            val assetsArray = json.optJSONArray("assets")
            if (assetsArray != null) {
                for (i in 0 until assetsArray.length()) {
                    val asset = assetsArray.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }

            // Fallback to release web page
            if (downloadUrl.isBlank()) {
                downloadUrl = json.optString("html_url", "https://github.com/$GITHUB_REPO/releases/latest")
            }

            val currentVersion = getCurrentVersionName()
            val isNewer = isRemoteVersionNewer(remote = remoteVersion, current = currentVersion)

            Log.i(TAG, "Update check: remote=$remoteVersion, local=$currentVersion, available=$isNewer")

            AppReleaseInfo(
                tagName = tagName,
                versionName = remoteVersion,
                title = title.ifBlank { "Transcriber $tagName" },
                changelog = changelog,
                downloadUrl = downloadUrl,
                publishedAt = publishedAt,
                isUpdateAvailable = isNewer,
                currentVersion = currentVersion
            )
        }
    }

    // Compare semantic version numbers
    fun isRemoteVersionNewer(remote: String, current: String): Boolean {
        val rClean = remote.trim().removePrefix("v").removePrefix("V")
        val cClean = current.trim().removePrefix("v").removePrefix("V")

        val rParts = rClean.split(".").mapNotNull { it.takeWhile { char -> char.isDigit() }.toIntOrNull() }
        val cParts = cClean.split(".").mapNotNull { it.takeWhile { char -> char.isDigit() }.toIntOrNull() }

        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    // Open browser to download APK
    fun openDownloadUrl(downloadUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open download url", e)
        }
    }
}
