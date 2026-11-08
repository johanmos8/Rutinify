package com.mirkwood.rutinify.ui.common

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources

/**
 * Text to show, described by resource id and arguments so it can be built outside composables (VMs,
 * mappers) and tested. Arguments may themselves be [UiText]; they are resolved first.
 */
sealed interface UiText {

    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(@param:PluralsRes val id: Int, val quantity: Int, val args: List<Any> = emptyList()) : UiText

    fun resolve(resources: Resources): String = when (this) {
        is Res -> resources.getString(id, *resolveArgs(args, resources))
        is Plural -> resources.getQuantityString(id, quantity, *resolveArgs(args, resources))
    }

    private fun resolveArgs(args: List<Any>, resources: Resources): Array<Any> =
        args.map { if (it is UiText) it.resolve(resources) else it }.toTypedArray()
}

@Composable
@ReadOnlyComposable
fun UiText.asString(): String = resolve(LocalResources.current)
