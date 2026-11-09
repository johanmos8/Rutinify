package com.mirkwood.rutinify.data.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.domain.system.Clipboard
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** [Clipboard] on top of `ClipboardManager`. Only read on a user tap, as Android 10+ requires. */
class AndroidClipboard @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : Clipboard {

    private val manager: ClipboardManager
        get() = context.getSystemService(ClipboardManager::class.java)

    override fun readText(): String? {
        val clip = manager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
    }

    override fun copyText(text: String) {
        manager.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.clipboard_label_template), text))
    }
}
