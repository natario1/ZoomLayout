package com.otaliastudios.zoom.internal.movement

import android.annotation.SuppressLint
import android.view.Gravity
import com.otaliastudios.zoom.*

/**
 * Contains:
 *
 * - utilities for transforming pan
 * - the pan boundaries and utilities for correcting it
 * - utilities for computing the pan status
 * - the pan settings (whether it's enabled or not).
 *
 * Does NOT hold the current pan values, which is done by the [engine].
 */
internal class PanManager(private val engine: ZoomEngine) {

    internal var horizontalOverPanEnabled = true
    internal var verticalOverPanEnabled = true
    internal var horizontalPanEnabled = true
    internal var verticalPanEnabled = true

    val isOverPanEnabled = horizontalOverPanEnabled || verticalOverPanEnabled

    val isPanEnabled = horizontalPanEnabled || verticalPanEnabled

    internal class Status {
        @ZoomApi.ScaledPan internal var minValue: Int = 0
        @ZoomApi.ScaledPan internal var currentValue: Int = 0
        @ZoomApi.ScaledPan internal var maxValue: Int = 0
        internal var isInOverPan: Boolean = false
    }


    // Puts min, start and max values in the mTemp array.
    // Since axes are shifted (pans are negative), min values are related to bottom-right,
    // while max values are related to top-left.
    internal fun computeStatus(horizontal: Boolean, output: Status) {
        @ZoomApi.ScaledPan val currentPan = (if (horizontal) engine.scaledPanX else engine.scaledPanY).toInt()
        val containerDim = (if (horizontal) engine.containerWidth else engine.containerHeight).toInt()
        @ZoomApi.ScaledPan val contentDim = (if (horizontal) engine.contentScaledWidth else engine.contentScaledHeight).toInt()
        val fix = checkBounds(horizontal, false).toInt()
        val alignment = if (horizontal) Alignment.getHorizontal(engine.alignment) else Alignment.getVertical(engine.alignment)
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
        @ZoomApi.ScaledPan val value = if (horizontal) engine.scaledPanX else engine.scaledPanY
        val containerSize = if (horizontal) engine.containerWidth else engine.containerHeight
        @ZoomApi.ScaledPan val contentSize = if (horizontal) engine.contentScaledWidth else engine.contentScaledHeight
        val overScrollable = if (horizontal) horizontalOverPanEnabled else verticalOverPanEnabled
        @ZoomApi.ScaledPan val overScroll = (if (overScrollable && allowOverScroll) maxOverPan else 0).toFloat()
        val alignmentGravity = if (horizontal) {
            Alignment.toHorizontalGravity(engine.alignment, Gravity.NO_GRAVITY)
        } else {
            Alignment.toVerticalGravity(engine.alignment, Gravity.NO_GRAVITY)
        }

        var min: Float
        var max: Float
        if (contentSize <= containerSize) {
            // If content is smaller than container, act according to the alignment.
            // Expect the output to be >= 0, we will show part of the container background.
            val extraSpace = containerSize - contentSize // > 0
            if (alignmentGravity != Gravity.NO_GRAVITY) {
                val correction = engine.applyGravity(alignmentGravity, extraSpace, horizontal)
                min = correction
                max = correction
            } else {
                // This is Alignment.NONE or NO_VALUE. Don't force a value, just stay in the container boundaries.
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
     * The amount of overscroll that is allowed in both direction. This is currently
     * a fixed value, but might be made configurable in the future.
     */
    @ZoomApi.ScaledPan
    internal val maxOverPan: Int
        get() {
            val overX = engine.containerWidth * DEFAULT_OVERPAN_FACTOR
            val overY = engine.containerHeight * DEFAULT_OVERPAN_FACTOR
            return Math.min(overX, overY).toInt()
        }

    companion object {

        // TODO add OverScrollCallback and OverPinchCallback.
        // Should notify the user when the boundaries are reached.
        // TODO expose friction parameters, use an interpolator.
        // TODO Make public, add API.
        /**
         * The default overscrolling factor
         */
        private const val DEFAULT_OVERPAN_FACTOR = 0.10f
    }
}