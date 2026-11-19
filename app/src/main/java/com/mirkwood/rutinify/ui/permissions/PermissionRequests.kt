package com.mirkwood.rutinify.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.annotation.RequiresApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/*
 * Platform glue for the permission buttons (design §9, D-51). These need an Activity (permission
 * dialog, rationale state) or start system screens, so they live in the UI layer; the decision of
 * *whether* a permission is missing stays in the ViewModels through PermissionChecker.
 */

private const val TAG = "Rutinify"

/**
 * Returns the action for the notifications "Corregir" button. On Android 13+ it asks for the runtime
 * permission first; if that is denied for good (no dialog, no rationale) it opens the app's
 * notification settings instead (R-11.8). Below 13, or with the permission granted but notifications
 * switched off, it goes straight to settings. [onDone] runs after the dialog closes.
 */
@Composable
fun rememberNotificationPermissionFixer(onDone: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && activity != null && isPermanentlyDenied(activity)) openNotificationSettings(context)
        onDone()
    }
    return remember(context, launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasRuntimePermission(context)) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openNotificationSettings(context)
            }
        }
    }
}

/** "Corregir" for exact alarms: the system "Alarms & reminders" page for this app (R-11.2, R-11.4). */
fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return // granted by default below Android 12
    start(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
}

/** "Corregir" for full-screen alarms: the system page of this app (R-15.11, D-68); granted at install below 14. */
fun openFullScreenIntentSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    start(context, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri()))
}

/**
 * Xiaomi's "Inicio automático" (R-11.10, D-102): MIUI and HyperOS list it under the public action [ACTION_MIUI_AUTO_START]
 * (checked on HyperOS 3, where the app's own page does not show that switch). Phones without it get the app's system
 * page instead, through [start]'s fallback.
 */
fun openAutoStartSettings(context: Context) {
    try {
        start(context, Intent(ACTION_MIUI_AUTO_START))
    } catch (e: SecurityException) {
        Log.w(TAG, "Auto-start screen not allowed", e)
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private const val ACTION_MIUI_AUTO_START = "miui.intent.action.OP_AUTO_START"

/** App notification settings (R-11.8). */
fun openNotificationSettings(context: Context) {
    start(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun hasRuntimePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** After a denial the rationale flag is true; once the user said "don't ask again" it is false. */
private fun isPermanentlyDenied(activity: Activity): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)

private fun start(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // Some OEM builds lack the specific page; fall back to the app's general settings
        Log.w(TAG, "No activity for ${intent.action}", e)
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
