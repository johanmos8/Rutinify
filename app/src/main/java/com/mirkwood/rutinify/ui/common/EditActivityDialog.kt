package com.mirkwood.rutinify.ui.common

import androidx.compose.foundation.layout.Arrangement
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
)

/** "Editar actividad" dialog shared by Review and the plan detail. */
@Composable
fun EditActivityDialog(
    form: ActivityForm,
    onChange: (time: String, title: String, category: String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.review_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(form.time, { onChange(it, form.title, form.category) }, R.string.review_field_time, form.timeError)
                FormField(form.title, { onChange(form.time, it, form.category) }, R.string.review_field_title, form.titleError)
                FormField(form.category, { onChange(form.time, form.title, it) }, R.string.review_field_category, form.categoryError)
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
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
