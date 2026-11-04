package com.mirkwood.rutinify.ui.newroutine

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.ui.common.BackTopAppBar
import com.mirkwood.rutinify.ui.preview.ThemePreviews
import com.mirkwood.rutinify.ui.theme.RutinifyTheme

/** Temporary New routine screen. The text field and validation arrive in T-21. */
@Composable
fun NewRoutineScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { BackTopAppBar(titleRes = R.string.new_routine_title, onBack = onBack) },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.new_routine_placeholder),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@ThemePreviews
@Composable
private fun NewRoutineScreenPreview() {
    RutinifyTheme { NewRoutineScreen(onBack = {}) }
}
