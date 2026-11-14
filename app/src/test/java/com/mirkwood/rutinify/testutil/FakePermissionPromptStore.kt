package com.mirkwood.rutinify.testutil

import com.mirkwood.rutinify.domain.scheduler.PermissionPromptStore

/** In-memory [PermissionPromptStore]. */
class FakePermissionPromptStore(var explained: Boolean = false) : PermissionPromptStore {

    override fun notificationsExplained(): Boolean = explained

    override fun markNotificationsExplained() {
        explained = true
    }
}
