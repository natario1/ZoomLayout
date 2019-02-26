package com.otaliastudios.zoom

import android.annotation.SuppressLint
import android.view.Gravity

/**
 * Holds constants for [ZoomApi.setAlignment].
 */
object Alignment {

    // Will use one hexadecimal value for each axis, so 16 possible values.
    internal const val MASK = 0xF0     // 1111 0000

    // A special value meaning that the flag for some axis was not set.
    internal const val NO_VALUE = 0x0

    // Vertical

    /**
     * Aligns top side of the content to the top side of the container.
     */
    const val TOP = 0x01               // 0000 0001

    /**
     * Aligns the bottom side of the content to the bottom side of the container.
     */
    const val BOTTOM = 0x02            // 0000 0010

    /**
     * Centers the content vertically inside the container.
     */
    const val CENTER_VERTICAL = 0x03   // 0000 0011

    /**
     * No forced alignment on the vertical axis.
     */
    const val NONE_VERTICAL = 0x04     // 0000 0100

    // Horizontal

    /**
     * Aligns left side of the content to the left side of the container.
     */
    const val LEFT = 0x10              // 0001 0000

    /**
     * Aligns right side of the content to the right side of the container.
     */
    const val RIGHT = 0x20             // 0010 0000

    /**
     * Centers the content horizontally inside the container.
     */
    const val CENTER_HORIZONTAL = 0x30 // 0011 0000

    /**
     * No forced alignment on the horizontal axis.
     */
    const val NONE_HORIZONTAL = 0x40   // 0100 0000

    // TODO support START and END

    /**
     * Shorthand for [CENTER_HORIZONTAL] and [CENTER_VERTICAL] together.
     */
    const val CENTER = CENTER_VERTICAL or CENTER_HORIZONTAL

    /**
     * Shorthand for [NONE_HORIZONTAL] and [NONE_VERTICAL] together.
     */
    const val NONE = NONE_VERTICAL or NONE_HORIZONTAL

    /**
     * Returns the horizontal alignment for this alignment,
     * or [NO_VALUE] if no value was set.
     */
    internal fun getHorizontal(alignment: Int): Int {
        return alignment and MASK
    }

    /**
     * Returns the vertical alignment for this alignment,
     * or [NO_VALUE] if no value was set.
     */
    internal fun getVertical(alignment: Int): Int {
        return alignment and MASK.inv()
    }

    /**
     * Returns whether this alignment is of 'none' type.
     * In case [alignment] includes both axes, both are required to be 'none' or [NO_VALUE].
     */
    internal fun isNone(alignment: Int): Boolean {
        return alignment == Alignment.NONE
                || alignment == Alignment.NO_VALUE
                || alignment == Alignment.NONE_HORIZONTAL
                || alignment == Alignment.NONE_VERTICAL
    }

    /**
     * Transforms this alignment to a horizontal gravity value.
     */
    @SuppressLint("RtlHardcoded")
    internal fun toHorizontalGravity(alignment: Int, valueIfNone: Int): Int {
        val horizontalAlignment = getHorizontal(alignment)
        return when (horizontalAlignment) {
            Alignment.LEFT -> Gravity.LEFT
            Alignment.RIGHT -> Gravity.RIGHT
            Alignment.CENTER_HORIZONTAL -> Gravity.CENTER_HORIZONTAL
            Alignment.NONE_HORIZONTAL -> valueIfNone
            else -> valueIfNone
        }
    }

    /**
     * Transforms this alignment to a vertical gravity value.
     */
    internal fun toVerticalGravity(alignment: Int, valueIfNone: Int): Int {
        val verticalAlignment = getHorizontal(alignment)
        return when (verticalAlignment) {
            Alignment.TOP -> Gravity.TOP
            Alignment.BOTTOM -> Gravity.BOTTOM
            Alignment.CENTER_VERTICAL -> Gravity.CENTER_VERTICAL
            Alignment.NONE_VERTICAL -> valueIfNone
            else -> valueIfNone
        }
    }
}