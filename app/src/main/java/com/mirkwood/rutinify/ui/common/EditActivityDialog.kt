package com.mirkwood.rutinify.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import com.mirkwood.rutinify.domain.importer.DraftItemRules
import java.time.LocalTime
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.ui.preview.ThemePreviews
import com.mirkwood.rutinify.ui.theme.RutinifyTheme

/** Time, title and category of one activity, with their errors: Review (R-4.2) and the plan detail (R-18.9). */
data class ActivityForm(
    val time: String,
    val title: String,
    val category: String,
    val timeError: UiText? = null,
    val titleError: UiText? = null,
    val categoryError: UiText? = null,
) {
    /** Where the time picker opens: the typed time, or noon when the field does not hold a valid one yet. */
    val pickerTime: LocalTime get() = DraftItemRules.parseTime(time) ?: LocalTime.NOON

    companion object {
        /** "06:45": how a picked time goes back into [time]. */
        fun timeText(time: LocalTime): String = time.format(DateFormats.TIME)
    }
}

/** "Editar actividad" dialog shared by Review and the plan detail. */
@Composable
fun EditActivityDialog(
    form: ActivityForm,
    onChange: (time: String, title: String, category: String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.review_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeField(form.time, form.timeError, onClick = { picking = true })
                FormField(form.title, { onChange(form.time, it, form.category) }, R.string.review_field_title, form.titleError)
                FormField(form.category, { onChange(form.time, form.title, it) }, R.string.review_field_category, form.categoryError)
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (picking) {
        RoutineTimePickerDialog(
            initial = form.pickerTime,
            onConfirm = {
                picking = false
                onChange(ActivityForm.timeText(it), form.title, form.category)
            },
            onDismiss = { picking = false },
        )
    }
}

/**
 * D-101: the time is picked, not typed, so it cannot be malformed; tapping anywhere on the field opens the picker.
 * The error line stays for rules the picker cannot know (a time already past, R-18.9).
 */
@Composable
private fun TimeField(value: String, error: UiText?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val openLabel = stringResource(R.string.time_picker_open)
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.review_field_time)) },
            trailingIcon = { Icon(painterResource(R.drawable.ic_schedule), contentDescription = null) },
            isError = error != null,
            supportingText = error?.let { { Text(it.asString()) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Above the read-only field, which would otherwise take the tap as focus
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick),
        )
    }
}

@Composable
private fun FormField(value: String, onValueChange: (String) -> Unit, label: Int, error: UiText?, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = error?.let { { Text(it.asString()) } },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

@ThemePreviews
@Composable
private fun EditActivityDialogPreview() {
    RutinifyTheme {
        EditActivityDialog(
            form = ActivityForm("14:00", "Amoxicilina", "medicación", timeError = UiText.Res(R.string.alarm_edit_past)),
            onChange = { _, _, _ -> },
            onSave = {},
            onDismiss = {},
        )
    }
}
