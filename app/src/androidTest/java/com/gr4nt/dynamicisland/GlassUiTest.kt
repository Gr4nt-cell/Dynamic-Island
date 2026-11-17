package com.gr4nt.dynamicisland

import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Render the real Compose views, including transitions, on an Android emulator. */
class GlassUiTest {
    @get:Rule val compose=createComposeRule()
    private val base=IslandItem("preview","preview","Messages","Ama","See you in a few minutes. I’m bringing the camera.")
    private fun capture(name: String) {
        val image=compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
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
        val shell=compose.onNodeWithTag("island-shell").fetchSemanticsNode().boundsInRoot
        val avatar=compose.onNodeWithTag("call-avatar",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val accept=compose.onNodeWithContentDescription("Accept call").fetchSemanticsNode().boundsInRoot
        assertEquals(shell.center.y,avatar.center.y,1f)
        assertEquals(shell.center.y,accept.center.y,1f)
    }
    @Test fun answeredCallShrinksToDurationPillAndReopensControls() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val intent=PendingIntent.getActivity(context,1,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val incoming=base.copy(key="call-transition",pkg="test.dialer",call=true,incoming=true,actions=listOf(IslandAction("Decline",intent,null),IslandAction("Accept",intent,null)))
        compose.mainClock.autoAdvance=false
        compose.setContent { val state by IslandController.state.collectAsState();IslandTheme(forceDark=false) { Box(Modifier.fillMaxSize()) { GlassBackdrop(Modifier.matchParentSize());IslandOverlay(state,IslandLayout()) } } }
        try {
            compose.runOnIdle { IslandController.post(incoming) }
            compose.mainClock.advanceTimeBy(1200)
            compose.onNodeWithContentDescription("Accept call").assertIsDisplayed()
            compose.runOnIdle { IslandController.post(incoming.copy(incoming=false,callStartedAtElapsed=SystemClock.elapsedRealtime()-30000,actions=listOf(IslandAction("Hang up",intent,null)))) }
            compose.mainClock.advanceTimeBy(1200)
            compose.onNodeWithContentDescription("Call duration",substring=true).assertIsDisplayed()
            compose.onNodeWithContentDescription("Dialer").assertIsDisplayed()
            capture("answered-call-pill-light")
            compose.onNodeWithContentDescription("Call duration",substring=true).performClick()
            compose.mainClock.advanceTimeBy(1200)
            compose.onNodeWithContentDescription("Hang up").assertIsDisplayed()
        } finally { compose.runOnIdle { IslandController.removed("call-transition") } }
    }
    @Test fun messageDuringAnsweredCallRestoresSameRunningTimer() {
        val call=base.copy(key="handoff-call",pkg="test.handoff",call=true,incoming=false,callStartedAtElapsed=SystemClock.elapsedRealtime()-30000)
        val message=base.copy(key="handoff-message",pkg="test.messages",title="Message during call")
        compose.setContent { val state by IslandController.state.collectAsState();IslandTheme(forceDark=false) { Box(Modifier.fillMaxSize()) { GlassBackdrop(Modifier.matchParentSize());IslandOverlay(state,IslandLayout()) } } }
        try {
            compose.runOnIdle { IslandController.post(call);IslandController.post(message) }
            compose.onNodeWithText("Message during call").assertIsDisplayed()
            compose.runOnIdle {
                IslandController.post(call.copy(text="Call refreshed",callStartedAtElapsed=SystemClock.elapsedRealtime()))
                assertEquals(message.key,IslandController.state.value.item?.key)
            }
            capture("message-during-call")
            compose.runOnIdle {
                IslandController.collapse()
                assertEquals(call.key,IslandController.state.value.item?.key)
                assertEquals(call.callStartedAtElapsed,IslandController.state.value.item?.callStartedAtElapsed)
                assertFalse(IslandController.state.value.expanded)
            }
            compose.onNodeWithContentDescription("Call duration",substring=true).assertIsDisplayed()
        } finally { compose.runOnIdle { IslandController.removed(message.key);IslandController.removed(call.key) } }
    }
    @Test fun ongoingCallRefreshCannotStealReplyFocus() {
        val call=base.copy(key="reply-call",pkg="test.replycall",call=true,incoming=false,callStartedAtElapsed=SystemClock.elapsedRealtime()-5000)
        val message=base.copy(key="reply-message",pkg="test.reply",title="Reply remains focused")
        compose.setContent { Box(Modifier.fillMaxSize()) }
        val previousFocus=IslandController.setFocusable
        val changes=mutableListOf<Boolean>()
        try {
            compose.runOnIdle {
                IslandController.setFocusable={changes.add(it)}
                IslandController.post(call);IslandController.post(message);IslandController.reply()
                val count=changes.size
                IslandController.post(call.copy(text="Updated"))
                assertEquals(count,changes.size)
                assertTrue(changes.last())
                assertTrue(IslandController.state.value.replying)
                assertEquals(message.key,IslandController.state.value.item?.key)
            }
        } finally { compose.runOnIdle { IslandController.cancelReply();IslandController.removed(message.key);IslandController.removed(call.key);IslandController.setFocusable=previousFocus } }
    }
    @Test fun replacingCallNotificationKeysPreservesDurationAndCollapse() {
        val incoming=base.copy(key="replace-incoming",pkg="test.replace",call=true,incoming=true)
        val ongoing=incoming.copy(key="replace-ongoing",incoming=false,callStartedAtElapsed=SystemClock.elapsedRealtime()-10000)
        val refreshed=ongoing.copy(key="replace-refreshed",callStartedAtElapsed=null)
        compose.setContent { Box(Modifier.fillMaxSize()) }
        try {
            compose.runOnIdle {
                IslandController.post(incoming)
                val answerObserved=SystemClock.elapsedRealtime()
                IslandController.post(ongoing)
                val connectedStart=IslandController.state.value.item?.callStartedAtElapsed
                assertNotNull(connectedStart)
                assertTrue(connectedStart!!>=answerObserved)
                assertEquals(ongoing.key,IslandController.state.value.item?.key)
                assertFalse(IslandController.state.value.expanded)
                IslandController.post(refreshed)
                assertEquals(refreshed.key,IslandController.state.value.item?.key)
                assertEquals(connectedStart,IslandController.state.value.item?.callStartedAtElapsed)
                IslandController.removed(incoming.key);IslandController.removed(ongoing.key)
                assertEquals(refreshed.key,IslandController.state.value.item?.key)
            }
        } finally { compose.runOnIdle { listOf(incoming.key,ongoing.key,refreshed.key).forEach(IslandController::removed) } }
    }
    @Test fun incomingCallKeepsPriorityOverOldCallAndMediaUpdates() {
        val ongoing=base.copy(key="priority-old-call",pkg="test.priority",title="Alice",call=true,incoming=false)
        val incoming=base.copy(key="priority-incoming",pkg="test.priority",title="Bob",call=true,incoming=true,
            media=MediaInfo(null,0,0,SystemClock.elapsedRealtime(),1f,true))
        val message=base.copy(key="priority-message",pkg="test.prioritymessage")
        compose.setContent { Box(Modifier.fillMaxSize()) }
        try {
            compose.runOnIdle {
                IslandController.post(ongoing);IslandController.post(incoming)
                IslandController.post(ongoing.copy(text="Ongoing refresh"));IslandController.post(message)
                assertEquals(incoming.key,IslandController.state.value.item?.key)
                assertNull(IslandController.state.value.item?.media)
                assertTrue(IslandController.state.value.expanded)
                IslandController.removed(incoming.key)
                assertEquals(ongoing.key,IslandController.state.value.item?.key)
                assertFalse(IslandController.state.value.expanded)
            }
        } finally { compose.runOnIdle { listOf(message.key,incoming.key,ongoing.key).forEach(IslandController::removed) } }
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
        compose.onNodeWithText("A little closer").assertIsDisplayed()
        val shell=compose.onNodeWithTag("island-shell").fetchSemanticsNode().boundsInRoot
        val play=compose.onNodeWithContentDescription("Pause").fetchSemanticsNode().boundsInRoot
        assertEquals(shell.center.x,play.center.x,1f)
        capture("music-card-light")
        compose.runOnIdle { expanded.value=false };compose.mainClock.advanceTimeBy(1200)
        capture("music-pill-after-collapse")
    }
    @Test fun largeCardsUseIdenticalDimensionsAndOpaqueBlackInLightMode() {
        scene("large-notification-black",false,base.copy(text="A long message with enough detail to need the large card. ".repeat(3)))
        compose.onNodeWithTag("island-shell").assertWidthIsEqualTo(366.dp).assertHeightIsEqualTo(191.52.dp)
        val bitmap=compose.onNodeWithTag("island-shell").captureToImage().asAndroidBitmap()
        val pixel=bitmap.getPixel(bitmap.width/2,bitmap.height-12)
        assertEquals(android.graphics.Color.BLACK,pixel)
    }
    @Test fun chargingAndLowBatteryUseTheSameSmallShell() {
        val item=mutableStateOf(base.copy(title="Charging",text="82%",event=IslandEvent.CHARGING))
        compose.mainClock.autoAdvance=false
        compose.setContent { IslandTheme(forceDark=false) { Box(Modifier.fillMaxSize()) { GlassBackdrop(Modifier.matchParentSize());IslandOverlay(IslandState(item.value),IslandLayout()) } } }
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithTag("island-shell").assertWidthIsEqualTo(250.dp).assertHeightIsEqualTo(48.dp)
        capture("charging-small-black")
        compose.runOnIdle { item.value=base.copy(key="low",title="Low Battery",text="20%",event=IslandEvent.LOW_BATTERY) }
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithTag("island-shell").assertWidthIsEqualTo(250.dp).assertHeightIsEqualTo(48.dp)
        compose.onNodeWithText("Low Battery").assertIsDisplayed()
        capture("battery-small-black")
    }
    @Test fun outgoingCallDoesNotCountUntilAnswerAndKeepsItsTimerOnRefresh() {
        val outgoing=base.copy(key="outgoing-wait",pkg="test.outgoing",call=true,text="Calling…")
        compose.mainClock.autoAdvance=false
        compose.setContent { val state by IslandController.state.collectAsState();IslandTheme { IslandOverlay(state,IslandLayout()) } }
        try {
            compose.runOnIdle { IslandController.post(outgoing) }
            compose.mainClock.advanceTimeBy(5000)
            compose.onNodeWithContentDescription("Calling",useUnmergedTree=true).assertIsDisplayed()
            compose.onNodeWithContentDescription("Call duration",substring=true).assertDoesNotExist()
            compose.runOnIdle { assertNull(IslandController.state.value.item?.callStartedAtElapsed) }
            capture("outgoing-waiting-no-timer")
            var answerStart: Long?=null
            compose.runOnIdle {
                val beforeAnswer=SystemClock.elapsedRealtime()
                // A dialer reusing its original dialing timestamp must not add ringing time.
                IslandController.post(outgoing.copy(text="Connected",callConnected=true,callStartedAtElapsed=beforeAnswer-20000))
                answerStart=IslandController.state.value.item?.callStartedAtElapsed
                assertTrue(answerStart!!>=beforeAnswer)
                IslandController.post(outgoing.copy(text="Refreshed",callConnected=true))
                assertEquals(answerStart,IslandController.state.value.item?.callStartedAtElapsed)
            }
            compose.mainClock.advanceTimeBy(1200)
            compose.onNodeWithContentDescription("Call duration",substring=true).assertIsDisplayed()
            capture("answered-colour-call-pill")
        } finally { compose.runOnIdle { IslandController.removed(outgoing.key) } }
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
