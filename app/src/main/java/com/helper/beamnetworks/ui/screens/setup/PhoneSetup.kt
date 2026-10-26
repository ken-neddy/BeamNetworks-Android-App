package com.helper.beamnetworks.ui.screens.setup

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.helper.beamnetworks.data.update.AppUpdater
import com.helper.beamnetworks.ui.components.BeamCard
import com.helper.beamnetworks.ui.components.IconTile
import com.helper.beamnetworks.ui.components.PrimaryButton
import com.helper.beamnetworks.ui.components.SecondaryButton
import com.helper.beamnetworks.ui.icons.BeamIcons
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamType
import com.helper.beamnetworks.ui.theme.TonePair
import com.helper.beamnetworks.ui.theme.Tones

/** One thing the app needs from the phone, and why, shown as a step of the setup. */
private data class SetupStep(
    val key: String,
    val icon: ImageVector,
    val tone: TonePair,
    val title: String,
    val why: String,
    val uses: List<String>,
    val allowLabel: String,
    /** Runtime permissions asked with the system prompt; empty for steps that open a Settings page. */
    val permissions: List<String> = emptyList(),
    val granted: (Context) -> Boolean,
)

private fun has(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun ignoresBatteryLimits(context: Context) =
    (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)

private val steps: List<SetupStep> = listOfNotNull(
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) SetupStep(
        key = "notifications",
        icon = BeamIcons.Bell, tone = Tones.Orange,
        title = "Notifications",
        why = "So the app can alert you the moment something needs you, even when it's closed.",
        uses = listOf("Routers going offline and outages", "Payments received, suspensions and reconnections", "New tickets and missed SLAs", "Low SMS, PayHero or stock balances"),
        allowLabel = "Allow notifications",
        permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
        granted = { has(it, Manifest.permission.POST_NOTIFICATIONS) },
    ) else null,
    SetupStep(
        key = "calls",
        icon = BeamIcons.Phone, tone = Tones.Teal,
        title = "Phone and call log",
        why = "So the app can tell who is calling and let you pick a number instead of typing it.",
        uses = listOf("Caller ID: shows the customer's account when they call", "Pick a number from recent calls when adding a customer, job or ticket", "The Call log screen, with known customers highlighted"),
        allowLabel = "Allow phone access",
        permissions = listOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG),
        granted = { has(it, Manifest.permission.READ_PHONE_STATE) && has(it, Manifest.permission.READ_CALL_LOG) },
    ),
    SetupStep(
        key = "overlay",
        icon = BeamIcons.ExternalLink, tone = Tones.Blue,
        title = "Show over other apps",
        why = "Caller ID pops up on top of the incoming call screen, which Android only allows with this setting.",
        uses = listOf("On the next screen, find Beam OPS and turn on \"Allow display over other apps\", then come back"),
        allowLabel = "Open the setting",
        granted = { Settings.canDrawOverlays(it) },
    ),
    SetupStep(
        key = "battery",
        icon = BeamIcons.Activity, tone = Tones.Red,
        title = "Battery: no restrictions",
        why = "Android's battery saver stops apps working in the background. Without this, alerts arrive late or not at all, and caller ID may not appear.",
        uses = listOf("Checks for outages, payments and tickets every 15 minutes in the background", "Keeps caller ID ready when a call comes in", "Tap Allow on the next prompt. It uses very little battery"),
        allowLabel = "Allow background use",
        granted = ::ignoresBatteryLimits,
    ),
    SetupStep(
        key = "sms",
        icon = BeamIcons.MessageSquare, tone = Tones.Green,
        title = "M-Pesa messages",
        why = "So the app can read your M-Pesa SMS and fill in payments and expenses for you. Messages are read on this phone only.",
        uses = listOf("Fill a payment's amount and M-Pesa code from a message", "Log an expense from a message: amount, who was paid and the date"),
        allowLabel = "Allow SMS access",
        permissions = listOf(Manifest.permission.READ_SMS),
        granted = { has(it, Manifest.permission.READ_SMS) },
    ),
    SetupStep(
        key = "sendsms",
        icon = BeamIcons.Send, tone = Tones.Teal,
        title = "Text technicians",
        why = "So the app can SMS a technician the moment you assign them a job. The text is sent from this phone's SIM, so your normal SMS rates apply.",
        uses = listOf("Sends the customer, place, time and whether they have a router and PoE", "Only sent when a technician is newly assigned to a job"),
        allowLabel = "Allow sending SMS",
        permissions = listOf(Manifest.permission.SEND_SMS),
        granted = { has(it, Manifest.permission.SEND_SMS) },
    ),
    SetupStep(
        key = "install",
        icon = BeamIcons.Refresh, tone = Tones.Blue,
        title = "Install app updates",
        why = "Beam OPS isn't on the Play Store, so it updates itself. Android needs your OK before an app can install a new version of itself.",
        uses = listOf("The app tells you when a new version is ready and downloads it", "Android always asks you to confirm before anything is installed", "On the next screen, turn on \"Allow from this source\", then come back"),
        allowLabel = "Open the setting",
        granted = { it.packageManager.canRequestPackageInstalls() },
    ),
    SetupStep(
        key = "location",
        icon = BeamIcons.MapPin, tone = Tones.Purple,
        title = "Location",
        why = "So you can drop a customer's map pin from where you are standing during an installation.",
        uses = listOf("Pin a customer's location on the map", "Share your location with the team"),
        allowLabel = "Allow location",
        permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        granted = { has(it, Manifest.permission.ACCESS_FINE_LOCATION) || has(it, Manifest.permission.ACCESS_COARSE_LOCATION) },
    ),
)

/** Whether the phone setup is showing; it opens on start-up when something is missing, or from Settings. */
object PhoneSetup {
    private const val PREFS = "beam_phone_setup"
    private const val ANSWERED = "answered"

    var visible by mutableStateOf(false)
        private set

    private fun answered(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(ANSWERED, emptySet()).orEmpty()

    internal fun markAnswered(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit { putStringSet(ANSWERED, answered(context) + key) }
    }

    /** Steps not yet allowed; on start-up, steps already answered (allowed or skipped) aren't asked again. */
    internal fun missing(context: Context, includeAnswered: Boolean): List<String> {
        val done = if (includeAnswered) emptySet() else answered(context)
        return steps.filter { !it.granted(context) && it.key !in done }.map { it.key }
    }

    /** Called once at start-up. */
    fun showIfNeeded(context: Context) {
        if (missing(context, includeAnswered = false).isNotEmpty()) visible = true
    }

    /** From Settings: walk through everything still missing, including steps skipped before. */
    fun open(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { remove(ANSWERED) }
        visible = true
    }

    fun allGranted(context: Context) = steps.all { it.granted(context) }

    internal fun close() {
        visible = false
    }
}

/** Opens the system prompt that takes this app off battery optimisation, or the closest Settings page. */
@SuppressLint("BatteryLife") // A staff tool that has to alert in the background; not a Play Store app
private fun openBatterySetting(context: Context) {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
    val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
    for (intent in listOf(direct, list, details)) {
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun openOverlaySetting(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
    }
}

/** Full-screen walk-through asking for each missing permission in turn, explaining why it's needed. */
@Composable
fun PhoneSetupScreen() {
    val context = LocalContext.current
    // The steps to walk through are fixed when the screen opens, so the "Step x of y" count stays put
    val planned = remember { PhoneSetup.missing(context, includeAnswered = false).ifEmpty { PhoneSetup.missing(context, includeAnswered = true) } }
    var index by remember { mutableIntStateOf(0) }
    // Bumped when the app resumes, so "granted" is re-read after a Settings page or prompt
    var refresh by remember { mutableIntStateOf(0) }
    var waitingOnSettings by remember { mutableStateOf<String?>(null) }

    fun advance() {
        var next = index + 1
        while (next < planned.size && steps.first { it.key == planned[next] }.granted(context)) next++
        if (next >= planned.size) PhoneSetup.close() else index = next
    }

    LifecycleResumeEffect(Unit) {
        refresh++
        waitingOnSettings?.let { key ->
            // Back from Settings: move on whether or not it was turned on
            waitingOnSettings = null
            PhoneSetup.markAnswered(context, key)
            advance()
        }
        onPauseOrDispose { }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val key = planned[index]
        val denied = result.filterValues { !it }.keys
        val activity = context.findActivity()
        // Android stops showing the prompt after it's been refused twice; send them to the app's page instead
        if (denied.isNotEmpty() && activity != null && denied.none { activity.shouldShowRequestPermissionRationale(it) }) {
            waitingOnSettings = key
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
        } else {
            PhoneSetup.markAnswered(context, key)
            advance()
        }
    }

    if (planned.isEmpty()) {
        PhoneSetup.close()
        return
    }
    val step = steps.first { it.key == planned[index] }
    val granted = remember(step, refresh) { step.granted(context) }

    fun skip() {
        PhoneSetup.markAnswered(context, step.key)
        advance()
    }

    BackHandler { skip() }

    Column(
        Modifier
            .fillMaxSize()
            .background(BeamColors.Background)
            // Keep taps from reaching the app underneath
            .pointerInput(Unit) { detectTapGestures { } }
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text("Set up this phone", style = BeamType.SectionTitle)
        Text("Beam OPS needs a few permissions to work fully. Each one is explained before you choose.", style = BeamType.Small.copy(color = BeamColors.TextSecondary), modifier = Modifier.padding(top = 4.dp))
        // Progress: one bar per step
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            planned.indices.forEach { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(if (i <= index) BeamColors.Primary else BeamColors.InputFill, RoundedCornerShape(2.dp)),
                )
            }
        }
        BeamCard(Modifier.padding(top = 20.dp), padding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
            IconTile(step.icon, step.tone, size = 56.dp, iconSize = 26.dp)
            Text("Step ${index + 1} of ${planned.size}", style = BeamType.Meta, modifier = Modifier.padding(top = 16.dp))
            Text(step.title, style = BeamType.EmptyTitle, modifier = Modifier.padding(top = 2.dp))
            Text(step.why, style = BeamType.Body, modifier = Modifier.padding(top = 10.dp))
            Text("What it's used for", style = BeamType.Label, modifier = Modifier.padding(top = 16.dp))
            step.uses.forEach { use ->
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                    Text("•", style = BeamType.Body, modifier = Modifier.padding(end = 8.dp))
                    Text(use, style = BeamType.Small)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        if (granted) {
            PrimaryButton("Allowed. Continue", ::advance)
        } else {
            PrimaryButton(step.allowLabel, {
                when (step.key) {
                    "overlay" -> {
                        waitingOnSettings = step.key
                        openOverlaySetting(context)
                    }
                    "install" -> {
                        waitingOnSettings = step.key
                        AppUpdater.openInstallPermission(context)
                    }
                    "battery" -> {
                        waitingOnSettings = step.key
                        openBatterySetting(context)
                    }
                    else -> launcher.launch(step.permissions.toTypedArray())
                }
            })
            SecondaryButton("Not now", ::skip, Modifier.padding(top = 8.dp))
        }
        Text(
            "You can come back to this any time from Settings › This phone › Phone permissions.",
            style = BeamType.Meta,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
