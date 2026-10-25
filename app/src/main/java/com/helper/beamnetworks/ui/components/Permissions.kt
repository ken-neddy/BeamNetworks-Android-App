package com.helper.beamnetworks.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Whether caller ID can work: overlay allowed plus phone-state and call-log access. */
fun callerIdReady(context: Context): Boolean =
    Settings.canDrawOverlays(context) &&
        listOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG)
            .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

/** [callerIdReady], re-checked whenever the screen resumes (e.g. back from Settings). */
@Composable
fun rememberCallerIdReady(): Boolean {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(callerIdReady(context)) }
    LifecycleResumeEffect(Unit) {
        ready = callerIdReady(context)
        onPauseOrDispose { }
    }
    return ready
}

/** Opens the screen where the user turns on what's missing for caller ID. */
fun openCallerIdSettings(context: Context) {
    val intent = if (!Settings.canDrawOverlays(context)) {
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
    }
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
