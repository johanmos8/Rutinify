package com.mirkwood.rutinify.ui.common

import androidx.annotation.StringRes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.ui.preview.ThemePreviews
import com.mirkwood.rutinify.ui.theme.RutinifyTheme

/** Top app bar with a title and a back arrow ("Volver"). */
@Composable
fun BackTopAppBar(
    @StringRes titleRes: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackTopAppBar(title = stringResource(titleRes), onBack = onBack, modifier = modifier)
}

/** Same bar with a runtime title, such as an order name (one line, ellipsized). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopAppBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.action_back),
                )
            }
        },
        modifier = modifier,
    )
}

@ThemePreviews
@Composable
private fun BackTopAppBarPreview() {
    RutinifyTheme { BackTopAppBar(titleRes = R.string.new_routine_title, onBack = {}) }
}
