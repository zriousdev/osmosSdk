package com.wikey.osmossdk

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver

class VisibilityTracker(
    private val view: View,
    private val threshold: Float = 0.5f,
    private val onVisible: () -> Unit
) {
    private var hasTriggered = false
    private var isStarted = false

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { checkVisibility() }
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { checkVisibility() }

    fun start() {
        if (isStarted) return
        isStarted = true
        
        // Add listeners to observe scroll changes and layout changes
        try {
            view.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
            view.viewTreeObserver.addOnScrollChangedListener(scrollListener)
        } catch (e: Exception) {
            // Safe fallback in case viewTreeObserver is not alive or throws
        }
        
        // Check immediately in case the view is already visible on layout
        view.post { checkVisibility() }
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        
        try {
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
                view.viewTreeObserver.removeOnScrollChangedListener(scrollListener)
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    fun reset() {
        hasTriggered = false
        if (isStarted) {
            view.post { checkVisibility() }
        }
    }

    private fun checkVisibility() {
        if (hasTriggered || !isStarted) return
        
        // The view must be attached to the window and its visibility must be VISIBLE
        if (!view.isAttachedToWindow || !view.isShown) return

        val viewWidth = view.width
        val viewHeight = view.height
        if (viewWidth <= 0 || viewHeight <= 0) return

        val rect = Rect()
        // getLocalVisibleRect checks if the view is clipped by its parents or screen edges.
        // It populates 'rect' with the visible portion of the view in its own coordinates.
        val isVisible = view.getLocalVisibleRect(rect)

        if (isVisible) {
            val visibleWidth = rect.width()
            val visibleHeight = rect.height()
            
            val totalArea = viewWidth.toLong() * viewHeight.toLong()
            val visibleArea = visibleWidth.toLong() * visibleHeight.toLong()

            if (totalArea > 0) {
                val visibilityRatio = visibleArea.toFloat() / totalArea.toFloat()
                if (visibilityRatio >= threshold) {
                    hasTriggered = true
                    onVisible()
                }
            }
        }
    }
}
