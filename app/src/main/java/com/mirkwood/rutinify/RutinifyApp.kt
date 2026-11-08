package com.mirkwood.rutinify

import android.app.Application
import android.util.Log
import com.mirkwood.rutinify.data.LOG_TAG
import com.mirkwood.rutinify.di.ApplicationScope
import com.mirkwood.rutinify.domain.usecase.RescheduleAllUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RutinifyApp : Application() {

    @Inject lateinit var rescheduleAll: RescheduleAllUseCase

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Reconcile on every start: "Force stop" drops all alarms without any broadcast (design §7.2, R-12.3)
        scope.launch {
            try {
                rescheduleAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Reschedule on start failed", e)
            }
        }
    }
}
