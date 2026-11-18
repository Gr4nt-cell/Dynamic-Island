package com.gr4nt.dynamicisland

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CallActionTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun action(code: Int)=PendingIntent.getBroadcast(context,code,
        Intent("${context.packageName}.TEST_ANSWER").setPackage(context.packageName),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    @Test fun canonicalAnswerOverridesLabelGuesses() {
        val actual=action(201);val other=action(202)
        val notification=Notification.Builder(context,"test").build()
        notification.extras.putParcelable("android.answerIntent",actual)
        val parsed=CallActions.from(notification,listOf(IslandAction("Answer",other,null)))
        assertEquals(actual,parsed.single { it.kind=="accept" }.intent)
    }
    @Test fun answerReachesDialerReceiverAndWaitsForConnectionConfirmation() {
        val received=CountDownLatch(1)
        val receiver=object: BroadcastReceiver() { override fun onReceive(c: Context,i: Intent) { received.countDown() } }
        ContextCompat.registerReceiver(context,receiver,IntentFilter("${context.packageName}.TEST_ANSWER"),ContextCompat.RECEIVER_NOT_EXPORTED)
        val intent=action(203)
        val item=IslandItem("answer-integration","test.dialer","Phone","Caller","Incoming call",call=true,incoming=true,
            actions=listOf(IslandAction("Answer",intent,null,explicitKind="accept")))
        try {
            instrumentation.runOnMainSync { IslandController.post(item);IslandController.action(item.actions.single()) }
            assertTrue("Answer must reach the original dialer action",received.await(3,TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                assertTrue(IslandController.state.value.item!!.incoming)
                assertTrue(IslandController.state.value.expanded)
                assertNull(IslandController.state.value.item!!.callStartedAtElapsed)
            }
        } finally {
            context.unregisterReceiver(receiver)
            instrumentation.runOnMainSync { IslandController.removed(item.key) }
        }
    }
    @Test fun canceledAnswerIsReportedAsFailure() {
        val intent=action(204);intent.cancel()
        assertFalse(NotificationActionSender.send(context,intent))
    }
}
