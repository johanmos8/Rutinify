package com.mirkwood.rutinify.data.system

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import com.mirkwood.rutinify.data.LOG_TAG
import com.mirkwood.rutinify.domain.scheduler.AutoStartStatus

/**
 * Xiaomi's "Inicio automático" (D-102). There is no public API for it: MIUI and HyperOS keep it as their own app op
 * 10008, which [AppOpsManager]'s hidden `checkOpNoThrow(int, int, String)` can read on many versions. Anything
 * unexpected (another phone maker, a missing method, an unknown mode) gives [AutoStartStatus.UNKNOWN] or
 * [AutoStartStatus.NOT_APPLICABLE], never a guess, so the user is asked to check rather than told it is fine.
 */
internal object XiaomiAutoStart {

    /** MIUI's private op code for "Inicio automático" (seen as `MIUIOP(10008)` in `appops`). */
    private const val OP_AUTO_START = 10008

    private val BRANDS = setOf("xiaomi", "redmi", "poco")

    fun isXiaomi(manufacturer: String, brand: String): Boolean =
        manufacturer.lowercase() in BRANDS || brand.lowercase() in BRANDS

    fun status(context: Context): AutoStartStatus {
        if (!isXiaomi(Build.MANUFACTURER, Build.BRAND)) return AutoStartStatus.NOT_APPLICABLE
        return fromMode(readMode(context))
    }

    /** [mode] from the app op, or null if it could not be read. */
    fun fromMode(mode: Int?): AutoStartStatus = when (mode) {
        AppOpsManager.MODE_ALLOWED -> AutoStartStatus.ALLOWED
        AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> AutoStartStatus.DENIED
        else -> AutoStartStatus.UNKNOWN
    }

    // Reflection on a hidden method is the only way to read this MIUI setting; any failure means "unknown"
    @SuppressLint("DiscouragedPrivateApi")
    private fun readMode(context: Context): Int? = try {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val method = AppOpsManager::class.java.getMethod(
            "checkOpNoThrow",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java,
        )
        method.invoke(appOps, OP_AUTO_START, Process.myUid(), context.packageName) as? Int
    } catch (e: ReflectiveOperationException) {
        Log.w(LOG_TAG, "Could not read Xiaomi auto-start", e)
        null
    } catch (e: SecurityException) {
        Log.w(LOG_TAG, "Could not read Xiaomi auto-start", e)
        null
    }
}
