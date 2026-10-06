package com.andersonmoreira.papelzinho

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityConfigTest {
    @Test
    fun includesLayoutContainersRequiredByCapturedPreviewSignatures() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service =
            context.getSystemService(AccessibilityManager::class.java).installedAccessibilityServiceList.single {
                it.resolveInfo.serviceInfo.packageName == context.packageName &&
                    it.resolveInfo.serviceInfo.name == ViewOnceAccessibilityService::class.java.name
            }
        assertTrue(service.flags and AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS != 0)
        assertTrue(service.flags and AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS != 0)
    }
}
