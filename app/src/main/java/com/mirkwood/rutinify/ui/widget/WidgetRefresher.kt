package com.mirkwood.rutinify.ui.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.mirkwood.rutinify.di.ApplicationScope
import com.mirkwood.rutinify.domain.usecase.ObserveNextAlarmUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Redraws the widget whenever the next alarm changes (R-21.4, D-92). Every change goes through Room in this process
 * (firing, marking, snoozing, moving, rescheduling, pausing, deleting), so following Room covers all of them; a
 * receiver that wakes the process also starts this.
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val observeNextAlarm: ObserveNextAlarmUseCase,
) {
    fun start() {
        scope.launch {
            // The first value is what the widget already shows from its own render
            observeNextAlarm().drop(1).collect {
                try {
                    NextAlarmWidget().updateAll(context)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IllegalStateException) {
                    Log.w("Rutinify", "Widget update failed", e)
                }
            }
        }
    }
}
