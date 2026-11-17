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
val LocalAccent=compositionLocalOf { Color(0xFF0A84FF) }
private val EqColors=listOf(Color(0xFFFF2D55),Color(0xFFFF9F0A),Color(0xFF30D158),Color(0xFF0A84FF))
private val Vivid=listOf(Color(0xFF0A84FF),Color(0xFFBF5AF2),Color(0xFFFF2D55),Color(0xFFFF9F0A),Color(0xFF30D158),Color(0xFF64D2FF))
/** Vivid accent from the notification art; falls back to a stable colour per app. */
private fun vibrant(bitmap: Bitmap?, seed: String): Color {
    val fallback=Vivid[(seed.hashCode() and 0x7fffffff)%Vivid.size]
    if(bitmap==null) return fallback
    return runCatching {
        val small=Bitmap.createScaledBitmap(bitmap.copy(Bitmap.Config.ARGB_8888,false),8,8,true)
        var bestScore=0f;var best=fallback;val hsv=FloatArray(3)
        for(x in 0 until 8) for(y in 0 until 8) {
            val px=small.getPixel(x,y); if(android.graphics.Color.alpha(px)<128) continue
            android.graphics.Color.colorToHSV(px,hsv); val score=hsv[1]*hsv[2]
            if(score>bestScore) { bestScore=score;best=Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0],hsv[1].coerceAtLeast(.6f),1f))) }
        }
        if(bestScore<.15f) fallback else best
    }.getOrDefault(fallback)
}
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
    CompositionLocalProvider(LocalDensity provides scaled,LocalGlassPalette provides GlassPalette(true)) { OverlayContent(state,layout) }
}
@Composable private fun OverlayContent(state: IslandState, layout: IslandLayout) {
    var retained by remember { mutableStateOf(state.item) }
    val opening=remember { Animatable(0f) }
    val drag=remember { Animatable(0f) }
    val reveal=remember { Animatable(0f) }
    val scope=rememberCoroutineScope()
    LaunchedEffect(state.item?.key) {
        if(state.item != null) { retained=state.item;reveal.snapTo(0f);launch { reveal.animateTo(1f,tween(160,easing=IosEase)) };opening.animateTo(1f,spring(.7f,800f)) }
        else { opening.animateTo(0f,tween(200,easing=CubicBezierEasing(.4f,0f,.2f,1f)));retained=null }
    }
    val item=state.item ?: retained
    if(item == null) { Spacer(Modifier.size(1.dp));return }
    val compact=(item.media != null || item.status != null || (item.call && !item.incoming)) && !state.expanded
    val pillWidth=if(item.status != null)236.dp else if(item.call)144.dp else 108.dp
    var lastSecondary by remember { mutableStateOf<IslandItem?>(null) }
    if(state.secondary!=null) lastSecondary=state.secondary
    BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.TopCenter) {
        val cardWidth=maxWidth
        val shellWidth by animateDpAsState(if(compact)pillWidth else cardWidth,spring(.74f,750f),label="shell width")
        val widthProgress=if(cardWidth>pillWidth) ((shellWidth-pillWidth)/(cardWidth-pillWidth)).coerceIn(0f,1f) else 1f
        val corners=RoundedCornerShape(layout.topLeft.dp,layout.topRight.dp,layout.bottomRight.dp,layout.bottomLeft.dp)
        val contentAlpha=minOf(((opening.value-.4f)/.6f).coerceIn(0f,1f),reveal.value)
        Box(Modifier.width(shellWidth).graphicsLayer {
            transformOrigin=TransformOrigin(.5f,0f)
            scaleX=.22f+.78f*opening.value
            scaleY=.12f+.88f*opening.value
            alpha=(opening.value*4).coerceIn(0f,1f)
            translationY=drag.value-6.dp.toPx()*(1-opening.value)
        }.softGlass(corners,overlay=true,black=true)
            .animateContentSize(spring(.76f,750f))
            .draggable(rememberDraggableState { delta -> scope.launch { drag.snapTo((drag.value+delta).coerceAtMost(0f)) } },Orientation.Vertical,
                onDragStopped={ if(drag.value < -40f) { IslandController.dismiss();drag.snapTo(0f) } else drag.animateTo(0f,spring(.75f,800f)) })
            .clickable(enabled=!state.replying) { IslandController.tap() }
        ) {
            // Fake frost uses notification art, never a rectangular live backdrop blur.
            val art=if(item.call) item.avatar else item.largeIcon ?: item.appIcon
            val accent=remember(item.key) { vibrant(item.largeIcon ?: item.avatar ?: item.appIcon,item.app) }
            CompositionLocalProvider(LocalAccent provides accent) { Box(Modifier.blur((16f*(1f-contentAlpha)).dp,BlurredEdgeTreatment.Unbounded).graphicsLayer { alpha=contentAlpha;translationY=8.dp.toPx()*(1-contentAlpha);scaleX=.86f+.14f*contentAlpha;scaleY=scaleX }) {
                if(item.media!=null && !item.call) {
                    // Each state measures at its final width, even while the shell is narrow.
                    Box(contentAlignment=Alignment.TopCenter) {
                        if(widthProgress < .99f) MusicPill(item,Modifier.requiredWidth(108.dp).alpha(1-widthProgress))
                        if(widthProgress > .01f) Column(Modifier.requiredWidth(cardWidth).alpha(widthProgress)) {
                            CameraBand(layout)
                            MusicCard(item,Modifier.padding(horizontal=20.dp,vertical=8.dp))
                        }
                    }
                } else if(item.status!=null) Box(contentAlignment=Alignment.TopCenter) {
                    if(widthProgress < .99f) StatusPill(item.status,Modifier.requiredWidth(pillWidth).alpha(1-widthProgress))
                    if(widthProgress > .01f) Column(Modifier.requiredWidth(cardWidth).alpha(widthProgress)) { CameraBand(layout);StatusCard(item.status) }
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
            } }
        }
        androidx.compose.animation.AnimatedVisibility(compact && state.secondary!=null,Modifier.align(Alignment.TopCenter).offset(x=shellWidth/2+25.dp),
            enter=androidx.compose.animation.scaleIn(spring(.6f,600f),initialScale=.2f)+androidx.compose.animation.fadeIn(tween(120)),
            exit=androidx.compose.animation.scaleOut(tween(140),targetScale=.2f)+androidx.compose.animation.fadeOut(tween(120))) {
            lastSecondary?.let { SideDot(it,Modifier) }
        }
    }
}
@Composable private fun CameraBand(layout: IslandLayout) {
    // Camera dimensions remain physical when the rest of the island is scaled.
    val context=LocalContext.current
    val rect=context.getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.windowInsets.displayCutout?.boundingRectTop
    val physicalDensity=context.resources.displayMetrics.density
    val cameraHeight=(rect?.width()?.div(physicalDensity) ?: 26f)/(layout.size/100f)
    Spacer(Modifier.height((cameraHeight*.78f+layout.cameraSpace/2f+layout.gap).coerceAtLeast(0f).dp))
}
@Composable private fun Countdown(generation: Long, seconds: Float) {
    val remaining=remember { Animatable(1f) }
    LaunchedEffect(generation,seconds) { remaining.snapTo(1f);remaining.animateTo(0f,tween((seconds*1000).toInt(),easing=LinearEasing)) }
    Box(Modifier.fillMaxWidth().height(2.dp).background(LocalGlassPalette.current.text.copy(alpha=.08f))) { Box(Modifier.fillMaxWidth(remaining.value).fillMaxHeight().background(LocalAccent.current)) }
}
@Composable fun IslandText(text: String, size: Int=14, color: Color=LocalGlassPalette.current.text, weight: FontWeight=FontWeight.Medium, lines: Int=2, modifier: Modifier=Modifier) {
    Text(text,modifier,color=color,fontSize=size.sp,fontWeight=weight,letterSpacing=(-.3).sp,maxLines=lines,overflow=TextOverflow.Ellipsis,lineHeight=(size+4).sp)
}
@Composable private fun Art(bitmap: Bitmap?, size: Dp, modifier: Modifier=Modifier) {
    if(bitmap!=null) Image(bitmap.asImageBitmap(),null,modifier.size(size).clip(RoundedCornerShape(size/4)),contentScale=ContentScale.Crop)
    else Box(modifier.size(size).clip(RoundedCornerShape(size/4)).background(Color(0xFF30343D)),contentAlignment=Alignment.Center) { Icon(Icons.Rounded.MusicNote,null,Modifier.size(size*.6f),tint=Secondary) }
}
@Composable private fun MessageCard(item: IslandItem, replying: Boolean, modifier: Modifier) {
    Column(modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Art(item.appIcon,16.dp);IslandText(item.app,12,LocalAccent.current,FontWeight.SemiBold,lines=1);IslandText("· now",12,Secondary)
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            if(item.largeIcon!=null && item.code==null) Image(item.largeIcon.asImageBitmap(),null,Modifier.size(44.dp).clip(CircleShape),contentScale=ContentScale.Crop)
            Column(Modifier.weight(1f,fill=false),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(item.title,color=Color.White,fontSize=17.sp,fontWeight=FontWeight.SemiBold,textAlign=androidx.compose.ui.text.style.TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis,letterSpacing=(-.3).sp)
                if(item.code!=null) Text(item.code,color=LocalAccent.current,fontSize=28.sp,fontWeight=FontWeight.Bold,letterSpacing=3.sp)
                else Text(item.text,color=Secondary,fontSize=14.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis,letterSpacing=(-.3).sp)
            }
        }
        if(replying) ReplyField(item)
        else if(item.code!=null) ActionPill("Copy code",Icons.Rounded.ContentCopy) { IslandController.copyCode() }
        else if(item.actions.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            item.actions.forEach { action ->
                val kind=if(action.reply) "reply" else action.kind
                val vector=when(kind) { "reply"->ReplyVector;"read"->ReadVector;"delete"->Icons.Rounded.DeleteOutline;"archive"->Icons.Rounded.Archive;else->null }
                if(kind=="reply" || kind=="read") RoundButton(action.title.ifBlank { kind },vector ?: ReplyVector,if(kind=="reply")Color(0xFF0A84FF) else Color(0xFFBF5AF2),38.dp) { IslandController.action(action) }
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
            textStyle=TextStyle(color=LocalGlassPalette.current.text,fontSize=15.sp),cursorBrush=SolidColor(LocalAccent.current),maxLines=4,
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
                val pulse=rememberInfiniteTransition(label="ring").animateFloat(1f,1.14f,infiniteRepeatable(tween(900,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="pulse")
                val ring=Brush.sweepGradient(listOf(Color(0xFF30D158),Color(0xFF0A84FF),Color(0xFFBF5AF2),Color(0xFFFF2D55),Color(0xFF30D158)))
                Box(Modifier.size(46.dp).graphicsLayer { if(item.incoming){scaleX=pulse.value;scaleY=pulse.value} }.border(2.dp,ring,CircleShape).padding(3.dp),contentAlignment=Alignment.Center) {
                if(item.avatar!=null) Image(item.avatar.asImageBitmap(),null,Modifier.fillMaxSize().clip(CircleShape),contentScale=ContentScale.Crop)
                else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF5E5CE6),Color(0xFFBF5AF2))),CircleShape),contentAlignment=Alignment.Center) {
                    val initial=item.title.trim().firstOrNull()?.takeIf { it.isLetter() }
                    if(initial!=null) IslandText(initial.uppercase(),20,weight=FontWeight.SemiBold) else Icon(Icons.Rounded.Person,null,tint=Color.White)
                } }
                Column(Modifier.weight(1f)) { if(item.incoming) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)) { Icon(Icons.Rounded.PhoneAndroid,null,Modifier.size(11.dp),tint=Secondary);IslandText("mobile",11,Secondary,lines=1) };IslandText(item.title,17,weight=FontWeight.SemiBold,lines=1);IslandText(if(item.incoming) "Incoming Call" else "Ongoing Call",13,if(item.incoming)IslandGreen else Secondary,lines=2) }
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
        else Icon(Icons.Rounded.MusicNote,null,Modifier.size(18.dp),tint=LocalAccent.current)
        // This transparent middle section sits directly over the physical lens.
        Spacer(Modifier.width(30.dp))
        val transition=rememberInfiniteTransition(label="levels")
        Row(horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically) {
            repeat(4) { i ->
                val h by transition.animateFloat(3f+(i%2)*3,14f-(i%3)*2,infiniteRepeatable(tween(350+i*80,easing=LinearEasing),RepeatMode.Reverse),label="level $i")
                Box(Modifier.width(2.5.dp).height(if(item.media?.playing==true) h.dp else 3.dp).clip(CircleShape).background(EqColors[i%EqColors.size]))
            }
        }
    }
}
@Composable private fun MusicCard(item: IslandItem,modifier: Modifier) {
    val media=item.media ?: return
    var position by remember(item.key) { mutableLongStateOf(media.elapsed()) }
    LaunchedEffect(media) { while(true) { position=media.elapsed();delay(500) } }
    Column(modifier,verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Art(item.largeIcon,56.dp)
            Column(Modifier.weight(1f)) { Text(item.title,color=Color.White,fontSize=16.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(item.text,color=Secondary,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis) }
            val t=rememberInfiniteTransition(label="musicwave")
            Row(horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically) {
                repeat(5) { i ->
                    val h by t.animateFloat(5f+(i%3)*2,20f-(i%2)*4,infiniteRepeatable(tween(330+i*80,easing=LinearEasing),RepeatMode.Reverse),label="mw$i")
                    Box(Modifier.width(2.5.dp).height(if(media.playing) h.dp else 4.dp).clip(CircleShape).background(LocalAccent.current))
                }
            }
        }
        val fraction=if(media.duration>0) (position.toFloat()/media.duration).coerceIn(0f,1f) else 0f
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha=.22f))) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Color.White.copy(alpha=.85f)))
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(clock(position),color=Secondary,fontSize=11.sp)
                Text(if(media.duration>0) "-"+clock(media.duration-position) else "",color=Secondary,fontSize=11.sp)
            }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
            val controls=item.actions.filterNot { it.reply }.filter { Regex("(?i)prev|back|rewind|play|pause|next|forward").containsMatchIn(it.title) }.take(3)
            if(controls.size==3) controls.forEachIndexed { i,a -> NotificationIconButton(a,if(i==1)54.dp else 44.dp) { IslandController.action(a) } }
            else {
                PlainControl(Icons.Rounded.FastRewind,"Previous",44.dp) { media.controller?.transportControls?.skipToPrevious() }
                PlainControl(if(media.playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,if(media.playing)"Pause" else "Play",54.dp) { if(media.playing)media.controller?.transportControls?.pause() else media.controller?.transportControls?.play() }
                PlainControl(Icons.Rounded.FastForward,"Next",44.dp) { media.controller?.transportControls?.skipToNext() }
            }
            PlainControl(Icons.Rounded.Cast,"Output",36.dp) { IslandController.openOutputSwitcher() }
        }
    }
}
@Composable private fun PlainControl(vector: ImageVector,label: String,size: Dp,click: ()->Unit) {
    Box(Modifier.size(size).iosClickable(pressedScale=.86f,onClick=click),contentAlignment=Alignment.Center) { Icon(vector,label,Modifier.size(size*.78f),tint=Color.White) }
}
private fun clock(ms: Long): String { val seconds=(ms/1000).coerceAtLeast(0);return "%d:%02d".format(seconds/60,seconds%60) }
@Composable private fun iconBitmap(icon: AndroidIcon?): Bitmap? {
    val c=LocalContext.current
    return remember(icon) { runCatching { icon?.loadDrawable(c)?.toBitmap(64,64,Bitmap.Config.ARGB_8888)?.copy(Bitmap.Config.ARGB_8888,false) }.getOrNull() }
}
@Composable private fun NotificationIconButton(a: IslandAction, size: Dp, click: ()->Unit) {
    val b=iconBitmap(a.icon)
    Box(Modifier.size(size).iosClickable(pressedScale=.86f,onClick=click),contentAlignment=Alignment.Center) {
        if(b!=null) Image(b.asImageBitmap(),a.title,Modifier.size(size*.7f),colorFilter=ColorFilter.tint(Color.White))
        else Icon(if(a.title.contains("pause",true))Icons.Rounded.Pause else if(a.title.contains("next",true))Icons.Rounded.FastForward else if(a.title.contains("prev",true))Icons.Rounded.FastRewind else Icons.Rounded.PlayArrow,a.title,tint=Color.White)
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

@Composable private fun CallPill(item: IslandItem, modifier: Modifier) {
    val fallbackStart=remember(item.key) { android.os.SystemClock.elapsedRealtime() }
    val start=item.callStartedAtElapsed ?: fallbackStart
    var duration by remember(start) { mutableLongStateOf((android.os.SystemClock.elapsedRealtime()-start).coerceAtLeast(0)) }
    LaunchedEffect(start) { while(true) { duration=(android.os.SystemClock.elapsedRealtime()-start).coerceAtLeast(0);delay(1000) } }
    Row(modifier.height(37.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(Icons.Rounded.Call,"Call",Modifier.size(15.dp),tint=IslandGreen)
        Spacer(Modifier.width(4.dp))
        Text(clock(duration),color=IslandGreen,fontSize=13.sp,fontWeight=FontWeight.SemiBold,maxLines=1,modifier=Modifier.semantics { contentDescription="Call duration ${clock(duration)}" })
        Spacer(Modifier.weight(1f))
        val t=rememberInfiniteTransition(label="callwave")
        Row(horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically) {
            repeat(5) { i ->
                val h by t.animateFloat(4f+(i%3)*2,15f-(i%2)*3,infiniteRepeatable(tween(320+i*70,easing=LinearEasing),RepeatMode.Reverse),label="cw$i")
                Box(Modifier.width(2.5.dp).height(h.dp).clip(CircleShape).background(IslandGreen))
            }
        }
    }
}

private fun statusColor(argb: Int) = Color(argb)
@Composable private fun BatteryGlyph(level: Int, color: Color) {
    Canvas(Modifier.size(26.dp,12.dp)) {
        val bodyW=size.width-3.dp.toPx();val r=androidx.compose.ui.geometry.CornerRadius(3.5.dp.toPx())
        drawRoundRect(color.copy(alpha=.4f),size=androidx.compose.ui.geometry.Size(bodyW,size.height),cornerRadius=r,style=androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
        val pad=2.dp.toPx();val w=((bodyW-pad*2)*(level.coerceIn(0,100)/100f)).coerceAtLeast(2.dp.toPx())
        drawRoundRect(color,topLeft=androidx.compose.ui.geometry.Offset(pad,pad),size=androidx.compose.ui.geometry.Size(w,size.height-pad*2),cornerRadius=androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
        drawRoundRect(color.copy(alpha=.5f),topLeft=androidx.compose.ui.geometry.Offset(bodyW+1.dp.toPx(),size.height*.32f),size=androidx.compose.ui.geometry.Size(1.6.dp.toPx(),size.height*.36f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
    }
}
@Composable private fun StatusLeading(s: StatusInfo) {
    when(s.kind) {
        "silent" -> Box(Modifier.size(34.dp,24.dp).clip(CircleShape).background(Color(0xFFFF3B30)),contentAlignment=Alignment.Center) { Icon(Icons.Rounded.NotificationsOff,"Silent",Modifier.size(17.dp),tint=Color.White) }
        "ring" -> Icon(Icons.Rounded.Notifications,"Ring",Modifier.size(19.dp).padding(start=2.dp),tint=Color.White)
        "vibrate" -> Icon(Icons.Rounded.Vibration,"Vibrate",Modifier.size(20.dp).padding(start=2.dp),tint=Color.White)
        "headset" -> Icon(Icons.Rounded.Headphones,"Headphones",Modifier.size(20.dp).padding(start=2.dp),tint=Color.White)
        "hotspot" -> Icon(Icons.Rounded.WifiTethering,"Hotspot",Modifier.size(20.dp).padding(start=2.dp),tint=statusColor(s.color))
        "timer" -> Icon(Icons.Rounded.Timer,"Timer",Modifier.size(21.dp).padding(start=2.dp),tint=statusColor(s.color))
        "nav" -> Icon(Icons.Rounded.Navigation,"Directions",Modifier.size(21.dp).padding(start=2.dp),tint=statusColor(s.color))
        "record" -> {
            val t=rememberInfiniteTransition(label="rec")
            Row(Modifier.padding(start=4.dp),horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically) {
                repeat(6) { i -> val h by t.animateFloat(4f+(i%3)*2,16f-(i%2)*4,infiniteRepeatable(tween(300+i*70,easing=LinearEasing),RepeatMode.Reverse),label="rc$i"); Box(Modifier.width(2.5.dp).height(h.dp).clip(CircleShape).background(statusColor(s.color))) }
            }
        }
        "unlock" -> {
            var open by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(260);open=true }
            val pop by animateFloatAsState(if(open)1f else .86f,spring(.45f,500f),label="unlock")
            Icon(if(open)Icons.Rounded.LockOpen else Icons.Rounded.Lock,"Unlocked",Modifier.size(19.dp).padding(start=2.dp).graphicsLayer { scaleX=pop;scaleY=pop },tint=Color.White)
        }
        "dnd" -> Icon(Icons.Rounded.Bedtime,"Do Not Disturb",Modifier.size(20.dp).padding(start=2.dp),tint=statusColor(s.color))
        else -> Text(s.label,color=Color.White,fontSize=14.sp,fontWeight=FontWeight.Medium,maxLines=1,modifier=Modifier.padding(start=4.dp))
    }
}
@Composable private fun StatusTrailing(s: StatusInfo) {
    val color=if(s.kind=="ring"||s.kind=="vibrate"||s.kind=="headset"||s.kind=="nav") Color.White else statusColor(s.color)
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        if(s.chrono) LiveTime(s,color) else Text(s.value,color=color,fontSize=if(s.kind=="nav")13.sp else 14.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.widthIn(max=110.dp))
        if(s.level>=0) BatteryGlyph(s.level,statusColor(s.color))
    }
}
@Composable private fun StatusPill(s: StatusInfo, modifier: Modifier) {
    Row(modifier.height(37.dp).padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically) {
        StatusLeading(s);Spacer(Modifier.weight(1f));StatusTrailing(s)
    }
}
@Composable private fun StatusCard(s: StatusInfo) {
    Column(Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(s.kind=="nav") Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Navigation,"Directions",Modifier.size(30.dp),tint=statusColor(s.color))
            Column { Text(s.label,color=Color.White,fontSize=17.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis);if(s.value.isNotBlank()) Text(s.value,color=Secondary,fontSize=14.sp,maxLines=1) }
        } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { if(s.kind=="timer"||s.kind=="record") Text(s.label,color=Color.White,fontSize=16.sp,fontWeight=FontWeight.SemiBold) else StatusLeading(s) }
            StatusTrailing(s)
        }
        if(s.action=="battery_saver") Box(Modifier.fillMaxWidth().height(46.dp).iosClickable { IslandController.openBatterySaver() }.clip(CircleShape).background(Color(0xFF2C2C2E)),contentAlignment=Alignment.Center) {
            Text("Battery Saver",color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Medium)
        }
    }
}

@Composable private fun LiveTime(s: StatusInfo, color: Color) {
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(s.baseElapsed) { while(true) { now=android.os.SystemClock.elapsedRealtime();delay(250) } }
    val ms=(if(s.countdown) s.baseElapsed-now else now-s.baseElapsed).coerceAtLeast(0)
    val total=ms/1000
    val text=if(total>=3600) "%d:%02d:%02d".format(total/3600,total%3600/60,total%60) else "%02d:%02d".format(total/60,total%60)
    Text(text,color=color,fontSize=15.sp,fontWeight=FontWeight.Medium,maxLines=1)
}
/** Small round island beside the main pill, like the iPhone's second live activity. */
@Composable private fun SideDot(item: IslandItem,modifier: Modifier) {
    Box(modifier.size(37.dp).iosClickable { IslandController.swap() }.softGlass(CircleShape,overlay=true,black=true),contentAlignment=Alignment.Center) {
        val kind=item.status?.kind
        when {
            item.call -> Icon(Icons.Rounded.Call,"Call",Modifier.size(18.dp),tint=IslandGreen)
            kind=="timer" -> Icon(Icons.Rounded.Timer,"Timer",Modifier.size(19.dp),tint=Color(0xFFFF9F0A))
            kind=="record" -> Icon(Icons.Rounded.Mic,"Recording",Modifier.size(19.dp),tint=Color(0xFFFF3B30))
            kind=="nav" -> Icon(Icons.Rounded.Navigation,"Directions",Modifier.size(19.dp),tint=Color(0xFF64D2FF))
            item.largeIcon!=null -> Image(item.largeIcon.asImageBitmap(),null,Modifier.size(26.dp).clip(CircleShape),contentScale=ContentScale.Crop)
            else -> Icon(Icons.Rounded.MusicNote,"Music",Modifier.size(18.dp),tint=LocalAccent.current)
        }
    }
}
