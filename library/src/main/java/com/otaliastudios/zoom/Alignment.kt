package com.otaliastudios.zoom

object Alignment {

    // Will use one hexadecimal value for each axis, so 16 possible values.
    internal const val MASK = 0xF0     // 1111 0000

    // A special value meaning that the flag for some axis was not set.
    internal const val NO_VALUE = 0x0

    // Vertical
    const val TOP = 0x01               // 0000 0001
    const val BOTTOM = 0x02            // 0000 0010
    const val CENTER_VERTICAL = 0x03   // 0000 0011
    const val NONE_VERTICAL = 0x04     // 0000 0100

    // Horizontal
    const val LEFT = 0x10              // 0001 0000
    const val RIGHT = 0x20             // 0010 0000
    const val CENTER_HORIZONTAL = 0x30 // 0011 0000
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
}