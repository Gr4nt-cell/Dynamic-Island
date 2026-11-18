package com.gr4nt.dynamicisland

import android.content.*
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState);setContent { IslandTheme { SettingsScreen() } } }
}
@Composable internal fun SettingsScreen() {
    val c=androidx.compose.ui.platform.LocalContext.current
    var contactsGranted by remember { mutableStateOf(c.checkSelfPermission(android.Manifest.permission.READ_CONTACTS)==android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val contactsPermission=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        contactsGranted=granted
        if(granted) IslandNotificationService.active?.refreshContacts()
    }
    val palette=LocalGlassPalette.current
    val view=LocalView.current
    DisposableEffect(palette.dark) {
        val window=(c as? android.app.Activity)?.window
        window?.let { WindowCompat.getInsetsController(it,view).apply { isAppearanceLightStatusBars=!palette.dark;isAppearanceLightNavigationBars=!palette.dark } }
        onDispose { }
    }
    val settings=IslandController.settings
    val layout by settings.flow.collectAsState()
    val connected by IslandController.connected.collectAsState()
    val listener by IslandController.listenerConnected.collectAsState()
    val scope=rememberCoroutineScope()
    var consent by remember { mutableStateOf<String?>(null) }
    var apps by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var localPreview by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(IslandController.root.hidden) }
    var rootBusy by remember { mutableStateOf(false) }
    fun open(action: String) { runCatching { c.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { IslandController.toast("Open this permission in system settings") } }
    Box(Modifier.fillMaxSize()) {
    GlassBackdrop(Modifier.matchParentSize())
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal=22.dp,vertical=24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        IslandText("DYNAMIC ISLAND",11,SettingsAccent,FontWeight.Bold,lines=1)
        IslandText("Around your camera.",30,weight=FontWeight.Bold,lines=2,modifier=Modifier.iosReveal())
        IslandText("Your notifications. One quiet place.",14,Secondary)
        Box(Modifier.fillMaxWidth().height(122.dp).iosReveal(50),contentAlignment=Alignment.TopCenter) {
            IslandOverlay(IslandState(IslandItem("settings-preview","preview","Messages","Ama","See you in a few minutes.")),IslandLayout(size=90f))
        }
        GlassButton("Preview island",Modifier.fillMaxWidth().iosReveal(80),Icons.Rounded.PlayArrow) {
            if(connected) IslandController.preview() else localPreview=true
        }
        SectionTitle("GET CONNECTED")
        SettingsGroup {
            AccessRow("Overlay access",if(connected)"Connected" else "Set up",Icons.Rounded.Layers) { consent="accessibility" }
            HorizontalDivider(color=palette.track)
            AccessRow("Notification access",if(listener)"Connected" else "Set up",Icons.Rounded.Notifications) { consent="notifications" }
            HorizontalDivider(color=palette.track)
            AccessRow("Contact photos",if(contactsGranted)"Connected" else "Allow contacts",Icons.Rounded.AccountCircle) {
                if(!contactsGranted) contactsPermission.launch(android.Manifest.permission.READ_CONTACTS)
                else IslandNotificationService.active?.refreshContacts()
            }
            IslandText("Allow Contacts to show saved caller photos even when the dialer omits them. Photos stay on your device.",12,Secondary)
            IslandText("All notification content is processed on your device. No internet permission is requested.",12,Secondary)
        }
        SectionTitle("LAYOUT")
        SettingsGroup {
            SettingSlider("Left edge distance","left",layout.left,0f..40f,"dp")
            SettingSlider("Right edge distance","right",layout.right,0f..40f,"dp")
            SettingSlider("Gap above camera","gap",layout.gap,-12f..20f,"dp")
            SettingSlider("Island size","size",layout.size,75f..125f,"%")
        }
        SectionTitle("BEHAVIOUR")
        SettingsGroup {
            IslandText("Messages resize to their text. Tap a short message to reveal Reply, Read and other available actions.",12,Secondary,lines=3)
            SettingSlider("Show time","seconds",layout.seconds,3f..20f,"seconds")
            SettingSwitch("Countdown line",layout.countdown) { scope.launch { settings.toggle("countdown",it) } }
            SettingSwitch("Handle all apps",layout.allApps) { scope.launch { settings.toggle("allApps",it) } }
            if(!layout.allApps) GlassButton("Choose apps · ${layout.allowList.size} selected",onClick={apps=true})
        }
        SettingsGroup {
            AccessRow("Advanced layout",if(advanced)"Less" else "More",Icons.Rounded.Tune) { advanced=!advanced }
            AnimatedVisibility(advanced,enter=fadeIn(IslandMotion.fadeIn())+expandVertically(IslandMotion.settle()),exit=fadeOut(IslandMotion.fadeOut())+shrinkVertically(IslandMotion.exit())) {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                IslandText("Four consistent shapes. Size scales every preset together; app content never stretches a card.",13,Secondary,lines=3)
                IslandText("Large 366 × 191.52 · Medium 366 × 96\nSmall 250 × 48 · Pill 148 × 37",13,Secondary,lines=2)
                listOf("Large media" to "media","Large notification" to "large","Small charging" to "charging","Small low battery" to "battery","Media pill" to "music-pill").forEach { (label,kind) ->
                    GlassButton(label,Modifier.fillMaxWidth(),onClick={ if(connected)IslandController.preview(kind) else localPreview=true })
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    GlassButton("Test call",Modifier.weight(1f),onClick={ if(connected)IslandController.preview("call") else localPreview=true })
                    GlassButton("Test code",Modifier.weight(1f),onClick={ if(connected)IslandController.preview("code") else localPreview=true })
                }
                IslandText("LIVE ACTIVITIES",11,SettingsAccent,FontWeight.Bold,lines=1)
                listOf("Two simultaneous islands" to "simultaneous","Navigation" to "navigation","Timer" to "timer","Stopwatch" to "stopwatch","Recording" to "recording","Unlock confirmation" to "unlock","Ring mode" to "ring","Vibrate mode" to "vibrate","Headphones" to "headphones","Hotspot" to "hotspot","Do Not Disturb" to "dnd").forEach { (label,kind) ->
                    GlassButton(label,Modifier.fillMaxWidth(),onClick={ if(connected)IslandController.preview(kind) else localPreview=true })
                }
                IslandText("Tap the second circle to switch apps. Unlock confirmation follows Android’s unlock state, including face, fingerprint and PIN. Tap a low-battery island to open Battery Saver.",12,Secondary,lines=5)
                GlassButton("Preview answered-call pill",Modifier.fillMaxWidth(),Icons.Rounded.Call,onClick={ if(connected)IslandController.preview("ongoing") else localPreview=true })
            }
        }
        }
        SectionTitle("OPTIONAL ROOT EXTRAS")
        SettingsGroup {
            SettingSwitch("Hide native pop-ups",hidden,enabled=!rootBusy) { hide ->
                rootBusy=true
                scope.launch {
                    val ok=IslandController.root.nativeHeadsUp(hide)
                    hidden=IslandController.root.hidden;rootBusy=false
                    if(!ok)IslandController.toast("Root permission unavailable. Native setting was not changed.")
                }
            }
            IslandText("Requires Magisk or KernelSU approval. The previous value is stored before changes and restored when switched off or the overlay service is disabled. Restore it before uninstalling.",12,Secondary)
            SettingSwitch("Recover hidden code text",layout.rootRecovery) { enabled ->
                if(enabled) consent="rootRecovery" else scope.launch { settings.toggle("rootRecovery",false) }
            }
            GlassButton("Enable service with root",enabled=!rootBusy,onClick={consent="rootEnable"})
        }
        SettingsGroup {
            IslandText("Using HyperOS?",16,weight=FontWeight.SemiBold)
            IslandText("Disable the system music island for each music app in Settings → Notifications → Hyper Island → App permissions. This app cannot remove it.",13,Secondary,lines=5)
            IslandText("Without root, native heads-up notifications can still appear. Android may hide sensitive notification text. Notification actions and camera alignment depend on your device and apps.",12,Secondary,lines=5)
        }
        IslandText("Dynamic Island 1.6 · Android 11+\nDesigned for centre punch-hole cameras.",12,Secondary,lines=2)
        Spacer(Modifier.height(20.dp))
    }
    }
    if(consent!=null) {
        val kind=consent
        val title=when(kind){"accessibility"->"Allow the notification overlay";"notifications"->"Allow notification access";"rootRecovery"->"Recover hidden text with root";else->"Enable the service with root"}
        val body=when(kind) {
            "accessibility"->"Dynamic Island uses an accessibility service only to draw an interactive island around your camera. It does not read screen contents, inspect other apps or perform gestures. Enable Dynamic Island on the next system settings screen. You can turn it off there at any time."
            "notifications"->"Notification access lets Dynamic Island read app names, message text, call and media status, and available actions so it can show them in the island. Replies are sent only when you press Send. Content stays on your device and is not stored as notification history."
            "rootRecovery"->"Android may hide verification codes. If you enable this optional feature, the app requests root and reads an unredacted notification dump only when visible text is missing. Only the matching notification record is used, in memory. Recovery may not work on every ROM."
            else->"This requests root to add Dynamic Island to your enabled accessibility services. Existing services are preserved. The service only draws the overlay and does not read your screen."
        }
        GlassDialog(title,dismiss={consent=null},confirmLabel="Agree and continue",confirm={
            consent=null
            when(kind) {
                "accessibility"->open(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                "notifications"->open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                "rootRecovery"->scope.launch { settings.toggle("rootRecovery",true) }
                else->{rootBusy=true;scope.launch { val ok=IslandController.root.enableAccessibility();rootBusy=false;IslandController.toast(if(ok)"Service enabled" else "Root permission unavailable") }}
            }
        }) { Text(body,color=palette.secondary) }
    }
    if(localPreview) GlassDialog("Preview island",dismiss={localPreview=false},confirmLabel="Set up overlay",confirm={localPreview=false;consent="accessibility"},dismissLabel="Close") {
        IslandText("Ama",17);IslandText("See you in a few minutes.")
        Text("Enable overlay access to preview around your camera and adjust its position live.",color=palette.secondary)
    }
    if(apps) AppPicker(layout.allowList,{scope.launch {settings.allow(it)}},{apps=false})
}
@Composable private fun SectionTitle(text: String) { IslandText(text,11,SettingsAccent,FontWeight.Bold,lines=1) }
@Composable private fun SettingsGroup(content: @Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth().iosReveal(90).softGlass(RoundedCornerShape(28.dp)).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
}
@Composable private fun AccessRow(title: String, status: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: ()->Unit) {
    Row(Modifier.fillMaxWidth().iosClickable(pressedScale=.985f,onClick=click).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Icon(icon,null,Modifier.size(22.dp),tint=SettingsAccent);IslandText(title,15,modifier=Modifier.weight(1f));IslandText(status,12,Secondary,lines=1);Icon(Icons.Rounded.ChevronRight,null,tint=Secondary)
    }
}
@Composable private fun SettingSwitch(title: String, checked: Boolean, enabled: Boolean=true, change: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { IslandText(title,14,modifier=Modifier.weight(1f));IosSwitch(checked,enabled,label=title,change=change) }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SettingSlider(title: String, key: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String) {
    val scope=rememberCoroutineScope()
    Column {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { IslandText(title,14);IslandText("${value.toInt()} $unit",12,Secondary,lines=1) }
        Slider(value.coerceIn(range),{v->scope.launch {IslandController.settings.number(key,v)}},valueRange=range,modifier=Modifier.fillMaxWidth(),
            colors=SliderDefaults.colors(thumbColor=Color.White,activeTrackColor=SettingsAccent.copy(alpha=.62f),inactiveTrackColor=LocalGlassPalette.current.track),
            thumb={ Box(Modifier.size(26.dp).softGlass(CircleShape,overlay=true).border(.8.dp,Color.White.copy(alpha=.9f),CircleShape)) },
            track={ state -> SliderDefaults.Track(state,Modifier.height(4.dp),colors=SliderDefaults.colors(activeTrackColor=SettingsAccent.copy(alpha=.62f),inactiveTrackColor=LocalGlassPalette.current.track),thumbTrackGapSize=0.dp,drawStopIndicator=null) })
    }
}
@Composable private fun AppPicker(selected: Set<String>, save: (Set<String>)->Unit, close: ()->Unit) {
    val c=androidx.compose.ui.platform.LocalContext.current
    var choices by remember { mutableStateOf<List<Pair<String,String>>>(emptyList()) }
    LaunchedEffect(Unit) { choices=withContext(Dispatchers.IO) { c.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0).map {it.activityInfo.packageName to it.loadLabel(c.packageManager).toString()}.distinctBy {it.first}.sortedBy {it.second.lowercase()} } }
    GlassDialog("Choose notification apps",dismiss=close,confirmLabel="Done",confirm=close,dismissLabel=null) {
        Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            choices.forEach { (pkg,name)->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    IosSwitch(pkg in selected,label=name) { on->save(if(on) selected+pkg else selected-pkg) }
                    IslandText(name,15,modifier=Modifier.weight(1f))
                }
            }
        }
    }
}
