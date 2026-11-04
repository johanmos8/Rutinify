package com.mirkwood.rutinify.ui.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/** Multipreview en tema claro y oscuro (CLAUDE.md §7). */
@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class ThemePreviews
