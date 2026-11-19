package com.mirkwood.rutinify.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max

/** Widest the screens' content gets (D-100): a larger window adds side margins instead of stretching cards. */
val MaxContentWidth: Dp = 840.dp

/**
 * Side margin that keeps the content centred and at most [MaxContentWidth] wide, and never below [min] (the usual
 * 16 dp phone margin). Read once per screen: it only changes with the window size.
 */
@Composable
fun contentSideMargin(min: Dp = 16.dp): Dp {
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    return max(min, (windowWidth - MaxContentWidth) / 2)
}
