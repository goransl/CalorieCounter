package com.example.floating.caloriecounter

// SwipeToNavigate.kt (or place near your screen composables)
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun Modifier.swipeToNavigate(
    thresholdPx: Float = 100f,             // distance to count as a swipe
    onSwipeLeft: (() -> Unit)? = null,     // user moved left → go next
    onSwipeRight: (() -> Unit)? = null     // user moved right → go back
): Modifier {
    var totalDrag by remember { mutableStateOf(0f) }
    return this.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = { totalDrag = 0f },
            onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
            onDragEnd = {
                when {
                    totalDrag <= -thresholdPx -> onSwipeLeft?.invoke()   // drag left
                    totalDrag >=  thresholdPx -> onSwipeRight?.invoke()  // drag right
                }
                totalDrag = 0f
            }
        )
    }
}
