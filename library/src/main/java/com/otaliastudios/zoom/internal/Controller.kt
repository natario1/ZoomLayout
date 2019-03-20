package com.otaliastudios.zoom.internal

import android.view.MotionEvent
import androidx.annotation.IntDef
import com.otaliastudios.zoom.ZoomLogger

/**
 * Deals with touch input, holds the internal [state] integer,
 * and applies special logic to touch inputs and state changes to
 * prefer one gesture over the other, for example.
 */
internal class Controller(private val callback: Callback) {

    internal interface Callback {
        // State callbacks
        fun isStateAllowed(@State newState: Int): Boolean
        fun onStateIdle()
        fun cleanupState(@State oldState: Int)

        // Touch callbacks
        fun maybeStartPinchGesture(event: MotionEvent): Boolean
        fun maybeStartScrollFlingGesture(event: MotionEvent): Boolean
        fun endScrollGesture()
    }

    @State
    internal var state: Int = NONE
        private set

    private fun setState(newState: Int): Boolean {
        LOG.v("trySetState:", newState.toStateName())
        if (!callback.isStateAllowed(newState)) return false

        // we need to do some cleanup in case of ANIMATING so we can't return just yet
        if (newState == state && newState != ANIMATING) return true
        val oldState = state

        when (newState) {
            SCROLLING -> if (oldState == PINCHING || oldState == ANIMATING) return false
            FLINGING -> if (oldState == ANIMATING) return false
            PINCHING -> if (oldState == ANIMATING) return false
            NONE -> callback.onStateIdle()
        }

        // Now that it succeeded, do some cleanup.
        callback.cleanupState(oldState)
        LOG.i("setState:", newState.toStateName())
        state = newState
        return true
    }

    internal fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return processTouchEvent(ev) > TOUCH_LISTEN
    }

    internal fun onTouchEvent(ev: MotionEvent): Boolean {
        return processTouchEvent(ev) > TOUCH_NO
    }

    private fun processTouchEvent(event: MotionEvent): Int {
        LOG.v("processTouchEvent:", "start.")
        if (isAnimating()) return TOUCH_STEAL

        var result = callback.maybeStartPinchGesture(event)
        LOG.v("processTouchEvent:", "scaleResult:", result)

        // Pinch detector always returns true. If we actually started a pinch,
        // Don't pass to fling detector.
        if (!isPinching()) {
            result = result or callback.maybeStartScrollFlingGesture(event)
            LOG.v("processTouchEvent:", "flingResult:", result)
        }

        // Detect scroll ends, this appears to be the only way.
        if (isScrolling()) {
            val a = event.actionMasked
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                LOG.i("processTouchEvent:", "up event while scrolling, dispatching endScrollGesture.")
                // We are not simply calling makeIdle() because we might be in overpan.
                // In that case, the Callback will animate back to legit position.
                callback.endScrollGesture()
            }
        }

        if (result && !isIdle()) {
            LOG.v("processTouchEvent:", "returning: TOUCH_STEAL")
            return TOUCH_STEAL
        } else if (result) {
            LOG.v("processTouchEvent:", "returning: TOUCH_LISTEN")
            return TOUCH_LISTEN
        } else {
            LOG.v("processTouchEvent:", "returning: TOUCH_NO")
            makeIdle()
            return TOUCH_NO
        }
    }
    
    internal fun isFlinging() = state == FLINGING

    internal fun isScrolling() = state == SCROLLING

    internal fun isPinching() = state == PINCHING

    internal fun isAnimating() = state == ANIMATING

    internal fun isIdle() = state == NONE

    internal fun setFlinging() = setState(FLINGING)

    internal fun setScrolling() = setState(SCROLLING)

    internal fun setPinching() = setState(PINCHING)

    internal fun setAnimating() = setState(ANIMATING)

    internal fun makeIdle() = setState(NONE)

    private fun Int.toStateName(): String {
        return when (this) {
            NONE -> "NONE"
            FLINGING -> "FLINGING"
            SCROLLING -> "SCROLLING"
            PINCHING -> "PINCHING"
            ANIMATING -> "ANIMATING"
            else -> ""
        }
    }

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(NONE, SCROLLING, PINCHING, ANIMATING, FLINGING)
    internal annotation class State

    companion object {
        private val TAG = Controller::class.java.simpleName
        private val LOG = ZoomLogger.create(TAG)

        internal const val NONE = 0
        internal const val SCROLLING = 1
        internal const val PINCHING = 2
        internal const val ANIMATING = 3
        internal const val FLINGING = 4
        
        // Might make these public some day?
        private const val TOUCH_NO = 0
        private const val TOUCH_LISTEN = 1
        private const val TOUCH_STEAL = 2
    }
}