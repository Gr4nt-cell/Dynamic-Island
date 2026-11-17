package com.gr4nt.dynamicisland

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/** Material values follow Fast-score's Glass.js and theme.js, not a live screen capture. */
data class GlassPalette(val dark: Boolean) {
    val text = if(dark) Color.White else Color(0xFF1D2428)
    val secondary = if(dark) Color(0xFFA4ADA9) else Color(0xFF5B6862)
    val accent = if(dark) Color(0xFFB6DEC9) else Color(0xFF35694E)
    val fill = if(dark) Color(0xFF26292F).copy(alpha=.34f) else Color(0xFFFCFDFE).copy(alpha=.22f)
    // Islands sit above arbitrary apps, so they need a denser frost for reliable text contrast.
    val overlayFill = if(dark) Color(0xFF16191D).copy(alpha=.9f) else Color(0xFFF6FAF8).copy(alpha=.9f)
    val rim = Color.White.copy(alpha=if(dark).24f else .84f)
    val track = if(dark) Color.White.copy(alpha=.12f) else Color(0xFF14271F).copy(alpha=.1f)
}
val LocalGlassPalette=staticCompositionLocalOf { GlassPalette(true) }
val Secondary: Color @Composable get()=LocalGlassPalette.current.secondary
val SettingsAccent: Color @Composable get()=LocalGlassPalette.current.accent
val IosEase=CubicBezierEasing(.22f,1f,.36f,1f)

@Composable fun IslandTheme(forceDark: Boolean?=null, content: @Composable ()->Unit) {
    val dark=forceDark ?: isSystemInDarkTheme()
    val p=remember(dark) { GlassPalette(dark) }
    CompositionLocalProvider(LocalGlassPalette provides p) {
        MaterialTheme(colorScheme=if(dark) darkColorScheme(primary=p.accent,onPrimary=Color(0xFF15241C),background=Color.Black,surface=Color(0xFF191D20),onSurface=p.text,onSurfaceVariant=p.secondary)
            else lightColorScheme(primary=p.accent,onPrimary=Color.White,background=Color(0xFFE6EEE9),surface=Color(0xFFF5F9F6),onSurface=p.text,onSurfaceVariant=p.secondary),content=content)
    }
}

/** Neutral rim only: no app-colour keyline and no elevation shadow on overlay windows. */
fun Modifier.softGlass(shape: Shape=RoundedCornerShape(26.dp), overlay: Boolean=false, tint: Color?=null): Modifier = composed {
    val p=LocalGlassPalette.current
    this.clip(shape).drawWithCache {
        val outline=shape.createOutline(size,layoutDirection,this)
        val fill=tint?.copy(alpha=.80f) ?: if(overlay) p.overlayFill else p.fill
        val sheen=Brush.verticalGradient(listOf(Color.White.copy(alpha=if(p.dark).14f else .36f),Color.Transparent),endY=size.height*.65f)
        val lower=Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=if(p.dark).16f else .025f)))
        val edge=Brush.verticalGradient(listOf(Color.White.copy(alpha=if(p.dark).32f else .96f),p.rim.copy(alpha=if(p.dark).10f else .38f),p.rim.copy(alpha=if(p.dark).16f else .54f)))
        onDrawWithContent {
            drawOutline(outline,fill)
            drawRect(sheen)
            drawRect(lower)
            drawContent()
            drawOutline(outline,edge,style=Stroke(.8.dp.toPx()))
        }
    }
}

fun Modifier.iosClickable(enabled: Boolean=true, pressedScale: Float=.96f, onClick: ()->Unit): Modifier = composed {
    val interaction=remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed && enabled) pressedScale else 1f,
        if(pressed) tween(90,easing=FastOutSlowInEasing) else spring(.72f,500f),label="iOS press")
    this.graphicsLayer { scaleX=scale;scaleY=scale;alpha=if(enabled)1f else .44f }
        .clickable(enabled=enabled,interactionSource=interaction,indication=null,onClick=onClick)
}
fun Modifier.iosReveal(delayMillis: Long=0): Modifier = composed {
    val progress=remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(delayMillis);progress.animateTo(1f,spring(.86f,420f)) }
    this.graphicsLayer { alpha=progress.value.coerceIn(0f,1f);translationY=18.dp.toPx()*(1-progress.value);scaleX=.985f+.015f*progress.value;scaleY=scaleX }
}

@Composable fun GlassBackdrop(modifier: Modifier=Modifier) {
    val p=LocalGlassPalette.current
    if(p.dark) Box(modifier.background(Color.Black))
    else Image(painterResource(R.drawable.pearl_mist),null,modifier,contentScale=ContentScale.Crop)
}

@Composable fun GlassButton(label: String, modifier: Modifier=Modifier, icon: ImageVector?=null, enabled: Boolean=true, onClick: ()->Unit) {
    Row(modifier.heightIn(min=48.dp).iosClickable(enabled,onClick=onClick).softGlass(CircleShape).padding(horizontal=20.dp,vertical=13.dp),
        horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
        if(icon!=null) { Icon(icon,null,Modifier.size(20.dp),tint=SettingsAccent);Spacer(Modifier.width(8.dp)) }
        IslandText(label,15,SettingsAccent,androidx.compose.ui.text.font.FontWeight.SemiBold,lines=1)
    }
}
@Composable fun IosSwitch(checked: Boolean, enabled: Boolean=true, label: String?=null, change: (Boolean)->Unit) {
    val p=LocalGlassPalette.current
    val offset by animateDpAsState(if(checked)22.dp else 2.dp,spring(.82f,600f),label="switch position")
    val colour by animateColorAsState(if(checked)Color(0xFF34C759).copy(alpha=.8f) else if(p.dark)Color(0xFF555A58) else Color(0xFFA8B4AD),tween(180),label="switch tint")
    Box(Modifier.size(51.dp,31.dp).semantics { role=Role.Switch;if(label!=null)contentDescription=label;toggleableState=if(checked)ToggleableState.On else ToggleableState.Off }
        .iosClickable(enabled,pressedScale=.96f) { change(!checked) }.softGlass(CircleShape,tint=colour),contentAlignment=Alignment.CenterStart) {
        Box(Modifier.offset(x=offset).size(27.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Color.White,Color(0xFFE9EFEB)))).border(.6.dp,Color.White.copy(alpha=.85f),CircleShape))
    }
}

/** Shared frosted dialogs, including consent and app selection. Content stays editable/accessible. */
@Composable fun GlassDialog(title: String, dismiss: ()->Unit, confirmLabel: String, confirm: ()->Unit,
    dismissLabel: String?="Cancel", body: @Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.padding(24.dp).widthIn(max=420.dp).fillMaxWidth().iosReveal().softGlass(RoundedCornerShape(34.dp),overlay=true).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            IslandText(title,21,weight=androidx.compose.ui.text.font.FontWeight.SemiBold,lines=3)
            body()
            GlassButton(confirmLabel,Modifier.fillMaxWidth(),onClick=confirm)
            if(dismissLabel!=null) GlassButton(dismissLabel,Modifier.fillMaxWidth(),onClick=dismiss)
        }
    }
}
