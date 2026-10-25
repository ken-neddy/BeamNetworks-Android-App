package com.helper.beamnetworks.ui.screens.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helper.beamnetworks.R
import com.helper.beamnetworks.data.AppContainer
import com.helper.beamnetworks.data.api.ApiException
import com.helper.beamnetworks.ui.components.FieldLabel
import com.helper.beamnetworks.ui.components.FormField
import com.helper.beamnetworks.ui.components.PrimaryButton
import com.helper.beamnetworks.ui.navigation.containerViewModel
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamShapes
import com.helper.beamnetworks.ui.theme.BeamType
import kotlinx.coroutines.launch

class LoginViewModel(private val app: AppContainer) : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            error = "Enter your email and password"
            return
        }
        busy = true
        error = null
        viewModelScope.launch {
            try {
                val session = app.login(email, password)
                if (session.mustChangePassword) {
                    app.logout()
                    error = "Set a new password on the Beam web dashboard first, then sign in here."
                }
            } catch (e: ApiException) {
                error = if (e.status == 401) "Wrong email or password" else e.message
            } finally {
                busy = false
            }
        }
    }
}

/** Tenant-staff sign in (POST auth/tenant/login/). Not part of the handoff; styled to match it. */
@Composable
fun LoginScreen() {
    val vm = containerViewModel { app, _ -> LoginViewModel(app) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .background(BeamColors.Background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            painterResource(R.drawable.beam_logo),
            contentDescription = "Beam OPS",
            modifier = Modifier
                .padding(top = 24.dp, bottom = 8.dp)
                .size(88.dp)
                .clip(RoundedCornerShape(24.dp))
                .align(Alignment.CenterHorizontally),
        )
        Text("Welcome back", style = BeamType.Greeting, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            "Sign in with your Beam staff account",
            style = BeamType.Small.copy(color = BeamColors.TextMuted),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp),
        )
        Column(
            Modifier.background(BeamColors.Surface, BeamShapes.CardLarge).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FormField("Email", email, { email = it }, placeholder = "you@isp.co.ke", keyboardType = KeyboardType.Email)
            FieldLabel("Password") {
                BasicTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    textStyle = BeamType.Body,
                    cursorBrush = SolidColor(BeamColors.Primary),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { vm.login(email, password) }),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        androidx.compose.foundation.layout.Box(
                            Modifier
                                .fillMaxWidth()
                                .background(BeamColors.InputFill, BeamShapes.Input)
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                        ) { inner() }
                    },
                )
            }
            vm.error?.let { Text(it, style = BeamType.Meta.copy(color = BeamColors.Danger)) }
            PrimaryButton(if (vm.busy) "Signing in…" else "Sign in", { vm.login(email, password) }, enabled = !vm.busy)
        }
        Text(
            "Forgot your password? Reset it from the Beam web dashboard.",
            style = BeamType.Meta,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
        )
    }
}
