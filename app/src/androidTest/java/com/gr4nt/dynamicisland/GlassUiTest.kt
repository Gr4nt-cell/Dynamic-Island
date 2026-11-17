package com.gr4nt.dynamicisland

import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Render the real Compose views, including transitions, on an Android emulator. */
class GlassUiTest {
    @get:Rule val compose=createComposeRule()
    private val base=IslandItem("preview","preview","Messages","Ama","See you in a few minutes. I’m bringing the camera.")
    private fun capture(name: String) {
        val image=compose.onRoot().captureToImage().asAndroidBitmap()
        val folder=InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("screenshots")!!
        folder.mkdirs()
        File(folder,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    private fun scene(name: String, dark: Boolean, item: IslandItem=base, expanded: Boolean=true) {
        compose.mainClock.autoAdvance=false
        compose.setContent { IslandTheme(forceDark=dark) { Box(Modifier.fillMaxSize()) { GlassBackdrop(Modifier.matchParentSize());IslandOverlay(IslandState(item,expanded),IslandLayout()) } } }
        compose.mainClock.advanceTimeBy(1200)
        compose.waitForIdle()
        capture(name)
    }
    @Test fun messageLight() { scene("message-light",false);compose.onNodeWithText("Ama").assertIsDisplayed() }
    @Test fun messageDark() { scene("message-dark",true);compose.onNodeWithText("Ama").assertIsDisplayed() }
    @Test fun callLight() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val intent=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        scene("call-light",false,base.copy(call=true,incoming=true,actions=listOf(IslandAction("Decline",intent,null),IslandAction("Accept",intent,null))))
        compose.onNodeWithContentDescription("Accept call").assertIsDisplayed()
        compose.onNodeWithContentDescription("Decline call").assertIsDisplayed()
    }
    @Test fun codeLight() { scene("code-light",false,base.copy(code="482916"));compose.onNodeWithText("Copy code").assertIsDisplayed() }
    @Test fun mediaResizesWithoutMeasuringCardAtPillWidth() {
        val item=base.copy(app="Music",title="A little closer",text="Evening Sessions",media=MediaInfo(null,222000,84000,SystemClock.elapsedRealtime(),1f,true))
        val expanded=mutableStateOf(false)
        compose.mainClock.autoAdvance=false
        compose.setContent { IslandTheme(forceDark=false) { Box(Modifier.fillMaxSize()) { GlassBackdrop(Modifier.matchParentSize());IslandOverlay(IslandState(item,expanded.value),IslandLayout()) } } }
        compose.mainClock.advanceTimeBy(1000);capture("music-pill-light")
        compose.runOnIdle { expanded.value=true }
        repeat(30) { compose.mainClock.advanceTimeBy(16) }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("A little closer").assertIsDisplayed();capture("music-card-light")
        compose.runOnIdle { expanded.value=false };compose.mainClock.advanceTimeBy(1200)
        capture("music-pill-after-collapse")
    }
    @Test fun settingsLightAndPreview() {
        compose.setContent { IslandTheme(forceDark=false) { SettingsScreen() } }
        compose.onNodeWithText("Around your camera.").assertIsDisplayed();capture("settings-light")
        compose.onNodeWithText("Preview island").performClick()
        compose.onNodeWithText("Set up overlay").assertIsDisplayed();capture("preview-dialog-light")
    }
    @Test fun settingsDarkAndConsent() {
        compose.setContent { IslandTheme(forceDark=true) { SettingsScreen() } }
        compose.onNodeWithText("Around your camera.").assertIsDisplayed();capture("settings-dark")
        compose.onNodeWithText("Overlay access").performClick()
        compose.onNodeWithText("Agree and continue").assertIsDisplayed();capture("consent-dialog-dark")
    }
}
