package com.gr4nt.dynamicisland

import androidx.compose.animation.core.*

/** Reference motion: quick response, soft settling, content follows the shell. */
object IslandMotion {
    private val ease=CubicBezierEasing(.22f,1f,.36f,1f)
    fun morph(): SpringSpec<Float> = spring(dampingRatio=.86f,stiffness=380f)
    fun close(): TweenSpec<Float> = tween(300,easing=ease)
    fun fadeIn(): TweenSpec<Float> = tween(180,easing=ease)
    fun fadeOut(): TweenSpec<Float> = tween(85,easing=LinearEasing)
    fun press(): TweenSpec<Float> = tween(90,easing=ease)
    fun <T> settle(): SpringSpec<T> = spring(dampingRatio=.86f,stiffness=380f)
    fun <T> exit(): TweenSpec<T> = tween(300,easing=ease)
    const val waveMillis=420
}
