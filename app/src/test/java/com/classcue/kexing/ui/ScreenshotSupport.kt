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

/** Saves the most recently shown dialog window (Compose dialogs live outside the activity window). */
internal fun AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>.saveDialogScreenshot(name: String) {
    // Dialogs never report idle under Robolectric (window focus loop), so drive the clock by hand.
    mainClock.autoAdvance = false
    repeat(20) {
        mainClock.advanceTimeByFrame()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }
    val dir = File(System.getProperty("classcue.screenshotDir") ?: "build/screenshots").apply { mkdirs() }
    val dialog = requireNotNull(org.robolectric.shadows.ShadowDialog.getLatestDialog()) { "no dialog is showing" }
    val view = requireNotNull(dialog.window).decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
        Canvas(it).apply { drawColor(android.graphics.Color.rgb(120, 120, 120)) }
        view.draw(Canvas(it))
    }
    File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
