package com.otaliastudios.zoom.internal.movement

import android.annotation.SuppressLint
import android.view.Gravity
import com.otaliastudios.zoom.*
import com.otaliastudios.zoom.internal.matrix.MatrixController

/**
 * Contains:
 *
 * - utilities for transforming pan
 * - the pan boundaries and utilities for correcting it
 * - utilities for computing the pan status
 * - the pan settings (whether it's enabled or not).
 *
 * Does NOT hold the current pan values, which is done by the [MatrixController].
 */
internal class PanManager(
        private val engine: ZoomEngine,
        provider: () -> MatrixController) : MovementManager(provider) {

    internal var horizontalOverPanEnabled = true
    internal var verticalOverPanEnabled = true
    internal var horizontalPanEnabled = true
    internal var verticalPanEnabled = true
    internal var alignment = ZoomApi.ALIGNMENT_DEFAULT

    internal var overPanRangeProvider: OverPanRangeProvider = OverPanRangeProvider.DEFAULT

    /** whether overpan is enabled, horizontally or vertically */
    override val isOverEnabled get() = horizontalOverPanEnabled || verticalOverPanEnabled

    /** whether pan is enabled, horizontally or vertically */
    override val isEnabled get() = horizontalPanEnabled || verticalPanEnabled


    override fun clear() {
        // We have no state to clear.
    }

    /**
     * Represent a snapshot of the current pan status along some dimension.
     * This can be filled by calling [computeStatus].
     */
    internal class Status {
        @ZoomApi.ScaledPan internal var minValue: Int = 0
        @ZoomApi.ScaledPan internal var currentValue: Int = 0
        @ZoomApi.ScaledPan internal var maxValue: Int = 0
        internal var isInOverPan: Boolean = false
    }

    /**
     * Puts minimum, maximum and current values in the [Status] object.
     * Since axes are shifted (pans are negative), min values are related to bottom-right,
     * while max values are related to top-left.
     */
    internal fun computeStatus(horizontal: Boolean, output: Status) {
        @ZoomApi.ScaledPan val currentPan = (if (horizontal) controller.scaledPanX else controller.scaledPanY).toInt()
        val containerDim = (if (horizontal) controller.containerWidth else controller.containerHeight).toInt()
        @ZoomApi.ScaledPan val contentDim = (if (horizontal) controller.contentScaledWidth else controller.contentScaledHeight).toInt()
        val fix = checkBounds(horizontal, false).toInt()
        val alignment = if (horizontal) Alignment.getHorizontal(alignment) else Alignment.getVertical(alignment)
        @Suppress("CascadeIf")
        if (contentDim > containerDim) {
            // Content is bigger. We can move between 0 and extraSpace, but since our pans
            // are negative, we must invert the sign.
            val extraSpace = contentDim - containerDim
            output.minValue = -extraSpace
            output.maxValue = 0
        } else if (Alignment.isNone(alignment)) {
            // Content is free to be moved, although smaller than the container. We can move
            // between 0 and extraSpace (and when content is smaller, pan is positive).
            val extraSpace = containerDim - contentDim
            output.minValue = 0
            output.maxValue = extraSpace
        } else {
            // Content can't move in this dimensions. Go back to the correct value.
            val finalValue = currentPan + fix
            output.minValue = finalValue
            output.maxValue = finalValue
        }
        output.currentValue = currentPan
        output.isInOverPan = fix != 0
    }

    /**
     * The scaled correction that should be applied to the content in order
     * to respect the constraints (e.g. boundaries or special gravity alignments)
     */
    internal val correction = ScaledPoint()
        get() {
            // update correction
            field.set(
                    checkBounds(horizontal = true, allowOverScroll = false),
                    checkBounds(horizontal = false, allowOverScroll = false)
            )
            return field
        }

    /**
     * Checks the current pan state.
     *
     * @param horizontal true when checking horizontal pan, false for vertical
     * @param allowOverScroll set to true if pan values within overscroll range should be considered valid
     *
     * @return the pan correction to be applied to get into a valid state (0 if valid already)
     */
    @SuppressLint("RtlHardcoded")
    @ZoomApi.ScaledPan
    internal fun checkBounds(horizontal: Boolean, allowOverScroll: Boolean): Float {
        @ZoomApi.ScaledPan val value = if (horizontal) controller.scaledPanX else controller.scaledPanY
        val containerSize = if (horizontal) controller.containerWidth else controller.containerHeight
        @ZoomApi.ScaledPan val contentSize = if (horizontal) controller.contentScaledWidth else controller.contentScaledHeight
        val overScrollable = if (horizontal) horizontalOverPanEnabled else verticalOverPanEnabled
        @ZoomApi.ScaledPan val overScroll = if (overScrollable && allowOverScroll) {
            if (horizontal) maxHorizontalOverPan else maxVerticalOverPan
        } else {
            0F
        }

        val alignmentGravity = if (horizontal) {
            Alignment.toHorizontalGravity(alignment, Gravity.NO_GRAVITY)
        } else {
            Alignment.toVerticalGravity(alignment, Gravity.NO_GRAVITY)
        }

        var min: Float
        var max: Float
        if (contentSize <= containerSize) {
            // If content is smaller than container, act according to the alignment.
            // Expect the output to be >= 0, we will show part of the container background.
            val extraSpace = containerSize - contentSize // > 0
            if (alignmentGravity != Gravity.NO_GRAVITY) {
                val correction = applyGravity(alignmentGravity, extraSpace, horizontal)
                min = correction
                max = correction
            } else {
                // This is Alignment.IDLE or NO_VALUE. Don't force a value, just stay in the container boundaries.
                min = 0F
                max = extraSpace
            }
        } else {
            // If contentSize is bigger, we just don't want to go outside.
            // Need a negative translation, that hides content.
            min = containerSize - contentSize
            max = 0f
        }
        min -= overScroll
        max += overScroll
        val desired = value.coerceIn(min, max)
        return desired - value
    }

    /**
     * The amount of overscroll that is allowed in horizontal direction.
     */
    @ZoomApi.ScaledPan
    internal val maxHorizontalOverPan: Float
        get() {
            var value = overPanRangeProvider.getOverPan(engine, horizontal = true)
            if (value < 0) {
                LOG.w("Received negative maxHorizontalOverPan value, coercing to 0")
                value = value.coerceAtLeast(0F)
            }
            return value
        }

    /**
     * The amount of overscroll that is allowed in vertical direction.
     */
    @ZoomApi.ScaledPan
    internal val maxVerticalOverPan: Float
        get() {
            var value = overPanRangeProvider.getOverPan(engine, horizontal = false)
            if (value < 0) {
                LOG.w("Received negative maxVerticalOverPan value, coercing to 0")
                value = value.coerceAtLeast(0F)
            }
            return value
        }

    /**
     * Returns 0 for 'start' gravities, [extraSpace] for 'end' gravities, and half of it
     * for 'center' gravities.
     */
    @SuppressLint("RtlHardcoded")
    internal fun applyGravity(gravity: Int, extraSpace: Float, horizontal: Boolean): Float {
        val resolved = if (horizontal) {
            // TODO support START and END correctly.
            gravity and Gravity.HORIZONTAL_GRAVITY_MASK
        } else {
            gravity and Gravity.VERTICAL_GRAVITY_MASK
        }
        return when (resolved) {
            Gravity.TOP, Gravity.LEFT -> 0F
            Gravity.BOTTOM, Gravity.RIGHT -> extraSpace
            Gravity.CENTER_VERTICAL, Gravity.CENTER_HORIZONTAL -> 0.5F * extraSpace
            else -> 0F // Includes Gravity.NO_GRAVITY and unsupported mixes like FILL
        }
    }

    companion object {

        private val TAG = PanManager::class.java.simpleName
        private val LOG = ZoomLogger.create(TAG)

    }

}