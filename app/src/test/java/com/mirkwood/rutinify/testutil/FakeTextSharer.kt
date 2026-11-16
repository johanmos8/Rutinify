package com.mirkwood.rutinify.testutil

import com.mirkwood.rutinify.domain.system.TextSharer

class FakeTextSharer(var available: Boolean = true) : TextSharer {
    override fun canShareText(): Boolean = available
}
