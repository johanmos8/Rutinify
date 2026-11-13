package com.mirkwood.rutinify.ui.common

import androidx.annotation.StringRes
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.ui.preview.ThemePreviews
import com.mirkwood.rutinify.ui.theme.RutinifyTheme

/**
 * Material date picker dialog. Dates travel as UTC-midnight millis, as the picker expects; the
 * ViewModel converts them to a `LocalDate`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineDatePickerDialog(
    initialMillis: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes confirmLabel: Int = R.string.action_save,
) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        confirmButton = {
            TextButton(onClick = { pickerState.selectedDateMillis?.let(onConfirm) ?: onDismiss() }) {
                Text(stringResource(confirmLabel))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = pickerState)
    }
}

@ThemePreviews
@Composable
private fun RoutineDatePickerDialogPreview() {
    // 2026-11-13T00:00:00Z
    RutinifyTheme { RoutineDatePickerDialog(initialMillis = 1_794_528_000_000, onConfirm = {}, onDismiss = {}) }
}
