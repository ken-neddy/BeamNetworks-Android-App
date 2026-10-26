package com.helper.beamnetworks.data.update

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.helper.beamnetworks.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** What `version.json` on the download site says about the newest build. */
@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apk: String,
    val sha256: String,
    val notes: String = "",
    /** What changed in each release, newest first. */
    val history: List<ReleaseNotes> = emptyList(),
) {
    /** The releases newer than the installed version, so someone skipping versions sees every change. */
    fun changesSince(installed: String): List<ReleaseNotes> = history.filter { compareVersions(it.version, installed) > 0 }
}

@Serializable
data class ReleaseNotes(val version: String, val changes: List<ReleaseChange> = emptyList())

@Serializable
data class ReleaseChange(val title: String, val text: String = "")

/** Compares dotted versions such as "1.10" and "1.9" numerically. */
fun compareVersions(a: String, b: String): Int {
    val x = a.split('.').map { it.toIntOrNull() ?: 0 }
    val y = b.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(x.size, y.size)) {
        val diff = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
        if (diff != 0) return diff
    }
    return 0
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Updates the app without the Play Store: reads `version.json` from the download site, downloads
 * the new signed APK, checks its SHA-256 and hands it to Android's installer. Android only accepts
 * it if it is signed with the same key as the installed app.
 */
object AppUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Version the person chose "Later" for, so the prompt doesn't come back until the app restarts. */
    private val _dismissed = MutableStateFlow(0)
    val dismissed: StateFlow<Int> = _dismissed.asStateFlow()

    fun dismiss() {
        (state.value as? UpdateState.Available)?.let { _dismissed.value = it.info.versionCode }
    }

    /** Looks for a newer version. A silent check (on start-up) stays quiet when it can't reach the site. */
    fun check(silent: Boolean) {
        val current = state.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        scope.launch {
            _state.value = try {
                val info = fetchInfo()
                if (info.versionCode > BuildConfig.VERSION_CODE) UpdateState.Available(info) else UpdateState.UpToDate
            } catch (e: Exception) {
                if (silent) UpdateState.Idle else UpdateState.Failed("Couldn't check for updates. Check your connection and try again.")
            }
        }
    }

    /** Downloads the update, verifies it, then moves to [UpdateState.Ready]. */
    fun download(context: Context) {
        val info = (state.value as? UpdateState.Available)?.info ?: return
        val appContext = context.applicationContext
        scope.launch {
            _state.value = UpdateState.Downloading(info, 0)
            _state.value = try {
                UpdateState.Ready(info, fetchApk(appContext, info))
            } catch (e: Exception) {
                UpdateState.Failed(e.message?.takeIf { it.startsWith("The update") } ?: "The download failed. Try again.")
            }
        }
    }

    /** Opens Android's installer for a downloaded update; false if the install permission still has to be given. */
    fun install(context: Context, file: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            openInstallPermission(context)
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun retry() {
        _state.value = UpdateState.Idle
    }

    private fun fetchInfo(): UpdateInfo {
        val request = Request.Builder().url(BuildConfig.UPDATE_BASE_URL + "version.json").header("Cache-Control", "no-cache").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return json.decodeFromString(UpdateInfo.serializer(), response.body?.string() ?: error("Empty response"))
        }
    }

    private fun fetchApk(context: Context, info: UpdateInfo): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "BeamOps-${info.versionName}.apk")
        val request = Request.Builder().url(BuildConfig.UPDATE_BASE_URL + info.apk).build()
        val digest = MessageDigest.getInstance("SHA-256")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("Empty response")
            val total = body.contentLength()
            var read = 0L
            var lastPercent = -1
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        if (total > 0) {
                            val percent = (read * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _state.value = UpdateState.Downloading(info, percent)
                            }
                        }
                    }
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(info.sha256, ignoreCase = true)) {
            target.delete()
            error("The update file didn't match and was discarded. Try again.")
        }
        return target
    }
}
