package com.mirkwood.rutinify.data.repository

import android.content.Context
import androidx.core.content.edit
import com.mirkwood.rutinify.data.local.DiagnosticCounterDao
import com.mirkwood.rutinify.domain.diagnostics.DiagnosticEvent
import com.mirkwood.rutinify.domain.diagnostics.DiagnosticsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Counters in Room, the switch in `SharedPreferences`, off by default (D-72). Nothing ever leaves the phone (R-17.4). */
@Singleton
class DiagnosticsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: DiagnosticCounterDao,
) : DiagnosticsRepository {

    private val prefs by lazy { context.getSharedPreferences(FILE, Context.MODE_PRIVATE) }

    override fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    override suspend fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
        if (!enabled) dao.clear()
    }

    override suspend fun record(event: DiagnosticEvent) {
        if (isEnabled()) dao.increment(event.key)
    }

    override suspend fun counters(): Map<String, Int> = dao.getAll().associate { it.key to it.count }

    override suspend fun clear() = dao.clear()

    private companion object {
        const val FILE = "diagnostics"
        const val KEY_ENABLED = "enabled"
    }
}
