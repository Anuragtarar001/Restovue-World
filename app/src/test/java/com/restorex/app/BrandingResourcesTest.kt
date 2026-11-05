package com.restorex.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandingResourcesTest {
    @Test
    fun manifestAndStringsUseRestovueBrandingWithoutChangingApplicationId() {
        val root = sequenceOf(File("."), File("app")).first { File(it, "src/main/AndroidManifest.xml").isFile }
        val manifest = File(root, "src/main/AndroidManifest.xml").readText()
        val strings = File(root, "src/main/res/values/strings.xml").readText()
        val build = File(root, "build.gradle.kts").readText()

        assertTrue(manifest.contains("@string/app_name"))
        assertTrue(strings.contains("Restovue : Photo Editor"))
        assertTrue(strings.contains("NextGen TechEdge Solutions"))
        assertTrue(build.contains("applicationId = \"com.restorex.app\""))
        assertFalse(manifest.contains("android.permission.INTERNET"))
    }
}
