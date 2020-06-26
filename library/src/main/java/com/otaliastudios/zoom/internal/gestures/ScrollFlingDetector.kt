package com.otaliastudios.zoom.internal.gestures

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.OverScroller
import com.otaliastudios.zoom.ScaledPoint
import com.otaliastudios.zoom.ZoomApi
import com.otaliastudios.zoom.ZoomLogger
import com.otaliastudios.zoom.internal.StateController
import com.otaliastudios.zoom.internal.matrix.MatrixController
import com.otaliastudios.zoom.internal.movement.PanManager
import kotlin.math.abs
import kotlin.math.pow

/**
 * Deals with scroll and fling gestures.
 *
 * - Detects them
 * - Checks state using [stateController]
 * - Checks pan using [panManager]
 * - Applies updates using the [matrixController]
 */
internal class ScrollFlingDetector(
        context: Context,
        private val panManager: PanManager,
        private val stateController: StateController,
        private val matrixController: MatrixController
) : GestureDetector.OnGestureListener {

    private val detector = GestureDetector(context, this).apply {
        setOnDoubleTapListener(null)
    }

    private val flingScroller = OverScroller(context)
    private val panStatusX = PanManager.Status()
    private val panStatusY = PanManager.Status()

    internal var flingEnabled = true
    internal var scrollEnabled = true
    internal var oneFingerScrollEnabled = true
    internal var twoFingersScrollEnabled = true
    internal var threeFingersScrollEnabled = true
    internal var flingInOverPanEnabled = false

    /**
     * Starts a pinch gesture or continues an ongoing gesture.
     * Returns true if we are interested in the result.
     */
    internal fun maybeStart(event: MotionEvent): Boolean {
        return detector.onTouchEvent(event)
    }

    /**
     * Cancels the current fling gesture. It will be released in the
     * next UI cycle, where we will go to idle state.
     */
    internal fun cancelFling() {
        flingScroller.forceFinished(true)
    }

    /**
     * Cancels the current scroll gesture. If we are in overpan, this
     * animates back to a reasonable value. Otherwise, just go to
     * idle state.
     */
    internal fun cancelScroll() {
        if (!correctOverpan()) {
            stateController.makeIdle()
        }
    }

    /**
     * Initiates an animation to correct any existing overpan
     * @return true if a correction was initiated, false otherwise
     */
    private fun correctOverpan(): Boolean {
        if (panManager.isOverEnabled) {
            val fix = panManager.correction
            if (fix.x != 0f || fix.y != 0f) {
                matrixController.animateUpdate { panBy(fix, true) }
                return true
            }
        }
        return false
    }

    override fun onDown(e: MotionEvent): Boolean {
        cancelFling()
        return true // We are interested in the gesture.
    }

    /**
     * Fling event was detected. Start animating.
     */
    override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
        if (!flingEnabled) return false
        if (!panManager.isEnabled) return false
        val velX = (if (panManager.horizontalPanEnabled) velocityX else 0F).toInt()
        val velY = (if (panManager.verticalPanEnabled) velocityY else 0F).toInt()

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
        if (minX >= maxX && minY >= maxY && !panManager.isOverEnabled) {
            return false
        }
        // Must be after the other conditions.
        if (!stateController.setFlinging()) return false
        // disable long press detection while we are flinging
        // to prevent long presses from interrupting a possible followup scroll gesture
        detector.setIsLongpressEnabled(false)

        @ZoomApi.ScaledPan val overScrollX = if (panManager.horizontalOverPanEnabled) panManager.maxHorizontalOverPan else 0F
        @ZoomApi.ScaledPan val overScrollY = if (panManager.verticalOverPanEnabled) panManager.maxVerticalOverPan else 0F
        LOG.i("startFling", "velocityX:", velX, "velocityY:", velY)
        LOG.i("startFling", "flingX:", "min:", minX, "max:", maxX, "start:", startX, "overScroll:", overScrollY)
        LOG.i("startFling", "flingY:", "min:", minY, "max:", maxY, "start:", startY, "overScroll:", overScrollX)
        flingScroller.fling(startX, startY,
                velX, velY,
                minX, maxX, minY, maxY,
                overScrollX.toInt(), overScrollY.toInt())

        matrixController.post(object : Runnable {
            override fun run() {
                if (flingScroller.isFinished) {
                    stateController.makeIdle()
                    // re-enable long press detection
                    detector.setIsLongpressEnabled(true)
                } else if (flingScroller.computeScrollOffset()) {
                    val newPan = ScaledPoint(flingScroller.currX.toFloat(), flingScroller.currY.toFloat())
                    // OverScroller will eventually go back to our bounds.
                    matrixController.applyUpdate { panTo(newPan, true) }
                    matrixController.postOnAnimation(this)
                }
            }
        })
        return true
    }


    /**
     * Scroll event detected.
     *
     * We assume overScroll is true. If this is the case, it will be reset in [cancelScroll].
     * If not, the applyScaledPan function will ignore our delta.
     *
     * TODO this this not true! ^
     */
    override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent?,
            @ZoomApi.ScaledPan distanceX: Float,
            @ZoomApi.ScaledPan distanceY: Float
    ): Boolean {
        if (!scrollEnabled) return false

        val isOneFinger = e2?.pointerCount == 1
        val isTwoFingers = e2?.pointerCount == 2
        val isThreeFingers = e2?.pointerCount == 3

        if (!oneFingerScrollEnabled && isOneFinger) return false
        if (!twoFingersScrollEnabled && isTwoFingers) return false
        if (!threeFingersScrollEnabled && isThreeFingers) return false
        if (!panManager.isEnabled) return false
        if (!stateController.setScrolling()) return false

        // Change sign, since we work with opposite values.
        val delta = ScaledPoint(-distanceX, -distanceY)

        // See if we are overscrolling.
        val panFix = panManager.correction

        // If we are overscrolling AND scrolling towards the overscroll direction...
        if (panFix.x < 0 && delta.x > 0 || panFix.x > 0 && delta.x < 0) {
            // Compute friction: a factor for distances. Must be 1 if we are not overscrolling,
            // and 0 if we are at the end of the available overscroll. This works:
            val overScrollX = abs(panFix.x) / panManager.maxHorizontalOverPan // 0 ... 1
            val frictionX = 0.6f * (1f - overScrollX.toDouble().pow(0.4).toFloat()) // 0 ... 0.6
            LOG.i("onScroll", "applying friction X:", frictionX)
            delta.x *= frictionX
        }
        if (panFix.y < 0 && delta.y > 0 || panFix.y > 0 && delta.y < 0) {
            val overScrollY = abs(panFix.y) / panManager.maxVerticalOverPan // 0 ... 1
            val frictionY = 0.6f * (1f - overScrollY.toDouble().pow(0.4).toFloat()) // 0 ... 10.6
            LOG.i("onScroll", "applying friction Y:", frictionY)
            delta.y *= frictionY
        }

        // If disabled, reset to 0.
        if (!panManager.horizontalPanEnabled) delta.x = 0f
        if (!panManager.verticalPanEnabled) delta.y = 0f


        if (delta.x != 0f || delta.y != 0f) {
            matrixController.applyUpdate { panBy(delta, true) }
        }
        return true
    }

    // Not interested in this callback
    override fun onShowPress(e: MotionEvent?) {}

    // Not interested in this callback
    override fun onSingleTapUp(e: MotionEvent?): Boolean {
        return false
    }

    // Not interested in this callback
    override fun onLongPress(e: MotionEvent?) {}

    companion object {
        private val TAG = ScrollFlingDetector::class.java.simpleName
        private val LOG = ZoomLogger.create(TAG)
    }
}