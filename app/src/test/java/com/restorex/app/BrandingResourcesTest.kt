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
        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(manifest.contains("android:roundIcon=\"@mipmap/ic_launcher_round\""))
        assertTrue(strings.contains("Restovue : Photo Editor"))
        assertTrue(strings.contains("NextGen TechEdge Solutions"))
        assertTrue(build.contains("applicationId = \"com.restorex.app\""))
        assertFalse(manifest.contains("android.permission.INTERNET"))
        assertTrue(File(root, "src/main/res/drawable-nodpi/restovue_logo.png").isFile)
        assertTrue(File(root, "src/main/res/mipmap-anydpi-v33/ic_launcher.xml").readText().contains("<monochrome"))
    }
}
