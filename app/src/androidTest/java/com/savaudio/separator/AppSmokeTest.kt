package com.savaudio.separator

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @Test fun packageNameIsCorrect() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.savaudio.separator", context.packageName)
    }

    @Test fun appListExcludesItself() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(loadApps(context).none { it.packageName == context.packageName })
    }
}
