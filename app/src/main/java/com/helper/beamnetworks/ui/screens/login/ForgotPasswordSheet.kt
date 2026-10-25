package com.helper.beamnetworks.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helper.beamnetworks.data.AppContainer
import com.helper.beamnetworks.data.api.ApiException
import com.helper.beamnetworks.data.api.PasswordResetConfirm
import com.helper.beamnetworks.data.api.PasswordResetRequest
import com.helper.beamnetworks.data.api.apiCall
import com.helper.beamnetworks.ui.components.BeamSheet
import com.helper.beamnetworks.ui.components.FieldLabel
import com.helper.beamnetworks.ui.components.FormField
import com.helper.beamnetworks.ui.components.TextLink
import com.helper.beamnetworks.ui.navigation.containerViewModel
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamShapes
import com.helper.beamnetworks.ui.theme.BeamType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ResetStep { Request, Confirm, Done }

/**
 * Forgot password for Beam staff, the same flow as the website: the phone number on the account gets a 6-digit
 * SMS code (POST auth/tenant/password-reset/request/), then the code and a new password confirm it (.../confirm/).
 */
class ForgotPasswordViewModel(private val app: AppContainer) : ViewModel() {
    var step by mutableStateOf(ResetStep.Request)
        private set
    var phone by mutableStateOf("")
    var code by mutableStateOf("")
        private set
    var password by mutableStateOf("")
    var repeat by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var resendIn by mutableIntStateOf(0)
        private set

    fun enterCode(value: String) {
        code = value.filter { it.isDigit() }.take(CODE_LENGTH)
    }

    val canRequest get() = phone.filter { it.isDigit() }.length >= 9 && !busy
    val canConfirm get() = code.length == CODE_LENGTH && password.length >= 8 && repeat.isNotEmpty() && !busy

    fun requestCode(resend: Boolean = false) {
        if (!canRequest) return
        busy = true
        error = null
        notice = null
        viewModelScope.launch {
            try {
                apiCall { app.api.requestPasswordReset(PasswordResetRequest(phone.trim())) }
                step = ResetStep.Confirm
                if (resend) {
                    code = ""
                    notice = "A new code is on its way. The previous code no longer works."
                }
                startCountdown()
            } catch (e: ApiException) {
                error = if (e.status == 429) "Too many attempts. Wait a minute before requesting another code." else e.message
            } finally {
                busy = false
            }
        }
    }

    fun confirm() {
        if (password != repeat) {
            error = "The two passwords do not match."
            return
        }
        if (!canConfirm) return
        busy = true
        error = null
        notice = null
        viewModelScope.launch {
            try {
                apiCall { app.api.confirmPasswordReset(PasswordResetConfirm(phone.trim(), code, password)) }
                step = ResetStep.Done
            } catch (e: ApiException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    fun back() {
        step = ResetStep.Request
        error = null
        notice = null
    }

    private fun startCountdown() {
        resendIn = RESEND_SECONDS
        viewModelScope.launch {
            while (resendIn > 0) {
                delay(1_000)
                resendIn -= 1
            }
        }
    }

    companion object {
        const val CODE_LENGTH = 6
        const val RESEND_SECONDS = 60
    }
}

@Composable
fun ForgotPasswordSheet(onDismiss: () -> Unit) {
    val vm = containerViewModel(key = "forgot-password") { app, _ -> ForgotPasswordViewModel(app) }
    val title = when (vm.step) {
        ResetStep.Request -> "Reset your password"
        ResetStep.Confirm -> "Enter the code"
        ResetStep.Done -> "Password changed"
    }
    BeamSheet(
        title = title,
        onDismiss = onDismiss,
        toast = null,
        ctaLabel = when (vm.step) {
            ResetStep.Request -> if (vm.busy) "Sending..." else "Send reset code"
            ResetStep.Confirm -> if (vm.busy) "Saving..." else "Change password"
            ResetStep.Done -> "Back to sign in"
        },
        onCta = {
            when (vm.step) {
                ResetStep.Request -> vm.requestCode()
                ResetStep.Confirm -> vm.confirm()
                ResetStep.Done -> onDismiss()
            }
        },
        ctaEnabled = when (vm.step) {
            ResetStep.Request -> vm.canRequest
            ResetStep.Confirm -> vm.canConfirm
            ResetStep.Done -> true
        },
    ) {
        when (vm.step) {
            ResetStep.Request -> {
                Text("Enter the phone number on your Beam account and we will text you a 6-digit code.", style = BeamType.Small.copy(color = BeamColors.TextMuted))
                FormField("Phone number", vm.phone, { vm.phone = it }, placeholder = "07... or +254...", keyboardType = KeyboardType.Phone)
            }
            ResetStep.Confirm -> {
                Text("We texted a code to ${vm.phone.trim()}. It expires in 10 minutes.", style = BeamType.Small.copy(color = BeamColors.TextMuted))
                FormField("6-digit code", vm.code, vm::enterCode, placeholder = "123456", keyboardType = KeyboardType.NumberPassword)
                MaskedField("New password (at least 8 characters)", vm.password) { vm.password = it }
                MaskedField("Repeat new password", vm.repeat) { vm.repeat = it }
                if (vm.resendIn > 0) {
                    Text("Send a new code in ${vm.resendIn}s", style = BeamType.Meta)
                } else {
                    TextLink("Send a new code", { vm.requestCode(resend = true) })
                }
                TextLink("Use a different number", vm::back)
            }
            ResetStep.Done -> Text("Your password has been changed. Sign in with the new password.", style = BeamType.Small.copy(color = BeamColors.TextMuted))
        }
        vm.notice?.let { Text(it, style = BeamType.Meta.copy(color = BeamColors.Primary)) }
        vm.error?.let { Text(it, style = BeamType.Meta.copy(color = BeamColors.Danger)) }
    }
}

@Composable
private fun MaskedField(label: String, value: String, onValueChange: (String) -> Unit) {
    FieldLabel(label) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = BeamType.Body,
            cursorBrush = SolidColor(BeamColors.Primary),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().background(BeamColors.InputFill, BeamShapes.Input).padding(horizontal = 12.dp, vertical = 14.dp)) { inner() }
            },
        )
    }
}
