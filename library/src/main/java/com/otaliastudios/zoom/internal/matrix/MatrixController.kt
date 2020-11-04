package com.otaliastudios.zoom.internal.matrix

import android.animation.*
import android.annotation.SuppressLint
import android.graphics.Matrix
import android.graphics.RectF
import android.view.animation.AccelerateDecelerateInterpolator
import com.otaliastudios.zoom.*
import com.otaliastudios.zoom.internal.StateController
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
        fun onMatrixSizeChanged(oldZoom: Float, firstTime: Boolean)
        fun post(action: Runnable): Boolean
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


    internal fun post(action: Runnable) = callback.post(action)

    internal fun postOnAnimation(action: Runnable) = callback.postOnAnimation(action)

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
            val oldZoom = zoom
            contentRect.set(0f, 0f, width, height)
            onSizeChanged(oldZoom, forceReset)
        }
    }

    internal fun setContainerSize(width: Float, height: Float, forceReset: Boolean) {
        if (width <= 0 || height <= 0) return
        if (width != containerWidth || height != containerHeight || forceReset) {
            containerWidth = width
            containerHeight = height
            onSizeChanged(zoom, forceReset)
        }
    }

    private fun onSizeChanged(oldZoom: Float, forceReset: Boolean) {
        sync()
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
        isInitialized = true
        callback.onMatrixSizeChanged(oldZoom, firstTime)
    }

    /**
     * Should be called anytime the [stub] matrix is edited or the [contentRect]
     * changes. This updates all the *scaled* values that this class exposes.
     */
    private fun sync() {
        stub.mapRect(contentScaledRect, contentRect)
    }

    private fun dispatch() {
        callback.onMatrixUpdate()
    }

    /**
     * Calls [PanManager.checkBounds] on both directions
     * and applies the correction to the matrix if needed.
     */
    private fun ensurePan(allowOverPan: Boolean) {
        @ZoomApi.ScaledPan val fixX = panManager.checkBounds(true, allowOverPan)
        @ZoomApi.ScaledPan val fixY = panManager.checkBounds(false, allowOverPan)
        if (fixX != 0f || fixY != 0f) {
            stub.postTranslate(fixX, fixY)
            sync()
        }
    }

    /**
     * Builds and applies a [MatrixUpdate].
     */
    internal fun applyUpdate(update: MatrixUpdate.Builder.() -> Unit) {
        applyUpdate(MatrixUpdate.obtain(update))
    }

    /**
     * Applies the given [MatrixUpdate].
     */
    internal fun applyUpdate(update: MatrixUpdate) {
        if (!isInitialized) return

        // Apply pan
        if (update.pan != null) {
            // With absolute pan, we will use preTranslate which works in the original
            // reference system.
            val delta = if (update.isPanRelative) update.pan else update.pan - pan
            stub.preTranslate(delta.x, delta.y)
            sync()
        } else if (update.scaledPan != null) {
            val delta = if (update.isPanRelative) update.scaledPan else update.scaledPan - scaledPan
            stub.postTranslate(delta.x, delta.y)
            sync()
        }

        // Apply zoom
        if (update.hasZoom) {
            var newZoom = if (update.isZoomRelative) zoom * update.zoom else update.zoom
            newZoom = zoomManager.checkBounds(newZoom, update.canOverZoom)
            val factor = newZoom / this.zoom
            val pivotX = when {
                update.pivotX != null -> update.pivotX
                update.hasPan -> 0F
                else -> containerWidth / 2f
            }
            val pivotY = when {
                update.pivotY != null -> update.pivotY
                update.hasPan -> 0F
                else -> containerHeight / 2f
            }
            stub.postScale(factor, factor, pivotX, pivotY)
            sync()
        }

        ensurePan(update.canOverPan)
        if (update.notify) dispatch()
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
     * Builds and applies the given [MatrixUpdate] by calling [applyUpdate]
     * repeatedly until the final position is reached, interpolating in the middle.
     */
    internal fun animateUpdate(update: MatrixUpdate.Builder.() -> Unit) {
        animateUpdate(MatrixUpdate.obtain(update))
    }

    /**
     * Calls [applyUpdate] repeatedly until the final position is reached,
     * interpolating in the middle.
     */
    @SuppressLint("ObjectAnimatorBinding")
    internal fun animateUpdate(update: MatrixUpdate) {
        if (!isInitialized) return
        if (!stateController.setAnimating()) return

        val holders = mutableListOf<PropertyValuesHolder>()
        if (update.pan != null) {
            val target = if (update.isPanRelative) pan + update.pan else update.pan
            // ofObject doesn't respect animator.interpolator, so we use ofFloat instead
            holders.add(PropertyValuesHolder.ofFloat("panX", panX, target.x))
            holders.add(PropertyValuesHolder.ofFloat("panY", panY, target.y))
        } else if (update.scaledPan != null) {
            val target = if (update.isPanRelative) scaledPan + update.scaledPan else update.scaledPan
            // ofObject doesn't respect animator.interpolator, so we use ofFloat instead
            holders.add(PropertyValuesHolder.ofFloat("panX", scaledPanX, target.x))
            holders.add(PropertyValuesHolder.ofFloat("panY", scaledPanY, target.y))
        }
        if (update.hasZoom) {
            var newZoom = if (update.isZoomRelative) zoom * update.zoom else update.zoom
            newZoom = zoomManager.checkBounds(newZoom, update.canOverZoom)
            holders.add(PropertyValuesHolder.ofFloat("zoom", zoom, newZoom))
        }

        val animator = ObjectAnimator.ofPropertyValuesHolder(*holders.toTypedArray())
        animator.duration = animationDuration
        animator.interpolator = ANIMATION_INTERPOLATOR
        animator.addListener(cancelAnimationListener)
        animator.addUpdateListener {
            applyUpdate {
                if (update.hasZoom) {
                    val newZoom = it.getAnimatedValue("zoom") as Float
                    zoomTo(newZoom, update.canOverZoom)
                }
                if (update.pan != null) {
                    val newPanX = it.getAnimatedValue("panX") as Float
                    val newPanY = it.getAnimatedValue("panY") as Float
                    panTo(AbsolutePoint(newPanX, newPanY), update.canOverPan)
                } else if (update.scaledPan != null) {
                    val newPanX = it.getAnimatedValue("panX") as Float
                    val newPanY = it.getAnimatedValue("panY") as Float
                    panTo(ScaledPoint(newPanX, newPanY), update.canOverPan)
                }
                pivot(update.pivotX, update.pivotY)
                notify = update.notify
            }
        }
        animator.start()
        activeAnimators.add(animator)
    }

    companion object {
        private val TAG = MatrixController::class.java.simpleName
        private val LOG = ZoomLogger.create(TAG)

        // TODO Make public, add API. Use androidx.Interpolator?
        private val ANIMATION_INTERPOLATOR = AccelerateDecelerateInterpolator()
    }
}
