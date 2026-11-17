package com.gr4nt.dynamicisland

import android.graphics.Bitmap
import android.graphics.drawable.Icon as AndroidIcon
import androidx.compose.animation.animateContentSize
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
val IslandRed=Color(0xFFFF3B30)
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
    CompositionLocalProvider(LocalDensity provides scaled) { OverlayContent(state,layout) }
}
@Composable private fun OverlayContent(state: IslandState, layout: IslandLayout) {
    var retained by remember { mutableStateOf(state.item) }
    val opening=remember { Animatable(0f) }
    val drag=remember { Animatable(0f) }
    val reveal=remember { Animatable(0f) }
    val scope=rememberCoroutineScope()
    LaunchedEffect(state.item?.key) {
        if(state.item != null) { retained=state.item;reveal.snapTo(0f);launch { reveal.animateTo(1f,tween(280,easing=IosEase)) };opening.animateTo(1f,spring(.78f,420f)) }
        else { opening.animateTo(0f,tween(400,easing=CubicBezierEasing(.4f,0f,.2f,1f)));retained=null }
    }
    val item=state.item ?: retained
    if(item == null) { Spacer(Modifier.size(1.dp));return }
    val compact=(item.media != null || (item.call && !item.incoming)) && !state.expanded
    val pillWidth=if(item.call)144.dp else 108.dp
    BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.TopCenter) {
        val cardWidth=maxWidth
        val shellWidth by animateDpAsState(if(compact)pillWidth else cardWidth,spring(.8f,Spring.StiffnessMediumLow),label="shell width")
        val widthProgress=if(cardWidth>pillWidth) ((shellWidth-pillWidth)/(cardWidth-pillWidth)).coerceIn(0f,1f) else 1f
        val corners=RoundedCornerShape(layout.topLeft.dp,layout.topRight.dp,layout.bottomRight.dp,layout.bottomLeft.dp)
        val contentAlpha=minOf(((opening.value-.4f)/.6f).coerceIn(0f,1f),reveal.value)
        Box(Modifier.width(shellWidth).graphicsLayer {
            transformOrigin=TransformOrigin(.5f,0f)
            scaleX=.26f+.74f*opening.value
            scaleY=.15f+.85f*opening.value
            alpha=(opening.value*4).coerceIn(0f,1f)
            translationY=drag.value-6.dp.toPx()*(1-opening.value)
        }.softGlass(corners,overlay=true)
            .animateContentSize(spring(.8f,420f))
            .draggable(rememberDraggableState { delta -> scope.launch { drag.snapTo((drag.value+delta).coerceAtMost(0f)) } },Orientation.Vertical,
                onDragStopped={ if(drag.value < -40f) { IslandController.dismiss();drag.snapTo(0f) } else drag.animateTo(0f,spring(.7f,500f)) })
            .clickable(enabled=!state.replying) { IslandController.tap() }
        ) {
            // Fake frost uses notification art, never a rectangular live backdrop blur.
            val art=if(item.call) item.avatar else item.largeIcon ?: item.appIcon
            if(art!=null && !compact) {
                val palette=LocalGlassPalette.current
                Image(art.asImageBitmap(),null,Modifier.matchParentSize().graphicsLayer { scaleX=1.3f;scaleY=1.3f;alpha=if(palette.dark).12f else .09f }.blur(38.dp,BlurredEdgeTreatment.Unbounded),contentScale=ContentScale.Crop)
            }
            LensMask(Modifier.matchParentSize(),layout,compact)
            Box(Modifier.graphicsLayer { alpha=contentAlpha;translationY=10.dp.toPx()*(1-contentAlpha);scaleX=.92f+.08f*contentAlpha;scaleY=scaleX }) {
                if(item.media!=null && !item.call) {
                    // Each state measures at its final width, even while the shell is narrow.
                    Box(contentAlignment=Alignment.TopCenter) {
                        if(widthProgress < .99f) MusicPill(item,Modifier.requiredWidth(108.dp).alpha(1-widthProgress))
                        if(widthProgress > .01f) Column(Modifier.requiredWidth(cardWidth).alpha(widthProgress)) {
                            CameraBand(layout)
                            MusicCard(item,Modifier.padding(horizontal=20.dp,vertical=8.dp))
                        }
                    }
                } else if(item.call) Box(contentAlignment=Alignment.TopCenter) {
                    if(widthProgress < .99f) CallPill(item,Modifier.requiredWidth(pillWidth).alpha(1-widthProgress))
                    if(widthProgress > .01f) Box(Modifier.requiredWidth(cardWidth).alpha(widthProgress)) { CallCard(item) }
                }
                else Column(Modifier.requiredWidth(cardWidth)) {
                    CameraBand(layout)
                    MessageCard(item,state.replying,Modifier.padding(horizontal=20.dp))
                    Spacer(Modifier.height(layout.bottomSpace.dp+8.dp))
                    if(layout.countdown && !state.replying) Countdown(state.generation,layout.seconds)
                }
            }
        }
    }
}
@Composable private fun CameraBand(layout: IslandLayout) {
    // Camera dimensions remain physical when the rest of the island is scaled.
    val context=LocalContext.current
    val rect=context.getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.windowInsets.displayCutout?.boundingRectTop
    val physicalDensity=context.resources.displayMetrics.density
    val cameraHeight=(rect?.width()?.div(physicalDensity) ?: 26f)/(layout.size/100f)
    Spacer(Modifier.height(cameraHeight.dp+layout.cameraSpace.dp+layout.gap.dp))
}
@Composable private fun Countdown(generation: Long, seconds: Float) {
    val remaining=remember { Animatable(1f) }
    LaunchedEffect(generation,seconds) { remaining.snapTo(1f);remaining.animateTo(0f,tween((seconds*1000).toInt(),easing=LinearEasing)) }
    Box(Modifier.fillMaxWidth().height(2.dp).background(LocalGlassPalette.current.text.copy(alpha=.08f))) { Box(Modifier.fillMaxWidth(remaining.value).fillMaxHeight().background(LocalGlassPalette.current.text.copy(alpha=.4f))) }
}
@Composable fun IslandText(text: String, size: Int=14, color: Color=LocalGlassPalette.current.text, weight: FontWeight=FontWeight.Medium, lines: Int=2, modifier: Modifier=Modifier) {
    Text(text,modifier,color=color,fontSize=size.sp,fontWeight=weight,letterSpacing=(-.3).sp,maxLines=lines,overflow=TextOverflow.Ellipsis,lineHeight=(size+4).sp)
}
@Composable private fun Art(bitmap: Bitmap?, size: Dp, modifier: Modifier=Modifier) {
    if(bitmap!=null) Image(bitmap.asImageBitmap(),null,modifier.size(size).clip(RoundedCornerShape(size/4)),contentScale=ContentScale.Crop)
    else Box(modifier.size(size).clip(RoundedCornerShape(size/4)).background(Color(0xFF30343D)),contentAlignment=Alignment.Center) { Icon(Icons.Rounded.MusicNote,null,Modifier.size(size*.6f),tint=Secondary) }
}
@Composable private fun MessageCard(item: IslandItem, replying: Boolean, modifier: Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Art(item.appIcon,16.dp);IslandText(item.app,12,Secondary,lines=1,modifier=Modifier.weight(1f));IslandText("now",12,Secondary)
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                IslandText(item.title,17,weight=FontWeight.SemiBold)
                if(item.code!=null) Text(item.code,color=SettingsAccent,fontSize=28.sp,fontWeight=FontWeight.Bold,letterSpacing=3.sp)
                else IslandText(item.text,14,Secondary)
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
    val decline=item.actions.firstOrNull { it.kind=="decline" } ?: item.actions.firstOrNull()
    val accept=item.actions.firstOrNull { it.kind=="accept" } ?: item.actions.getOrNull(1)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal=26.dp,vertical=14.dp)) {
        // Keep text and call controls beside the lens rather than drawing through it.
        val side=((maxWidth-42.dp)/2).coerceAtLeast(80.dp)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Row(Modifier.width(side),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if(item.avatar!=null) Image(item.avatar.asImageBitmap(),null,Modifier.size(42.dp).clip(CircleShape),contentScale=ContentScale.Crop)
                else Box(Modifier.size(42.dp).softGlass(CircleShape),contentAlignment=Alignment.Center) {
                    val initial=item.title.trim().firstOrNull()?.takeIf { it.isLetter() }
                    if(initial!=null) IslandText(initial.uppercase(),20,weight=FontWeight.SemiBold) else Icon(Icons.Rounded.Person,null,tint=LocalGlassPalette.current.text)
                }
                Column(Modifier.weight(1f)) { IslandText(item.title,17,weight=FontWeight.SemiBold,lines=1);IslandText(if(item.incoming) "Incoming Call" else "Ongoing Call",13,Secondary,lines=2) }
            }
            Row(Modifier.width(side),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
                if(decline!=null) RoundButton(if(item.incoming) "Decline call" else "Hang up",Icons.Rounded.CallEnd,IslandRed) { IslandController.action(decline) }
                if(item.incoming && accept!=null) { Spacer(Modifier.width(8.dp));RoundButton("Accept call",Icons.Rounded.Call,IslandGreen) { IslandController.action(accept) } }
            }
        }
    }
}
@Composable private fun MusicPill(item: IslandItem, modifier: Modifier) {
    Row(modifier.height(34.dp).padding(horizontal=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        if(item.largeIcon!=null) Image(item.largeIcon.asImageBitmap(),null,Modifier.size(22.dp,15.dp).clip(RoundedCornerShape(3.dp)),contentScale=ContentScale.Crop)
        else Icon(Icons.Rounded.MusicNote,null,Modifier.size(18.dp),tint=SettingsAccent)
        // This transparent middle section sits directly over the physical lens.
        Spacer(Modifier.width(30.dp))
        val transition=rememberInfiniteTransition(label="levels")
        Row(horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically) {
            repeat(4) { i ->
                val h by transition.animateFloat(3f+(i%2)*3,14f-(i%3)*2,infiniteRepeatable(tween(350+i*80,easing=LinearEasing),RepeatMode.Reverse),label="level $i")
                Box(Modifier.width(2.5.dp).height(if(item.media?.playing==true) h.dp else 3.dp).clip(CircleShape).background(SettingsAccent))
            }
        }
    }
}
@Composable private fun MusicCard(item: IslandItem,modifier: Modifier) {
    val media=item.media ?: return
    var position by remember(item.key) { mutableLongStateOf(media.elapsed()) }
    LaunchedEffect(media) { while(true) { position=media.elapsed();delay(1000) } }
    Column(modifier,verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Art(item.largeIcon,52.dp)
            Column(Modifier.weight(1f)) { IslandText(item.title,17,weight=FontWeight.SemiBold);IslandText(item.text,14,Secondary) }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
            val controls=item.actions.filterNot { it.reply }.filter { Regex("(?i)prev|back|rewind|play|pause|next|forward").containsMatchIn(it.title) }.take(3)
            if(controls.isNotEmpty()) controls.forEachIndexed { i,a ->
                NotificationIconButton(a,if(i==1)48.dp else 38.dp) { IslandController.action(a) }
            } else {
                RoundButton("Previous",Icons.Rounded.SkipPrevious,Color.Transparent,38.dp) { media.controller?.transportControls?.skipToPrevious() }
                RoundButton(if(media.playing)"Pause" else "Play",if(media.playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,Color.Transparent,48.dp) { if(media.playing)media.controller?.transportControls?.pause() else media.controller?.transportControls?.play() }
                RoundButton("Next",Icons.Rounded.SkipNext,Color.Transparent,38.dp) { media.controller?.transportControls?.skipToNext() }
            }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(LocalGlassPalette.current.track)) {
            Box(Modifier.fillMaxWidth(if(media.duration>0) (position.toFloat()/media.duration).coerceIn(0f,1f) else 0f).fillMaxHeight().background(SettingsAccent))
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { IslandText(clock(position),11,Secondary);IslandText(clock(media.duration),11,Secondary) }
    }
}
private fun clock(ms: Long): String { val seconds=(ms/1000).coerceAtLeast(0);return "%d:%02d".format(seconds/60,seconds%60) }
@Composable private fun iconBitmap(icon: AndroidIcon?): Bitmap? {
    val c=LocalContext.current
    return remember(icon) { runCatching { icon?.loadDrawable(c)?.toBitmap(64,64,Bitmap.Config.ARGB_8888)?.copy(Bitmap.Config.ARGB_8888,false) }.getOrNull() }
}
@Composable private fun NotificationIconButton(a: IslandAction, size: Dp, click: ()->Unit) {
    val b=iconBitmap(a.icon)
    Box(Modifier.size(size).iosClickable(pressedScale=.88f,onClick=click).softGlass(CircleShape),contentAlignment=Alignment.Center) {
        if(b!=null) Image(b.asImageBitmap(),a.title,Modifier.size(size*.65f),colorFilter=ColorFilter.tint(LocalGlassPalette.current.text))
        else Icon(if(a.title.contains("pause",true))Icons.Rounded.Pause else if(a.title.contains("next",true))Icons.Rounded.SkipNext else if(a.title.contains("prev",true))Icons.Rounded.SkipPrevious else Icons.Rounded.PlayArrow,a.title,tint=LocalGlassPalette.current.text)
    }
}
@Composable fun RoundButton(label: String, vector: ImageVector, color: Color, size: Dp=42.dp, click: ()->Unit) {
    val tinted=color!=Color.Transparent
    Box(Modifier.size(size).iosClickable(pressedScale=.88f,onClick=click).softGlass(CircleShape,tint=if(tinted)color else null),contentAlignment=Alignment.Center) {
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

@Composable private fun LensMask(modifier: Modifier, layout: IslandLayout, compact: Boolean) {
    val context=LocalContext.current
    val actualDensity=context.resources.displayMetrics.density
    val rect=context.getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.windowInsets.displayCutout?.boundingRectTop
    val diameter=rect?.width()?.toFloat() ?: 24f*actualDensity
    // Cutout bounds may include generous OEM clearance around the physical lens.
    val maskDiameter=diameter.coerceAtMost(28f*actualDensity)
    val estimatedTop=rect?.let { NotificationRules.cameraTop(it.top,it.bottom,it.width()).toFloat() } ?: 10f*actualDensity
    val lensY=if(compact) estimatedTop+diameter/2-10f*actualDensity else diameter/2+layout.gap*actualDensity
    Canvas(modifier) {
        drawCircle(Color.Black,maskDiameter/2+1.5f*actualDensity,center=androidx.compose.ui.geometry.Offset(size.width/2,lensY))
    }
}

@Composable private fun CallPill(item: IslandItem, modifier: Modifier) {
    val fallbackStart=remember(item.key) { android.os.SystemClock.elapsedRealtime() }
    val start=item.callStartedAtElapsed ?: fallbackStart
    var duration by remember(start) { mutableLongStateOf((android.os.SystemClock.elapsedRealtime()-start).coerceAtLeast(0)) }
    LaunchedEffect(start) { while(true) { duration=(android.os.SystemClock.elapsedRealtime()-start).coerceAtLeast(0);delay(1000) } }
    Row(modifier.height(37.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
        IslandText(clock(duration),13,weight=FontWeight.SemiBold,lines=1,modifier=Modifier.width(44.dp).semantics { contentDescription="Call duration ${clock(duration)}" })
        Spacer(Modifier.weight(1f))
        if(item.appIcon!=null) Image(item.appIcon.asImageBitmap(),"Dialer",Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)),contentScale=ContentScale.Crop)
        else Icon(Icons.Rounded.Call,"Dialer",Modifier.size(20.dp),tint=IslandGreen)
    }
}
