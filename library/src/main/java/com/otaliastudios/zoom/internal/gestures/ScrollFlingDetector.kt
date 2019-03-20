package com.otaliastudios.zoom.internal.gestures

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.OverScroller
import com.otaliastudios.zoom.AbsolutePoint
import com.otaliastudios.zoom.ZoomApi
import com.otaliastudios.zoom.ZoomEngine
import com.otaliastudios.zoom.ZoomLogger
import com.otaliastudios.zoom.internal.StateManager
import com.otaliastudios.zoom.internal.movement.PanManager

/**
 * Deals with scroll and fling gestures.
 *
 * - Detects them
 * - Checks state using [stateManager]
 * - Checks pan using [panManager]
 * - Applies updates using the [engine]
 */
internal class ScrollFlingDetector(
        context: Context,
        private val stateManager: StateManager,
        private val panManager: PanManager,
        private val engine: ZoomEngine) {

    private val detector = GestureDetector(context, Listener()).apply {
        setOnDoubleTapListener(null)
    }

    private val flingScroller = OverScroller(context)
    private val panStatusX = PanManager.Status()
    private val panStatusY = PanManager.Status()

    internal var flingEnabled = true
    internal var flingInOverPanEnabled = false

    internal fun maybeStart(event: MotionEvent): Boolean {
        return detector.onTouchEvent(event)
    }

    internal fun cancelFling() {
        flingScroller.forceFinished(true)
    }

    internal fun cancelScroll() {
        // If we are in over pan, animate back to a reasonable value. Otherwise,
        // just directly set state to idle.
        if (panManager.isOverPanEnabled) {
            val fix = panManager.correction
            if (fix.x != 0f || fix.y != 0f) {
                engine.animateScaledPan(fix.x, fix.y, true)
                return
            }
        }
        stateManager.makeIdle()
    }

    private fun startFling(@ZoomApi.ScaledPan velocityX: Int, @ZoomApi.ScaledPan velocityY: Int): Boolean {
        // Using actual pan values for the scroller.
        // Note: these won't make sense if zoom changes.
        panManager.computeStatus(true, panStatusX)
        panManager.computeStatus(false, panStatusY)
        @ZoomApi.ScaledPan val minX = panStatusX.minValue
        @ZoomApi.ScaledPan val startX = panStatusX.currentValue
        @ZoomApi.ScaledPan val maxX = panStatusX.maxValue
        @ZoomApi.ScaledPan val minY = panStatusY.minValue
        @ZoomApi.ScaledPan val startY = panStatusY.currentValue
        @ZoomApi.ScaledPan val maxY = panStatusY.maxValue
        if (!flingInOverPanEnabled && (panStatusX.isInOverPan || panStatusY.isInOverPan)) {
            // Only allow new flings while overscrolled if explicitly enabled as this might causes artifacts.
            return false
        }
        if (minX >= maxX && minY >= maxY && !panManager.isOverPanEnabled) {
            return false
        }
        // Must be after the other conditions.
        if (!stateManager.setFlinging()) return false

        @ZoomApi.ScaledPan val overScrollX = if (panManager.horizontalOverPanEnabled) panManager.maxOverPan else 0
        @ZoomApi.ScaledPan val overScrollY = if (panManager.verticalOverPanEnabled) panManager.maxOverPan else 0
        LOG.i("startFling", "velocityX:", velocityX, "velocityY:", velocityY)
        LOG.i("startFling", "flingX:", "min:", minX, "max:", maxX, "start:", startX, "overScroll:", overScrollY)
        LOG.i("startFling", "flingY:", "min:", minY, "max:", maxY, "start:", startY, "overScroll:", overScrollX)
        flingScroller.fling(startX, startY,
                velocityX, velocityY,
                minX, maxX, minY, maxY,
                overScrollX, overScrollY)

        engine.post(object : Runnable {
            override fun run() {
                if (flingScroller.isFinished) {
                    stateManager.makeIdle()
                } else if (flingScroller.computeScrollOffset()) {
                    @ZoomApi.ScaledPan val newPanX = flingScroller.currX
                    @ZoomApi.ScaledPan val newPanY = flingScroller.currY
                    // OverScroller will eventually go back to our bounds.
                    engine.applyScaledPan(
                            newPanX - engine.scaledPanX,
                            newPanY - engine.scaledPanY,
                            true)
                    engine.postOnAnimation(this)
                }
            }
        })
        return true
    }

    private inner class Listener : GestureDetector.SimpleOnGestureListener() {

        override fun onDown(e: MotionEvent): Boolean {
            return true // We are interested in the gesture.
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
            // If disabled, don't start the gesture.
            if (!flingEnabled) return false
            if (!panManager.isPanEnabled) return false
            val vX = (if (panManager.horizontalPanEnabled) velocityX else 0F).toInt()
            val vY = (if (panManager.verticalPanEnabled) velocityY else 0F).toInt()
            return startFling(vX, vY)
        }

        /**
         * Scroll event detected.
         *
         * We assume overScroll is true. If this is the case, it will be reset in [endScrollGesture].
         * If not, the [applyScaledPan] function will ignore our delta.
         *
         * TODO this this not true! ^
         */
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent?,
                              @ZoomApi.AbsolutePan distanceX: Float, @ZoomApi.AbsolutePan distanceY: Float): Boolean {
            if (!panManager.isPanEnabled) return false
            if (!stateManager.setScrolling()) return false

            // Change sign, since we work with opposite values.
            val delta = AbsolutePoint(-distanceX, -distanceY)

            // See if we are overscrolling.
            val panFix = panManager.correction

            // If we are overscrolling AND scrolling towards the overscroll direction...
            if (panFix.x < 0 && delta.x > 0 || panFix.x > 0 && delta.x < 0) {
                // Compute friction: a factor for distances. Must be 1 if we are not overscrolling,
                // and 0 if we are at the end of the available overscroll. This works:
                val overScrollX = Math.abs(panFix.x) / panManager.maxOverPan // 0 ... 1
                val frictionX = 0.6f * (1f - Math.pow(overScrollX.toDouble(), 0.4).toFloat()) // 0 ... 0.6
                LOG.i("onScroll", "applying friction X:", frictionX)
                delta.x *= frictionX
            }
            if (panFix.y < 0 && delta.y > 0 || panFix.y > 0 && delta.y < 0) {
                val overScrollY = Math.abs(panFix.y) / panManager.maxOverPan // 0 ... 1
                val frictionY = 0.6f * (1f - Math.pow(overScrollY.toDouble(), 0.4).toFloat()) // 0 ... 10.6
                LOG.i("onScroll", "applying friction Y:", frictionY)
                delta.y *= frictionY
            }

            // If disabled, reset to 0.
            if (!panManager.horizontalPanEnabled) delta.x = 0f
            if (!panManager.verticalPanEnabled) delta.y = 0f

            if (delta.x != 0f || delta.y != 0f) {
                engine.applyScaledPan(delta.x, delta.y, true)
            }
            return true
        }
    }

    companion object {
        private val TAG = ScrollFlingDetector::class.java.simpleName
        private val LOG = ZoomLogger.create(TAG)
    }
}