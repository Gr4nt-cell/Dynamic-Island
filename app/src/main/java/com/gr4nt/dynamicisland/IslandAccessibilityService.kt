package com.gr4nt.dynamicisland

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.*
import androidx.lifecycle.*
import androidx.savedstate.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class OverlayOwners : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry=LifecycleRegistry(this)
    private val saved=SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get()=registry
    override val savedStateRegistry: SavedStateRegistry get()=saved.savedStateRegistry
    override val viewModelStore=ViewModelStore()
    fun start() { saved.performAttach();saved.performRestore(null);registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);registry.handleLifecycleEvent(Lifecycle.Event.ON_START);registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
    fun destroy() { registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE);registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP);registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);viewModelStore.clear() }
}
class IslandAccessibilityService : AccessibilityService() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate+CoroutineExceptionHandler { _,e -> android.util.Log.e("DynamicIsland","coroutine failed",e) })
    private lateinit var wm: WindowManager
    private var view: ComposeView?=null
    private var owners: OverlayOwners?=null
    private var params: WindowManager.LayoutParams?=null
    private var focused=false
    override fun onServiceConnected() {
        wm=getSystemService(WindowManager::class.java)
        IslandController.connected.value=true
        StatusEvents.start(this)
        IslandController.setFocusable={ focus -> focused=focus;updateWindow() }
        scope.launch { IslandController.settings.flow.collect { runCatching { rebuild() } } }
        scope.launch { IslandController.state.map { Triple(it.item?.key, it.expanded, it.item != null) }.distinctUntilChanged().collect { runCatching { updateWindow() } } }
    }
    private fun makeParams(): WindowManager.LayoutParams {
        val l=IslandController.settings.flow.value
        val density=resources.displayMetrics.density
        val metrics=wm.maximumWindowMetrics
        val rect=metrics.windowInsets.displayCutout?.boundingRectTop
        val cameraTop=rect?.let { NotificationRules.cameraTop(it.top,it.bottom,it.width()) } ?: (10*density).toInt()
        return WindowManager.LayoutParams(
            (metrics.bounds.width()-(l.left+l.right)*density).toInt().coerceAtLeast((120*density).toInt()),
            WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                if(focused) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply {
            gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x=((l.left-l.right)*density/2).toInt()
            y=if((IslandController.state.value.item?.media != null || IslandController.state.value.item?.status != null || (IslandController.state.value.item?.call==true && IslandController.state.value.item?.incoming==false)) && !IslandController.state.value.expanded) (10*density).toInt() else (cameraTop-l.gap*density).toInt()
            if(IslandController.state.value.item == null) flags=flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            title="Dynamic Island"
        }
    }
    private fun rebuild() {
        removeView()
        val owner=OverlayOwners().also { it.start() };owners=owner
        val v=ComposeView(this)
        v.setViewTreeLifecycleOwner(owner);v.setViewTreeSavedStateRegistryOwner(owner);v.setViewTreeViewModelStoreOwner(owner)
        v.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        v.setContent {
            val s by IslandController.state.collectAsState()
            val l by IslandController.settings.flow.collectAsState()
            IslandTheme { IslandOverlay(s,l) }
        }
        params=makeParams();view=v
        runCatching { wm.addView(v,params) }.onFailure { removeView();IslandController.toast("Unable to attach the island overlay") }
        IslandController.schedule()
    }
    private fun updateWindow() {
        val v=view ?: return
        params=makeParams();runCatching { wm.updateViewLayout(v,params) }
        if(focused) v.requestFocus() else (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(v.windowToken,0)
    }
    private fun removeView() {
        view?.let { if(it.isAttachedToWindow) wm.removeViewImmediate(it);it.disposeComposition() };view=null
        owners?.destroy();owners=null
    }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig);runCatching { rebuild() } }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { IslandController.cancelReply() }
    override fun onUnbind(intent: android.content.Intent?): Boolean { cleanup();return super.onUnbind(intent) }
    private fun cleanup() {
        IslandController.connected.value=false;IslandController.setFocusable={};StatusEvents.stop(this);removeView()
        // Restore native heads-up when disabled, when possible. Global changes otherwise survive app death.
        IslandController.scope.launch { if(IslandController.root.hidden) IslandController.root.nativeHeadsUp(false) }
    }
    override fun onDestroy() { cleanup();scope.cancel();super.onDestroy() }
}
