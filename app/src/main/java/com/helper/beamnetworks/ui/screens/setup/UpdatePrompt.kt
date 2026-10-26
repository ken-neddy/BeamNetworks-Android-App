package com.helper.beamnetworks.ui.screens.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.helper.beamnetworks.BuildConfig
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helper.beamnetworks.data.update.AppUpdater
import com.helper.beamnetworks.data.update.UpdateState
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamType

/** Offers a new version when the updater finds one, then walks through download and install. */
@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    val state by AppUpdater.state.collectAsStateWithLifecycle()
    val dismissed by AppUpdater.dismissed.collectAsStateWithLifecycle()
    // Re-read after coming back from the "install unknown apps" page
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    val canInstall = remember(resumes, state) { context.packageManager.canRequestPackageInstalls() }

    when (val s = state) {
        is UpdateState.Available -> if (s.info.versionCode != dismissed) {
            AlertDialog(
                onDismissRequest = AppUpdater::dismiss,
                title = { Text("Update available", style = BeamType.SectionTitle) },
                text = {
                    val releases = s.info.changesSince(BuildConfig.VERSION_NAME)
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        Text("BeamOps ${s.info.versionName} is ready to install. You have ${BuildConfig.VERSION_NAME}.", style = BeamType.Body)
                        if (s.info.notes.isNotBlank()) Text(s.info.notes, style = BeamType.Small, modifier = Modifier.padding(top = 8.dp))
                        releases.forEach { release ->
                            if (releases.size > 1) Text("Version ${release.version}", style = BeamType.RowTitle, modifier = Modifier.padding(top = 12.dp))
                            else Text("What's new", style = BeamType.RowTitle, modifier = Modifier.padding(top = 12.dp))
                            release.changes.forEach { change ->
                                Text(
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(change.title + ". ") }
                                        append(change.text)
                                    },
                                    style = BeamType.Small,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                },
                confirmButton = { TextButton({ AppUpdater.download(context) }) { Text("Update now") } },
                dismissButton = { TextButton(AppUpdater::dismiss) { Text("Later") } },
            )
        }
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading update", style = BeamType.SectionTitle) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(progress = { s.percent / 100f }, color = BeamColors.Primary, modifier = Modifier.fillMaxWidth())
                    Text("${s.percent}%", style = BeamType.Meta, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {},
        )
        is UpdateState.Ready -> AlertDialog(
            onDismissRequest = AppUpdater::retry,
            title = { Text("Ready to install", style = BeamType.SectionTitle) },
            text = {
                Text(
                    if (canInstall) "BeamOps ${s.info.versionName} has downloaded. Tap Install, then Update when Android asks."
                    else "Android needs your OK to install updates from BeamOps. Tap Continue, turn on \"Allow from this source\", then come back and tap Install.",
                    style = BeamType.Body,
                )
            },
            confirmButton = {
                TextButton({ AppUpdater.install(context, s.file) }) {
                    Text(if (canInstall) "Install" else "Continue")
                }
            },
            dismissButton = { TextButton(AppUpdater::retry) { Text("Cancel") } },
        )
        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = AppUpdater::retry,
            title = { Text("Update problem", style = BeamType.SectionTitle) },
            text = { Text(s.message, style = BeamType.Body) },
            confirmButton = { TextButton(AppUpdater::retry) { Text("OK") } },
        )
        else -> Unit
    }
}
