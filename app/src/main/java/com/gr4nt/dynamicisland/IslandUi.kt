package com.gr4nt.dynamicisland

import android.graphics.Bitmap
import android.graphics.drawable.Icon as AndroidIcon
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.*
import kotlin.math.roundToInt

val IslandGreen=Color(0xFF34C759)
private val EqColors=listOf(Color(0xFFFF2D55),Color(0xFFFF9F0A),Color(0xFF30D158),Color(0xFF0A84FF))
val IslandRed=Color(0xFFFF3B30)
private val LocalIslandAccent=compositionLocalOf { Color(0xFFBF5AF2) }
/** Saturated artwork colour, with a stable vivid fallback for monochrome/missing art. */
private fun vividAccent(item: IslandItem): Color {
    val palette=listOf(0xFFBF5AF2,0xFF64D2FF,0xFFFF2D55,0xFFFF9F0A,0xFF30D158)
    val fallback=Color(palette[(item.pkg.hashCode() and 0x7fffffff)%palette.size])
    val bitmap=item.largeIcon ?: item.appIcon ?: return fallback
    return runCatching {
        val copy=bitmap.copy(Bitmap.Config.ARGB_8888,false) ?: return fallback
        try {
            val hsv=FloatArray(3);var score=0f;var hue=0f
            for(x in 0 until copy.width step 8) for(y in 0 until copy.height step 8) {
                val px=copy.getPixel(x,y)
                if(android.graphics.Color.alpha(px)<128) continue
                android.graphics.Color.colorToHSV(px,hsv)
                if(hsv[1]*hsv[2]>score) { score=hsv[1]*hsv[2];hue=hsv[0] }
            }
            if(score<.15f) fallback else Color(android.graphics.Color.HSVToColor(floatArrayOf(hue,.8f,1f)))
        } finally { copy.recycle() }
    }.getOrDefault(fallback)
}
private val ReplyVector=ImageVector.Builder("Reply",24.dp,24.dp,24f,24f).apply {
    path(fill=null,stroke=SolidColor(Color.White),strokeLineWidth=1.85f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round) {
        moveTo(9f,5f);lineTo(3f,11f);lineTo(9f,17f);moveTo(3f,11f);lineTo(13f,11f);curveTo(18f,11f,21f,14f,21f,20f)
    }
}.build()
private val ReadVector=ImageVector.Builder("Mark read",24.dp,24.dp,24f,24f).apply {
    path(fill=null,stroke=SolidColor(Color.White),strokeLineWidth=1.85f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round) {
        moveTo(2f,12f);curveTo(7f,4f,17f,4f,22f,12f);curveTo(17f,20f,7f,20f,2f,12f);close()
        moveTo(15f,12f);curveTo(15f,16f,9f,16f,9f,12f);curveTo(9f,8f,15f,8f,15f,12f);close()
    }
}.build()

@Composable fun IslandOverlay(state: IslandState, layout: IslandLayout) {
    val originalDensity=LocalDensity.current
    val scaled=Density(originalDensity.density*layout.size/100f,originalDensity.fontScale)
    CompositionLocalProvider(LocalDensity provides scaled) { IslandTheme(forceDark=true) { OverlayContent(state,layout) } }
}
@Composable private fun OverlayContent(state: IslandState, layout: IslandLayout) {
    var retained by remember { mutableStateOf(state.item) }
    var retainedExpanded by remember { mutableStateOf(state.expanded) }
    var retainedReplying by remember { mutableStateOf(state.replying) }
    val width=remember { Animatable(48f) }
    val height=remember { Animatable(30f) }
    val corner=remember { Animatable(15f) }
    val opacity=remember { Animatable(0f) }
    val reveal=remember { Animatable(0f) }
    val drag=remember { Animatable(0f) }
    val scope=rememberCoroutineScope()
    val targetTier=state.item?.let { IslandPresentation.tier(it,state.expanded,state.replying) }
    // A single shell owns both dimensions. Never scale text or ask content to size the shell.
    LaunchedEffect(state.item?.key,targetTier,state.replying) {
        reveal.animateTo(0f,IslandMotion.fadeOut())
        if(state.item==null) {
            coroutineScope {
                launch { width.animateTo(48f,IslandMotion.close()) }
                launch { height.animateTo(30f,IslandMotion.close()) }
                launch { corner.animateTo(15f,IslandMotion.close()) }
                launch { delay(150);opacity.animateTo(0f,IslandMotion.fadeOut()) }
            }
            retained=null
        } else {
            retained=state.item
            retainedExpanded=state.expanded;retainedReplying=state.replying
            val tier=targetTier ?: IslandTier.PILL
            coroutineScope {
                launch { width.animateTo(tier.width,IslandMotion.morph()) }
                launch { height.animateTo(tier.height,IslandMotion.morph()) }
                launch { corner.animateTo(tier.radius,IslandMotion.morph()) }
                launch { opacity.animateTo(1f,IslandMotion.fadeIn()) }
                launch { delay(95);reveal.animateTo(1f,IslandMotion.fadeIn()) }
            }
        }
    }
    val item=if(state.item?.key==retained?.key) state.item ?: retained else retained
    if(item==null) { Spacer(Modifier.size(1.dp));return }
    val tier=IslandPresentation.tier(item,retainedExpanded,retainedReplying)
    val compact=tier==IslandTier.PILL
    BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.TopCenter) {
        val contentWidth=minOf(tier.width.dp,maxWidth)
        val shellWidth=minOf(width.value.dp,maxWidth)
        val radius=minOf(corner.value,height.value/2f)
        val shape=RoundedCornerShape(radius.dp)
        Box(Modifier.width(shellWidth).height(height.value.dp.coerceAtLeast(1.dp))
            .testTag("island-shell").semantics { contentDescription="Island ${tier.name.lowercase()}" }
            .graphicsLayer { alpha=opacity.value;translationY=drag.value }
            .clip(shape).background(Color.Black)
            .draggable(rememberDraggableState { delta -> scope.launch { drag.snapTo((drag.value+delta).coerceAtMost(0f)) } },Orientation.Vertical,
                onDragStopped={ if(drag.value < -40f) { IslandController.dismiss();drag.snapTo(0f) } else drag.animateTo(0f,IslandMotion.morph()) })
            .clickable(enabled=!state.replying) { IslandController.tap() },contentAlignment=Alignment.TopCenter
        ) {
            LensMask(Modifier.matchParentSize(),layout)
            // Measure content at its destination size while the shell morphs and clips it.
            val accent=remember(item.largeIcon,item.appIcon,item.pkg) { vividAccent(item) }
            CompositionLocalProvider(LocalIslandAccent provides accent) {
            Box(Modifier.requiredSize(contentWidth,tier.height.dp).graphicsLayer {
                alpha=reveal.value.coerceIn(0f,1f)
                translationY=4.dp.toPx()*(1-reveal.value)
            },contentAlignment=Alignment.TopCenter) {
                when {
                    compact && item.call -> CallPill(item,Modifier.fillMaxWidth())
                    compact -> MusicPill(item,Modifier.fillMaxWidth())
                    tier==IslandTier.SMALL -> StatusCard(item)
                    item.call -> CallCard(item)
                    else -> Column(Modifier.fillMaxSize()) {
                        CameraBand(layout)
                        when {
                            item.media!=null -> MusicCard(item,Modifier.fillMaxWidth().weight(1f).padding(horizontal=20.dp,vertical=4.dp))
                            tier==IslandTier.MEDIUM -> BriefCard(item)
                            else -> Box(Modifier.weight(1f).fillMaxWidth()) {
                                MessageCard(item,state.replying,Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=4.dp))
                            }
                        }
                        if(layout.countdown && !state.replying && !item.call && item.media==null) Countdown(state.generation,layout.seconds)
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            }
        }
    }
}
@Composable private fun CameraBand(layout: IslandLayout) {
    val context=LocalContext.current
    val rect=context.getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.windowInsets.displayCutout?.boundingRectTop
    val physicalDensity=context.resources.displayMetrics.density
    val cameraHeight=(rect?.width()?.div(physicalDensity) ?: 26f).coerceAtMost(28f)/(layout.size/100f)
    Spacer(Modifier.height((cameraHeight+layout.gap+4f).coerceIn(28f,40f).dp))
}
@Composable private fun BriefCard(item: IslandItem) {
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Art(item.appIcon,32.dp,fallback=Icons.Rounded.ChatBubble)
        Column(Modifier.weight(1f)) {
            IslandText(item.title,16,weight=FontWeight.SemiBold,lines=1)
            IslandText(item.text,13,Secondary,lines=1)
        }
    }
}
@Composable private fun StatusCard(item: IslandItem) {
    val color=when(item.event) { IslandEvent.LOW_BATTERY -> IslandRed;IslandEvent.CHARGING -> IslandGreen;else -> Color(0xFFFF9F0A) }
    Row(Modifier.fillMaxSize().padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
        IslandText(item.title,12,weight=FontWeight.SemiBold,lines=1,modifier=Modifier.weight(1f))
        Spacer(Modifier.width(36.dp))
        Row(Modifier.weight(1f),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
            IslandText(item.text,12,color,lines=1)
            Spacer(Modifier.width(5.dp))
            Icon(when(item.event) { IslandEvent.CHARGING -> Icons.Rounded.BatteryChargingFull;IslandEvent.LOW_BATTERY -> Icons.Rounded.BatteryAlert;IslandEvent.HEADPHONES -> Icons.Rounded.Headphones;else -> Icons.Rounded.Notifications },null,Modifier.size(18.dp),tint=color)
        }
    }
}
@Composable private fun Countdown(generation: Long, seconds: Float) {
    val remaining=remember { Animatable(1f) }
    LaunchedEffect(generation,seconds) { remaining.snapTo(1f);remaining.animateTo(0f,tween((seconds*1000).toInt(),easing=LinearEasing)) }
    Box(Modifier.fillMaxWidth().height(2.dp).background(LocalGlassPalette.current.text.copy(alpha=.08f))) { Box(Modifier.fillMaxWidth(remaining.value).fillMaxHeight().background(LocalIslandAccent.current)) }
}
@Composable fun IslandText(text: String, size: Int=14, color: Color=LocalGlassPalette.current.text, weight: FontWeight=FontWeight.Medium, lines: Int=2, modifier: Modifier=Modifier) {
    Text(text,modifier,color=color,fontSize=size.sp,fontWeight=weight,letterSpacing=(-.3).sp,maxLines=lines,overflow=TextOverflow.Ellipsis,lineHeight=(size+4).sp)
}
@Composable private fun Art(bitmap: Bitmap?, size: Dp, modifier: Modifier=Modifier, fallback: ImageVector=Icons.Rounded.MusicNote) {
    if(bitmap!=null) Image(bitmap.asImageBitmap(),null,modifier.size(size).clip(RoundedCornerShape(size/4)),contentScale=ContentScale.Crop)
    else Box(modifier.size(size).clip(RoundedCornerShape(size/4)).background(Brush.linearGradient(listOf(Color(0xFFB354FF),Color(0xFF397AFF)))),contentAlignment=Alignment.Center) { Icon(fallback,null,Modifier.size(size*.6f),tint=Color.White) }
}
@Composable private fun MessageCard(item: IslandItem, replying: Boolean, modifier: Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Art(item.appIcon,16.dp,fallback=Icons.Rounded.ChatBubble);IslandText(item.app,12,Color(item.accent),lines=1,modifier=Modifier.weight(1f));IslandText("now",12,Secondary)
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                IslandText(item.title,17,weight=FontWeight.SemiBold,lines=1)
                if(item.code!=null) Text(item.code,color=Color(0xFF66D4FF),fontSize=28.sp,fontWeight=FontWeight.Bold,letterSpacing=3.sp)
                else IslandText(item.text,14,Secondary,lines=if(replying)3 else 6)
            }
            if(item.largeIcon!=null && item.code==null) Image(item.largeIcon.asImageBitmap(),null,Modifier.size(44.dp).clip(CircleShape),contentScale=ContentScale.Crop)
        }
        if(replying) ReplyField(item)
        else if(item.code!=null) ActionPill("Copy code",Icons.Rounded.ContentCopy) { IslandController.copyCode() }
        else if(item.actions.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            item.actions.forEach { action ->
                val kind=if(action.reply) "reply" else action.kind
                val vector=when(kind) { "reply"->ReplyVector;"read"->ReadVector;"delete"->Icons.Rounded.DeleteOutline;"archive"->Icons.Rounded.Archive;else->null }
                if(kind=="reply" || kind=="read") RoundButton(action.title.ifBlank { kind },vector ?: ReplyVector,Color.Transparent,38.dp) { IslandController.action(action) }
                else ActionPill(action.title,vector,action.icon) { IslandController.action(action) }
            }
        }
    }
}
@Composable private fun ReplyField(item: IslandItem) {
    var reply by remember(item.key) { mutableStateOf("") }
    val requester=remember { FocusRequester() };val keyboard=LocalSoftwareKeyboardController.current
    LaunchedEffect(item.key) { delay(150);requester.requestFocus();keyboard?.show() }
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        BasicTextField(reply,{reply=it.take(4000)},Modifier.weight(1f).softGlass(RoundedCornerShape(18.dp)).padding(12.dp).focusRequester(requester),
            textStyle=TextStyle(color=LocalGlassPalette.current.text,fontSize=15.sp),cursorBrush=SolidColor(SettingsAccent),maxLines=4,
            keyboardOptions=KeyboardOptions(imeAction=ImeAction.Send),keyboardActions=KeyboardActions(onSend={IslandController.sendReply(reply)}),
            decorationBox={ field -> if(reply.isEmpty()) IslandText("Reply…",14,Secondary);field() })
        RoundButton("Send reply",Icons.AutoMirrored.Rounded.Send,IslandGreen,38.dp) { IslandController.sendReply(reply) }
        RoundButton("Cancel reply",Icons.Rounded.Close,Color.Transparent,32.dp) { IslandController.cancelReply() }
    }
}
@Composable private fun CallCard(item: IslandItem) {
    val decline=item.actions.firstOrNull { it.kind=="decline" }
    val accept=item.actions.firstOrNull { it.kind=="accept" }
    // Both side groups are centred in the shell, with an empty lane for the lens.
    Row(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
        Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(38.dp).testTag("call-avatar").clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF5E5CE6),Color(0xFFBF5AF2)))),contentAlignment=Alignment.Center) {
                if(item.avatar!=null) Image(item.avatar.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                else {
                    val initial=item.title.trim().firstOrNull()?.takeIf { it.isLetter() }
                    if(initial!=null) IslandText(initial.uppercase(),18,weight=FontWeight.SemiBold) else Icon(Icons.Rounded.Person,null,tint=Color.White)
                }
            }
            Column(Modifier.weight(1f)) {
                IslandText(item.title,16,weight=FontWeight.SemiBold,lines=1)
                IslandText(if(item.incoming) "Incoming Call" else if(item.callStartedAtElapsed==null) "Calling…" else "Ongoing Call",12,Secondary,lines=1)
            }
        }
        Spacer(Modifier.width(36.dp))
        Row(Modifier.weight(1f),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
            if(decline!=null) RoundButton(if(item.incoming) "Decline call" else "Hang up",Icons.Rounded.CallEnd,IslandRed,40.dp) { IslandController.action(decline) }
            if(item.incoming && accept!=null) { Spacer(Modifier.width(10.dp));RoundButton("Accept call",Icons.Rounded.Call,IslandGreen,40.dp) { IslandController.action(accept) } }
        }
    }
}
@Composable private fun ColorWaveform(playing: Boolean, modifier: Modifier=Modifier, bars: Int=4) {
    val transition=rememberInfiniteTransition(label="colour branch visualizer")
    Row(modifier.height(20.dp).semantics { contentDescription="Color visualizer" },horizontalArrangement=Arrangement.spacedBy(1.5.dp),verticalAlignment=Alignment.CenterVertically) {
        repeat(bars) { i ->
            val h by transition.animateFloat(3f+(i%2)*3,16f-(i%3)*2,infiniteRepeatable(tween(IslandMotion.waveMillis+i*60,easing=LinearEasing),RepeatMode.Reverse),label="colour level $i")
            Box(Modifier.width(2.5.dp).height(if(playing) h.dp else 3.dp).clip(CircleShape).background(EqColors[i%EqColors.size]))
        }
    }
}
@Composable private fun MusicPill(item: IslandItem, modifier: Modifier) {
    Row(modifier.height(IslandTier.PILL.height.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        if(item.largeIcon!=null) Image(item.largeIcon.asImageBitmap(),null,Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)),contentScale=ContentScale.Crop)
        else Icon(Icons.Rounded.MusicNote,null,Modifier.size(18.dp),tint=LocalIslandAccent.current)
        Spacer(Modifier.width(30.dp))
        ColorWaveform(item.media?.playing==true)
    }
}
@Composable private fun MusicCard(item: IslandItem,modifier: Modifier) {
    val media=item.media ?: return
    val accent=LocalIslandAccent.current
    var position by remember(item.key) { mutableLongStateOf(media.elapsed()) }
    LaunchedEffect(media) { while(true) { position=media.elapsed();delay(500) } }
    Column(modifier,verticalArrangement=Arrangement.SpaceBetween) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Art(item.largeIcon,50.dp)
            Column(Modifier.weight(1f)) { IslandText(item.title,16,weight=FontWeight.SemiBold,lines=1);IslandText(item.text,13,Secondary,lines=1) }
            ColorWaveform(media.playing,bars=5)
        }
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            val fraction=if(media.duration>0) (position.toFloat()/media.duration).coerceIn(0f,1f) else 0f
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(accent.copy(alpha=.24f))) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Brush.horizontalGradient(listOf(accent,Color(0xFF64D2FF)))))
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                IslandText(clock(position),11,accent)
                IslandText(if(media.duration>0) "−"+clock((media.duration-position).coerceAtLeast(0)) else "",11,accent)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
            MediaControl(Icons.Rounded.FastRewind,"Previous",42.dp,accent) { performMedia(item,"prev") }
            MediaControl(if(media.playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,if(media.playing)"Pause" else "Play",48.dp,accent) { performMedia(item,"toggle") }
            MediaControl(Icons.Rounded.FastForward,"Next",42.dp,accent) { performMedia(item,"next") }
            MediaControl(Icons.Rounded.Airplay,"Audio output",32.dp,accent) { IslandController.openAudioOutput() }
        }
    }
}
private fun performMedia(item: IslandItem, command: String) {
    val controls=item.media?.controller?.transportControls
    if(controls!=null) when(command) {
        "prev" -> controls.skipToPrevious()
        "next" -> controls.skipToNext()
        else -> if(item.media?.playing==true) controls.pause() else controls.play()
    } else {
        val pattern=when(command) { "prev" -> "(?i)prev|back|rewind";"next" -> "(?i)next|forward";else -> "(?i)play|pause|resume" }
        item.actions.firstOrNull { Regex(pattern).containsMatchIn(it.title) }?.let(IslandController::action)
    }
}
@Composable private fun MediaControl(vector: ImageVector,label: String,size: Dp,tint: Color,click: ()->Unit) {
    Box(Modifier.size(size).iosClickable(pressedScale=.9f,onClick=click),contentAlignment=Alignment.Center) {
        Icon(vector,label,Modifier.size(size*.72f),tint=tint)
    }
}
private fun clock(ms: Long): String { val seconds=(ms/1000).coerceAtLeast(0);return "%d:%02d".format(seconds/60,seconds%60) }
@Composable private fun iconBitmap(icon: AndroidIcon?): Bitmap? {
    val c=LocalContext.current
    return remember(icon) { runCatching { icon?.loadDrawable(c)?.toBitmap(64,64,Bitmap.Config.ARGB_8888)?.copy(Bitmap.Config.ARGB_8888,false) }.getOrNull() }
}
@Composable private fun NotificationIconButton(a: IslandAction, size: Dp, click: ()->Unit) {
    val b=iconBitmap(a.icon)
    Box(Modifier.size(size).iosClickable(pressedScale=.88f,onClick=click),contentAlignment=Alignment.Center) {
        if(b!=null) Image(b.asImageBitmap(),a.title,Modifier.size(size*.65f),colorFilter=ColorFilter.tint(LocalGlassPalette.current.text))
        else Icon(if(a.title.contains("pause",true))Icons.Rounded.Pause else if(a.title.contains("next",true))Icons.Rounded.SkipNext else if(a.title.contains("prev",true))Icons.Rounded.SkipPrevious else Icons.Rounded.PlayArrow,a.title,tint=LocalGlassPalette.current.text)
    }
}
@Composable fun RoundButton(label: String, vector: ImageVector, color: Color, size: Dp=42.dp, flat: Boolean=false, click: ()->Unit) {
    val tinted=color!=Color.Transparent
    Box(Modifier.size(size).iosClickable(pressedScale=.88f,onClick=click).then(if(flat) Modifier else Modifier.softGlass(CircleShape,tint=if(tinted)color else null)),contentAlignment=Alignment.Center) {
        Icon(vector,label,Modifier.size(size*.52f),tint=if(tinted)Color.White else LocalGlassPalette.current.text)
    }
}
@Composable fun ActionPill(label: String, vector: ImageVector?=null, icon: AndroidIcon?=null, click: ()->Unit) {
    val b=iconBitmap(icon)
    Row(Modifier.height(38.dp).iosClickable(onClick=click).softGlass(CircleShape).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        if(vector!=null) Icon(vector,null,Modifier.size(18.dp),tint=LocalGlassPalette.current.text)
        else if(b!=null) Image(b.asImageBitmap(),null,Modifier.size(18.dp),colorFilter=ColorFilter.tint(LocalGlassPalette.current.text))
        IslandText(label,14,weight=FontWeight.SemiBold,lines=1)
    }
}

@Composable private fun LensMask(modifier: Modifier, layout: IslandLayout) {
    val context=LocalContext.current
    val actualDensity=context.resources.displayMetrics.density
    val rect=context.getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.windowInsets.displayCutout?.boundingRectTop
    val diameter=rect?.width()?.toFloat() ?: 24f*actualDensity
    // Cutout bounds may include generous OEM clearance around the physical lens.
    val maskDiameter=diameter.coerceAtMost(28f*actualDensity)
    val lensY=diameter/2+layout.gap*actualDensity
    Canvas(modifier) {
        drawCircle(Color.Black,maskDiameter/2+1.5f*actualDensity,center=androidx.compose.ui.geometry.Offset(size.width/2,lensY))
    }
}

@Composable private fun CallPill(item: IslandItem, modifier: Modifier) {
    val start=item.callStartedAtElapsed
    var duration by remember(start) { mutableLongStateOf(if(start!=null) (android.os.SystemClock.elapsedRealtime()-start).coerceAtLeast(0) else 0) }
    LaunchedEffect(start) { if(start!=null) while(true) { duration=(android.os.SystemClock.elapsedRealtime()-start).coerceAtLeast(0);delay(1000) } }
    Row(modifier.height(IslandTier.PILL.height.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
        IslandText(if(start==null) "Calling…" else clock(duration),if(start==null)10 else 13,IslandGreen,weight=FontWeight.SemiBold,lines=1,
            modifier=Modifier.width(44.dp).semantics { contentDescription=if(start==null) "Calling" else "Call duration ${clock(duration)}" })
        Spacer(Modifier.weight(1f))
        Row(Modifier.width(42.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.End) {
            if(item.appIcon!=null) Image(item.appIcon.asImageBitmap(),"Dialer",Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)),contentScale=ContentScale.Crop)
            else Icon(Icons.Rounded.Call,"Dialer",Modifier.size(15.dp),tint=IslandGreen)
            Spacer(Modifier.width(4.dp))
            ColorWaveform(start!=null,bars=4)
        }
    }
}
