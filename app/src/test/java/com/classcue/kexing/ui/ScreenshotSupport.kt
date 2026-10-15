package com.classcue.kexing.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import java.io.File

/** Saves the activity window as a PNG for visual review (run with -Pscreenshots). */
internal fun AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>.saveScreenshot(name: String) {
    waitForIdle()
    val dir = File(System.getProperty("classcue.screenshotDir") ?: "build/screenshots").apply { mkdirs() }
    var bitmap: Bitmap? = null
    activityRule.scenario.onActivity { activity ->
        val view = activity.window.decorView
        bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }
    File(dir, "$name.png").outputStream().use { requireNotNull(bitmap).compress(Bitmap.CompressFormat.PNG, 100, it) }
}
