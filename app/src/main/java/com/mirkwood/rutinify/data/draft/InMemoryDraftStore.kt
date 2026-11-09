package com.mirkwood.rutinify.data.draft

import com.mirkwood.rutinify.domain.repository.DraftEntry
import com.mirkwood.rutinify.domain.repository.DraftStore
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Lives as long as the process (D-10). */
@Singleton
class InMemoryDraftStore @Inject constructor() : DraftStore {

    private val drafts = ConcurrentHashMap<String, DraftEntry>()

    override fun put(entry: DraftEntry) {
        drafts[entry.draft.id] = entry
    }

    override fun get(draftId: String): DraftEntry? = drafts[draftId]

    override fun remove(draftId: String) {
        drafts.remove(draftId)
    }
}
