package com.mirkwood.rutinify.domain.scheduler

/** Remembers, across launches, that the notification explanation of R-11.1 was already shown. */
interface PermissionPromptStore {

    fun notificationsExplained(): Boolean

    fun markNotificationsExplained()
}
