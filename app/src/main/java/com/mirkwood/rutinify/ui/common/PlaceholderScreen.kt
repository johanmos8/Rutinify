package com.mirkwood.rutinify.ui.common

import androidx.annotation.StringRes
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
import com.mirkwood.rutinify.ui.preview.ThemePreviews
import com.mirkwood.rutinify.ui.theme.RutinifyTheme

/** Temporary screen for destinations not implemented yet. */
@Composable
fun PlaceholderScreen(
    @StringRes titleRes: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { BackTopAppBar(titleRes = titleRes, onBack = onBack) },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.placeholder_body),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@ThemePreviews
@Composable
private fun PlaceholderScreenPreview() {
    RutinifyTheme { PlaceholderScreen(titleRes = R.string.review_title, onBack = {}) }
}
