package com.otaliastudios.zoom.internal

import android.animation.*
import android.annotation.SuppressLint
import android.graphics.Matrix
import android.graphics.RectF
import android.view.animation.AccelerateDecelerateInterpolator
import com.otaliastudios.zoom.*
import com.otaliastudios.zoom.ZoomApi.RealZoom
import com.otaliastudios.zoom.ZoomApi.Zoom
import com.otaliastudios.zoom.internal.movement.PanManager
import com.otaliastudios.zoom.internal.movement.ZoomManager

/**
 * Applies changes to the matrix, holds the content and container sizes and
 * transformed rects, dispatches updates.
 *
 * It also uses [StateController] because it can start (and end) animations.
 */
internal class MatrixController(
        private val zoomManager: ZoomManager,
        private val panManager: PanManager,
        private val stateController: StateController,
        private val callback: Callback
) {

    internal interface Callback {
        fun onMatrixUpdate()
        fun onMatrixSizeChanged(firstTime: Boolean)
        fun post(action: Runnable)
        fun postOnAnimation(action: Runnable)
    }

    private var contentScaledRect = RectF()
    private var contentRect = RectF()
    private var stub = Matrix()
    internal var isInitialized = false
        private set

    /**
     * Returns the current matrix. This can be changed from the outside, but is not
     * guaranteed to remain stable.
     *
     * @return the current matrix.
     */
    internal val matrix: Matrix = Matrix()
        get() {
            field.set(stub)
            return field
        }

    @ZoomApi.ScaledPan
    internal val contentScaledWidth: Float
        get() = contentScaledRect.width()

    @ZoomApi.ScaledPan
    internal val contentScaledHeight: Float
        get() = contentScaledRect.height()

    /**
     * Returns the content width as passed to [setContentSize].
     * @return the current width
     */
    @ZoomApi.AbsolutePan
    internal val contentWidth: Float
        get() = contentRect.width()

    /**
     * Returns the content height as passed to [setContentSize].
     * @return the current height
     */
    @ZoomApi.AbsolutePan
    internal val contentHeight: Float
        get() = contentRect.height()

    /**
     * Returns the container width as passed to [setContainerSize].
     * @return the current width
     */
    internal var containerWidth = 0F
        private set

    /**
     * Returns the container height as passed to [setContainerSize].
     * @return the current height
     */
    internal var containerHeight = 0F
        private set

    /**
     * The current pan as a [ScaledPoint]
     * This field will be updated according to current scaled pan when accessed.
     */
    internal val scaledPan = ScaledPoint()
        get() {
            field.set(scaledPanX, scaledPanY)
            return field
        }

    /**
     * The current horizontal scaled pan, which is the pan position of the content
     * according to the current zoom value (so it's scaled).
     */
    @ZoomApi.ScaledPan
    internal val scaledPanX: Float
        get() = contentScaledRect.left

    /**
     * The current vertical scaled pan, which is the pan position of the content
     * according to the current zoom value (so it's scaled).
     */
    @ZoomApi.ScaledPan
    internal val scaledPanY: Float
        get() = contentScaledRect.top

    /**
     * Gets the current zoom value.
     * This value will match the scaleX - scaleY values you get into the [Matrix],
     * and is the actual scale value of the content from its original size.
     */
    @ZoomApi.RealZoom
    internal val zoom: Float
        get() = contentScaledRect.width() / contentRect.width()


    /**
     * Returns the current horizontal pan value, in content coordinates
     * (that is, as if there was no zoom at all) referring to what was passed
     * to [setContentSize].
     */
    @ZoomApi.AbsolutePan
    internal val panX: Float
        get() = scaledPanX / zoom

    /**
     * Returns the current vertical pan value, in content coordinates
     * (that is, as if there was no zoom at all) referring to what was passed
     * to [setContentSize].
     */
    @ZoomApi.AbsolutePan
    internal val panY: Float
        get() = scaledPanY / zoom

    /**
     * The current pan as an [AbsolutePoint].
     * This field will be updated according to current pan when accessed.
     */
    internal val pan = AbsolutePoint()
        get() {
            field.set(panX, panY)
            return field
        }


    internal fun post(action: Runnable) {
        callback.post(action)
    }

    internal fun postOnAnimation(action: Runnable) {
        callback.postOnAnimation(action)
    }

    /**
     * Clears our state.
     */
    internal fun clear() {
        isInitialized = false
        containerHeight = 0f
        containerWidth = 0f
        contentScaledRect = RectF()
        contentRect = RectF()
        stub = Matrix()
    }

    internal fun setContentSize(width: Float, height: Float, forceReset: Boolean) {
        if (width <= 0 || height <= 0) return
        if (contentWidth != width || contentHeight != height || forceReset) {
            contentRect.set(0f, 0f, width, height)
            onSizeChanged(forceReset)
        }
    }

    internal fun setContainerSize(width: Float, height: Float, forceReset: Boolean) {
        if (width <= 0 || height <= 0) return
        if (width != containerWidth || height != containerHeight || forceReset) {
            containerWidth = width
            containerHeight = height
            onSizeChanged(forceReset)
        }
    }

    private fun onSizeChanged(forceReset: Boolean) {
        // We will sync them later.
        contentScaledRect.set(contentRect)
        if (contentWidth <= 0
                || contentHeight <= 0
                || containerWidth <= 0
                || containerHeight <= 0)
            return
        LOG.w("onSizeChanged:", "containerWidth:", containerWidth,
                "containerHeight:", containerHeight,
                "contentWidth:", contentWidth,
                "contentHeight:", contentHeight)

        val firstTime = !isInitialized || forceReset
        callback.onMatrixSizeChanged(firstTime)
        isInitialized = true
    }

    /**
     * Should be called anytime the [stub] matrix is edited or the [contentRect]
     * changes.
     */
    internal fun sync(notify: Boolean = false) {
        stub.mapRect(contentScaledRect, contentRect)
        if (notify) dispatch()
    }
    
    private fun dispatch() {
        callback.onMatrixUpdate()
    }

    /**
     * Low level function to just set the scale.
     */
    internal fun setScale(scale: Float) {
        stub.setScale(scale, scale)
        sync()
    }

    /**
     * Calls [PanManager.checkBounds] on both directions
     * and applies the correction to the matrix if needed.
     */
    internal fun ensurePan(allowOverPan: Boolean, notify: Boolean = false) {
        @ZoomApi.ScaledPan val fixX = panManager.checkBounds(true, allowOverPan)
        @ZoomApi.ScaledPan val fixY = panManager.checkBounds(false, allowOverPan)
        if (fixX != 0f || fixY != 0f) {
            stub.postTranslate(fixX, fixY)
            sync(notify)
        }
    }

    /**
     * Applies the given zoom value.
     * The zoom is applied so that the center point is kept in its place
     *
     * @param zoom           the new zoom value
     * @param allowOverZoom  whether to overpinch
     * @param zoomTargetX    the x-axis zoom target
     * @param zoomTargetY    the y-axis zoom target
     */
    internal fun applyZoom(
            @RealZoom zoom: Float,
            allowOverZoom: Boolean,
            allowOverPan: Boolean = false,
            zoomTargetX: Float = containerWidth / 2f,
            zoomTargetY: Float = containerHeight / 2f,
            notifyListeners: Boolean = true
    ) {
        if (!isInitialized) return
        val newZoom = zoomManager.checkBounds(zoom, allowOverZoom)
        val scaleFactor = newZoom / this.zoom
        stub.postScale(scaleFactor, scaleFactor, zoomTargetX, zoomTargetY)
        sync()
        ensurePan(allowOverPan)
        if (notifyListeners) dispatch()
    }

    /**
     * Applies the given scaled translation.
     *
     * Scaled translation are applied through [Matrix.postTranslate],
     * which acts on the actual dimension of the rect.
     *
     * @param deltaX          the x translation
     * @param deltaY          the y translation
     * @param allowOverPan whether to overScroll
     */
    internal fun applyScaledPan(
            @ZoomApi.ScaledPan deltaX: Float,
            @ZoomApi.ScaledPan deltaY: Float,
            allowOverPan: Boolean
    ) {
        if (!isInitialized) return
        stub.postTranslate(deltaX, deltaY)
        sync()
        ensurePan(allowOverPan)
        dispatch()
    }

    /**
     * Applies both zoom and absolute pan. This is like specifying a position.
     * The semantics of this are that after the position is applied, the zoom corresponds
     * to the given value, [ZoomApi.panX] returns x, [ZoomApi.panY] returns y.
     *
     * Absolute panning is achieved through [Matrix.preTranslate],
     * which works in the original coordinate system.
     *
     * @param zoom            the new zoom value
     * @param x               the final left absolute pan
     * @param y               the final top absolute pan
     * @param allowOverPan true if overscroll is allowed, false otherwise
     * @param allowOverZoom  true if overpinch is allowed, false otherwise
     * @param zoomTargetX     the x-axis zoom target
     * @param zoomTargetY     the y-axis zoom target
     * @param notifyListeners when true listeners are informed about this zoom/pan, otherwise they wont
     */
    internal fun applyZoomAndAbsolutePan(
            @RealZoom zoom: Float,
            @ZoomApi.AbsolutePan x: Float,
            @ZoomApi.AbsolutePan y: Float,
            allowOverPan: Boolean,
            allowOverZoom: Boolean = false,
            zoomTargetX: Float? = null,
            zoomTargetY: Float? = null,
            notifyListeners: Boolean = true
    ) {
        if (!isInitialized) return
        // Translation
        val delta = AbsolutePoint(x, y) - pan
        stub.preTranslate(delta.x, delta.y)
        sync()

        // Scale
        val newZoom = zoomManager.checkBounds(zoom, allowOverZoom)
        val scaleFactor = newZoom / this.zoom
        // TODO: This used to work but I am not sure about it.
        // mMatrix.postScale(scaleFactor, scaleFactor, getScaledPanX(), getScaledPanY());
        // It keeps the pivot point at the scaled values 0, 0 (see applyPinch).
        // I think we should keep the current top, left.. Let's try:
        val pivotX = zoomTargetX ?: 0F
        val pivotY = zoomTargetY ?: 0F
        stub.postScale(scaleFactor, scaleFactor, pivotX, pivotY)
        sync()
        ensurePan(allowOverPan)
        if (notifyListeners) dispatch()
    }

    internal var animationDuration = ZoomEngine.DEFAULT_ANIMATION_DURATION
    private val activeAnimators = mutableSetOf<ValueAnimator>()
    private val cancelAnimationListener = object : AnimatorListenerAdapter() {

        override fun onAnimationEnd(animator: Animator) {
            cleanup(animator)
        }

        override fun onAnimationCancel(animator: Animator) {
            cleanup(animator)
        }

        private fun cleanup(animator: Animator) {
            animator.removeListener(this)
            activeAnimators.remove(animator)
            if (activeAnimators.isEmpty()) stateController.makeIdle()
        }
    }

    internal fun cancelAnimations() {
        activeAnimators.forEach { it.cancel() }
        activeAnimators.clear()
    }

    /**
     * Prepares a [ValueAnimator] for the first run
     *
     * @return itself (for chaining)
     */
    private fun ValueAnimator.prepare(): ValueAnimator {
        this.duration = animationDuration
        this.addListener(cancelAnimationListener)
        this.interpolator = ANIMATION_INTERPOLATOR
        return this
    }

    /**
     * Starts a [ValueAnimator] with the given update function
     *
     * @return itself (for chaining)
     */
    private fun ValueAnimator.start(onUpdate: (ValueAnimator) -> Unit): ValueAnimator {
        this.addUpdateListener(onUpdate)
        this.start()
        activeAnimators.add(this)
        return this
    }

    /**
     * Calls [applyZoom] repeatedly
     * until the final zoom is reached, interpolating.
     *
     * @param zoom the new zoom
     * @param allowOverPinch whether overpinching is allowed
     */
    internal fun animateZoom(@RealZoom zoom: Float, allowOverPinch: Boolean) {
        if (!isInitialized) return
        if (!stateController.setAnimating()) return
        @RealZoom val startZoom = this.zoom
        @RealZoom val endZoom = zoomManager.checkBounds(zoom, allowOverPinch)
        ValueAnimator.ofFloat(startZoom, endZoom).prepare().start {
            LOG.v("animateZoom:", "animationStep:", it.animatedFraction)
            applyZoom(it.animatedValue as Float, allowOverPinch)
        }
    }

    /**
     * Calls [applyZoomAndAbsolutePan] repeatedly
     * until the final position is reached, interpolating.
     *
     * @param zoom            new zoom
     * @param x               final abs pan
     * @param y               final abs pan
     * @param allowOverScroll true if overscroll is allowed, false otherwise
     * @param allowOverPinch  true if overpinch is allowed, false otherwise
     * @param zoomTargetX     the x-axis zoom target
     * @param zoomTargetY     the y-axis zoom target
     */
    @SuppressLint("ObjectAnimatorBinding")
    internal fun animateZoomAndAbsolutePan(
            @RealZoom zoom: Float,
            @ZoomApi.AbsolutePan x: Float,
            @ZoomApi.AbsolutePan y: Float,
            allowOverScroll: Boolean,
            allowOverPinch: Boolean = false,
            zoomTargetX: Float? = null,
            zoomTargetY: Float? = null
    ) {
        if (!isInitialized) return
        if (!stateController.setAnimating()) return
        @Zoom val startZoom = this.zoom
        @Zoom val endZoom = zoomManager.checkBounds(zoom, allowOverScroll)
        val startPan = pan
        val targetPan = AbsolutePoint(x, y)
        LOG.i("animateZoomAndAbsolutePan:", "starting.", "startX:", startPan.x, "endX:", x, "startY:", startPan.y, "endY:", y)
        LOG.i("animateZoomAndAbsolutePan:", "starting.", "startZoom:", startZoom, "endZoom:", endZoom)

        ObjectAnimator.ofPropertyValuesHolder(
                PropertyValuesHolder.ofObject(
                        "pan",
                        TypeEvaluator { fraction: Float, startValue: AbsolutePoint, endValue: AbsolutePoint ->
                            startValue + (endValue - startValue) * fraction
                        }, startPan, targetPan),
                PropertyValuesHolder.ofFloat(
                        "zoom",
                        startZoom, endZoom)
        ).prepare().start {
            val newZoom = it.getAnimatedValue("zoom") as Float
            val currentPan = it.getAnimatedValue("pan") as AbsolutePoint
            applyZoomAndAbsolutePan(newZoom,
                    currentPan.x, currentPan.y,
                    allowOverScroll, allowOverPinch,
                    zoomTargetX, zoomTargetY)
        }
    }

    /**
     * Calls [applyScaledPan] repeatedly
     * until the final delta is applied, interpolating.
     *
     * @param deltaX          a scaled delta
     * @param deltaY          a scaled delta
     * @param allowOverScroll whether to overscroll
     */
    internal fun animateScaledPan(
            @ZoomApi.ScaledPan deltaX: Float,
            @ZoomApi.ScaledPan deltaY: Float,
            allowOverScroll: Boolean
    ) {
        if (!isInitialized) return
        if (!stateController.setAnimating()) return
        val startPan = scaledPan
        val endPan = startPan + ScaledPoint(deltaX, deltaY)

        ValueAnimator.ofObject(TypeEvaluator { fraction, startValue: ScaledPoint, endValue: ScaledPoint ->
            startValue + (endValue - startValue) * fraction - scaledPan
        }, startPan, endPan).prepare().start {
            LOG.v("animateScaledPan:", "animationStep:", it.animatedFraction)
            val currentPan = it.animatedValue as ScaledPoint
            applyScaledPan(currentPan.x, currentPan.y, allowOverScroll)
        }
    }


    companion object {
        private val TAG = MatrixController::class.java.simpleName
        private val LOG = ZoomLogger.create(TAG)

        // TODO Make public, add API. Use androidx.Interpolator?
        private val ANIMATION_INTERPOLATOR = AccelerateDecelerateInterpolator()
    }
}