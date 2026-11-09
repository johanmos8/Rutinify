package com.mirkwood.rutinify.testutil

import com.mirkwood.rutinify.domain.system.Clipboard

class FakeClipboard(var text: String? = null) : Clipboard {
    override fun readText(): String? = text?.takeIf { it.isNotBlank() }
    override fun copyText(text: String) {
        this.text = text
    }
}
