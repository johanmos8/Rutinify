package com.mirkwood.rutinify.data.share

import com.mirkwood.rutinify.domain.repository.SharedTextInbox
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/** Lives as long as the process; New routine copies the text into its saved state as soon as it takes it. */
@Singleton
class InMemorySharedTextInbox @Inject constructor() : SharedTextInbox {

    private val pending = AtomicReference<String?>(null)

    override fun put(text: String?) {
        pending.set(text)
    }

    override fun take(): String? = pending.getAndSet(null)
}
