package com.gr4nt.dynamicisland

import android.content.*
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
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
@Composable private fun SettingsScreen() {
    val c=androidx.compose.ui.platform.LocalContext.current
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
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal=22.dp,vertical=24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        IslandText("DYNAMIC ISLAND",11,SettingsAccent,FontWeight.Bold,lines=1)
        IslandText("Around your camera.",30,weight=FontWeight.Bold,lines=2)
        IslandText("Your notifications. One quiet place.",14,Color(0xFFA4A6B3))
        Box(Modifier.fillMaxWidth().height(162.dp).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Color(0xFF202A46),Color(0xFF132B27),Color(0xFF171923)))).padding(12.dp),contentAlignment=Alignment.TopCenter) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(36.dp)).background(Color.Black).border(.8.dp,Color(0xFF34493B),RoundedCornerShape(36.dp)).padding(horizontal=20.dp,vertical=15.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(11.dp).clip(CircleShape).background(Color(0xFF151A27)))
                IslandText("Messages · now",12,Secondary)
                IslandText("Ama",17,weight=FontWeight.SemiBold)
                IslandText("See you in a few minutes.",14,Color.White.copy(alpha=.76f))
            }
        }
        Button(onClick={ if(connected)IslandController.preview() else localPreview=true },Modifier.fillMaxWidth().height(52.dp),shape=CircleShape,colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF353D61),contentColor=Color.White)) {
            Icon(Icons.Rounded.PlayArrow,null);Spacer(Modifier.width(8.dp));Text("Preview island",fontWeight=FontWeight.SemiBold)
        }
        SectionTitle("GET CONNECTED")
        SettingsGroup {
            AccessRow("Overlay access",if(connected)"Connected" else "Set up",Icons.Rounded.Layers) { consent="accessibility" }
            HorizontalDivider(color=Color.White.copy(alpha=.06f))
            AccessRow("Notification access",if(listener)"Connected" else "Set up",Icons.Rounded.Notifications) { consent="notifications" }
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
            SettingSlider("Show time","seconds",layout.seconds,3f..20f,"seconds")
            SettingSwitch("Tinted outline",layout.outline) { scope.launch { settings.toggle("outline",it) } }
            SettingSwitch("Countdown line",layout.countdown) { scope.launch { settings.toggle("countdown",it) } }
            SettingSwitch("Handle all apps",layout.allApps) { scope.launch { settings.toggle("allApps",it) } }
            if(!layout.allApps) TextButton(onClick={apps=true}) { Text("Choose apps · ${layout.allowList.size} selected") }
        }
        SettingsGroup {
            AccessRow("Advanced layout",if(advanced)"Less" else "More",Icons.Rounded.Tune) { advanced=!advanced }
            if(advanced) {
                SettingSlider("Camera to content","cameraSpace",layout.cameraSpace,0f..40f,"dp")
                SettingSlider("Space below content","bottomSpace",layout.bottomSpace,0f..40f,"dp")
                SettingSlider("Top left corner","topLeft",layout.topLeft,0f..60f,"dp")
                SettingSlider("Top right corner","topRight",layout.topRight,0f..60f,"dp")
                SettingSlider("Bottom left corner","bottomLeft",layout.bottomLeft,0f..60f,"dp")
                SettingSlider("Bottom right corner","bottomRight",layout.bottomRight,0f..60f,"dp")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick={ if(connected)IslandController.preview("call") else localPreview=true }) { Text("Test call") }
                    TextButton(onClick={ if(connected)IslandController.preview("code") else localPreview=true }) { Text("Test code") }
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
            TextButton(onClick={consent="rootEnable"},enabled=!rootBusy) { Text("Enable overlay service with root") }
        }
        SettingsGroup {
            IslandText("Using HyperOS?",16,weight=FontWeight.SemiBold)
            IslandText("Disable the system music island for each music app in Settings → Notifications → Hyper Island → App permissions. This app cannot remove it.",13,Secondary,lines=5)
            IslandText("Without root, native heads-up notifications can still appear. Android may hide sensitive notification text. Notification actions and camera alignment depend on your device and apps.",12,Secondary,lines=5)
        }
        IslandText("Dynamic Island 1.0 · Android 11+\nDesigned for centre punch-hole cameras.",12,Secondary,lines=2)
        Spacer(Modifier.height(20.dp))
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
        AlertDialog(onDismissRequest={consent=null},title={Text(title)},text={Text(body)},confirmButton={TextButton(onClick={
            consent=null
            when(kind) {
                "accessibility"->open(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                "notifications"->open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                "rootRecovery"->scope.launch { settings.toggle("rootRecovery",true) }
                else->{rootBusy=true;scope.launch { val ok=IslandController.root.enableAccessibility();rootBusy=false;IslandController.toast(if(ok)"Service enabled" else "Root permission unavailable") }}
            }
        }) { Text("Agree and continue") }},dismissButton={TextButton(onClick={consent=null}){Text("Cancel")}})
    }
    if(localPreview) AlertDialog(onDismissRequest={localPreview=false},title={Text("Preview island")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){IslandText("Ama",17);IslandText("See you in a few minutes.");IslandText("Enable overlay access to preview around your camera and adjust its position live.",13,Secondary,lines=4)}},confirmButton={TextButton(onClick={localPreview=false;consent="accessibility"}){Text("Set up overlay")}},dismissButton={TextButton(onClick={localPreview=false}){Text("Close")}})
    if(apps) AppPicker(layout.allowList,{scope.launch {settings.allow(it)}},{apps=false})
}
@Composable private fun SectionTitle(text: String) { IslandText(text,11,SettingsAccent,FontWeight.Bold,lines=1) }
@Composable private fun SettingsGroup(content: @Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
}
@Composable private fun AccessRow(title: String, status: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: ()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=click).padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Icon(icon,null,Modifier.size(22.dp),tint=SettingsAccent);IslandText(title,15,modifier=Modifier.weight(1f));IslandText(status,12,Secondary,lines=1);Icon(Icons.Rounded.ChevronRight,null,tint=Secondary)
    }
}
@Composable private fun SettingSwitch(title: String, checked: Boolean, enabled: Boolean=true, change: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { IslandText(title,14,modifier=Modifier.weight(1f));Switch(checked,change,enabled=enabled,colors=SwitchDefaults.colors(checkedTrackColor=Color(0xFF465584),checkedThumbColor=Color.White)) }
}
@Composable private fun SettingSlider(title: String, key: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String) {
    val scope=rememberCoroutineScope()
    Column {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { IslandText(title,14);IslandText("${value.toInt()} $unit",12,Secondary,lines=1) }
        Slider(value.coerceIn(range),{v->scope.launch {IslandController.settings.number(key,v)}},valueRange=range,modifier=Modifier.fillMaxWidth(),colors=SliderDefaults.colors(thumbColor=SettingsAccent,activeTrackColor=Color(0xFF7C8BBB),inactiveTrackColor=Color(0xFF303543)))
    }
}
@Composable private fun AppPicker(selected: Set<String>, save: (Set<String>)->Unit, close: ()->Unit) {
    val c=androidx.compose.ui.platform.LocalContext.current
    var choices by remember { mutableStateOf<List<Pair<String,String>>>(emptyList()) }
    LaunchedEffect(Unit) { choices=withContext(Dispatchers.IO) { c.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0).map {it.activityInfo.packageName to it.loadLabel(c.packageManager).toString()}.distinctBy {it.first}.sortedBy {it.second.lowercase()} } }
    AlertDialog(onDismissRequest=close,title={Text("Choose notification apps")},text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState())) {choices.forEach { (pkg,name)->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Checkbox(pkg in selected,{on->save(if(on) selected+pkg else selected-pkg)});Text(name,Modifier.weight(1f))}}}},confirmButton={TextButton(onClick=close){Text("Done")}})
}
