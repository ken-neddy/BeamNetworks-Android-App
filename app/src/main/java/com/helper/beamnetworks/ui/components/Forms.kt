package com.helper.beamnetworks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.helper.beamnetworks.ui.icons.BeamIcons
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamShapes
import com.helper.beamnetworks.ui.theme.BeamType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Bottom sheet from the handoff: white, 28dp top radius, 40x4 handle, title + close circle,
 * children stacked 14dp apart, optional full-width CTA. [toast] is drawn inside the sheet because
 * the sheet sits in its own window above the app's toast.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeamSheet(
    title: String,
    onDismiss: () -> Unit,
    toast: String?,
    ctaLabel: String? = null,
    onCta: () -> Unit = {},
    ctaEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = BeamShapes.Sheet,
        containerColor = BeamColors.Surface,
        scrimColor = BeamColors.Scrim,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(BeamColors.SheetHandle, BeamShapes.Pill),
            )
        },
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = BeamType.SheetTitle, modifier = Modifier.weight(1f))
                    CircleButton(BeamIcons.X, "Close", onDismiss, size = 34.dp, iconSize = 16.dp, color = BeamColors.InputFill)
                }
                content()
                if (ctaLabel != null) {
                    PrimaryButton(ctaLabel, onCta, modifier = Modifier.padding(top = 4.dp), enabled = ctaEnabled)
                }
            }
            BeamToast(toast, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Label above, 6dp gap, then the field. */
@Composable
fun FieldLabel(label: String, modifier: Modifier = Modifier, field: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = BeamType.Label)
        field()
    }
}

/** Borderless filled input (48dp, radius 12, #F3F4F6). [large] = 52dp/20sp amount input. */
@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    large: Boolean = false,
    singleLine: Boolean = true,
    trailingIcon: ImageVector? = null,
    trailingLabel: String? = null,
    onTrailing: (() -> Unit)? = null,
) {
    FieldLabel(label, modifier) {
        InputBox(value, onValueChange, placeholder, keyboardType, large, singleLine, trailingIcon, trailingLabel, onTrailing)
    }
}

@Composable
fun InputBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    large: Boolean = false,
    singleLine: Boolean = true,
    trailingIcon: ImageVector? = null,
    trailingLabel: String? = null,
    onTrailing: (() -> Unit)? = null,
    height: Dp = if (large) 52.dp else 48.dp,
    fill: Color = BeamColors.InputFill,
    shape: androidx.compose.ui.graphics.Shape = BeamShapes.Input,
    leadingIcon: ImageVector? = null,
) {
    val textStyle: TextStyle = if (large) BeamType.Hero.copy(fontSize = BeamType.EmptyTitle.fontSize, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = BeamType.Body.letterSpacing)
    else BeamType.Body
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .background(fill, shape)
            .padding(start = if (leadingIcon != null) 14.dp else 12.dp, end = if (trailingIcon != null) 4.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (leadingIcon != null) Icon(leadingIcon, null, tint = BeamColors.TextMuted, modifier = Modifier.size(17.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = textStyle,
            cursorBrush = SolidColor(BeamColors.Primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.weight(1f).padding(vertical = if (singleLine) 0.dp else 12.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = textStyle.copy(color = BeamColors.TextMuted), maxLines = 1)
                    }
                    inner()
                }
            },
        )
        if (trailingIcon != null && onTrailing != null) {
            Box(
                Modifier
                    .size(40.dp)
                    .pressable(CircleShape, Color.Transparent, BeamColors.PressedNeutral, onClick = onTrailing)
                    .semantics { contentDescription = trailingLabel ?: "" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(trailingIcon, null, tint = BeamColors.TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Read-only field that looks like an input and opens something when tapped. */
@Composable
fun SelectBox(text: String, placeholder: String, onClick: () -> Unit, icon: ImageVector = BeamIcons.ChevronDown) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .pressable(BeamShapes.Input, BeamColors.InputFill, BeamColors.PressedNeutral, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.ifEmpty { placeholder },
            style = BeamType.Body.copy(color = if (text.isEmpty()) BeamColors.TextMuted else BeamColors.Ink),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(icon, null, tint = BeamColors.TextMuted, modifier = Modifier.size(16.dp))
    }
}

/** Fixed list of options shown as a dropdown under a select box. */
@Composable
fun DropdownField(label: String, value: String, options: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    FieldLabel(label, modifier) {
        Box {
            SelectBox(value, "Choose…", onClick = { open = true })
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = BeamColors.Surface) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, style = BeamType.Body) },
                        onClick = {
                            onSelect(option)
                            open = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * Text field with suggestions underneath (customer names, issues, technicians). Typing filters
 * the list; choosing one calls [onPick]. Free text is allowed.
 */
@Composable
fun <T> SuggestField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<T>,
    suggestionText: (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    trailingIcon: ImageVector? = null,
    trailingLabel: String? = null,
    onTrailing: (() -> Unit)? = null,
    maxShown: Int = 4,
) {
    var focusedText by remember { mutableStateOf<String?>(null) }
    val query = value.trim()
    val matches = if (query.isEmpty() || focusedText == value) emptyList()
    else suggestions.filter {
        val text = suggestionText(it)
        text.contains(query, ignoreCase = true) && !text.equals(query, ignoreCase = true)
    }.take(maxShown)
    FieldLabel(label, modifier) {
        Column {
            InputBox(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder,
                keyboardType = keyboardType,
                trailingIcon = trailingIcon,
                trailingLabel = trailingLabel,
                onTrailing = onTrailing,
            )
            if (matches.isNotEmpty()) {
                Column(
                    Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .background(BeamColors.TileFill, BeamShapes.Input)
                        .padding(vertical = 4.dp),
                ) {
                    matches.forEach { item ->
                        Text(
                            suggestionText(item),
                            style = BeamType.Body,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    focusedText = suggestionText(item)
                                    onPick(item)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

private val dateLabel = DateTimeFormatter.ofPattern("d MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    FieldLabel(label, modifier) {
        SelectBox(value.format(dateLabel), "", onClick = { open = true }, icon = BeamIcons.Calendar)
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = value.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, value: LocalTime, onChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    FieldLabel(label, modifier) {
        SelectBox(value.format(DateTimeFormatter.ofPattern("HH:mm")), "", onClick = { open = true }, icon = BeamIcons.Clock)
    }
    if (open) {
        val state = rememberTimePickerState(initialHour = value.hour, initialMinute = value.minute, is24Hour = true)
        Dialog(onDismissRequest = { open = false }) {
            Column(
                Modifier
                    .background(BeamColors.Surface, BeamShapes.CardLarge)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Select time", style = BeamType.SectionTitle, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { open = false }) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        onChange(LocalTime.of(state.hour, state.minute))
                        open = false
                    }) { Text("OK") }
                }
            }
        }
    }
}

/** A checkbox with its label, for yes/no questions that belong together. */
@Composable
fun CheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, colors = CheckboxDefaults.colors(checkedColor = BeamColors.Primary))
        Text(label, style = BeamType.Body, modifier = Modifier.weight(1f))
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(BeamColors.InputFill, BeamShapes.Input)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = BeamType.Body, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = BeamColors.Primary, uncheckedBorderColor = Color.Transparent, uncheckedTrackColor = BeamColors.PressedNeutral),
        )
    }
}
