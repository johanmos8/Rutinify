package com.mirkwood.rutinify.data.system

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.mirkwood.rutinify.domain.system.AppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Version from the package manager, so no `BuildConfig` is needed. */
class AndroidAppInfo @Inject constructor(@param:ApplicationContext private val context: Context) : AppInfo {

    private val info by lazy { context.packageManager.getPackageInfo(context.packageName, 0) }

    override val versionName: String get() = info.versionName.orEmpty()

    override val versionCode: Long get() = PackageInfoCompat.getLongVersionCode(info)
}
