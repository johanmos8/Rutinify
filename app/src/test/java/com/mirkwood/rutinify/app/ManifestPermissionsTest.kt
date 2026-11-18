package com.mirkwood.rutinify.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Permissions the merged manifest asks for (R-13.6, D-09, D-69). */
@RunWith(AndroidJUnit4::class)
class ManifestPermissionsTest {

    private val requested: List<String> = ApplicationProvider.getApplicationContext<Context>().let { context ->
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toList()
    }

    @Test
    fun `no pide USE_EXACT_ALARM en v1_2`() { // D-69 option C, T-48
        assertThat(requested).contains("android.permission.SCHEDULE_EXACT_ALARM")
        assertThat(requested).doesNotContain("android.permission.USE_EXACT_ALARM")
    }

    @Test
    fun `no pide acceso a internet`() { // R-13.6
        assertThat(requested).doesNotContain("android.permission.INTERNET")
    }
}
