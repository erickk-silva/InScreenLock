package com.inscreenlock.app.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring

/** Shared expressive motion tuned for short, low-amplitude UI transitions. */
object InScreenLockMotion {
    fun <T> spatialSpring(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> effectsSpring(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)
    const val TabTransitionMillis = 200
}
