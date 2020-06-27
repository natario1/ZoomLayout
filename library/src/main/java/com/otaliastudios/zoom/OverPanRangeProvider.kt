package com.otaliastudios.zoom

/**
 * Defines the allowed range for overpan.
 */
interface OverPanRangeProvider {

    /**
     * Calculates the maximum overpan
     *
     * @param engine the zoom engine
     * @param horizontal true when horizontal range should be calculated, false for vertical
     * @return the maximum overpan to allow
     */
    @ZoomApi.ScaledPan
    fun getOverPan(engine: ZoomEngine, horizontal: Boolean): Float

    companion object {
        // TODO add OverScrollCallback and OverPinchCallback.
        // Should notify the user when the boundaries are reached.
        // TODO expose friction parameters, use an interpolator.
        // TODO Make public, add API.

        @JvmField
        val DEFAULT = object : OverPanRangeProvider {
            private val DEFAULT_OVERPAN_FACTOR = 0.10f
            override fun getOverPan(engine: ZoomEngine, horizontal: Boolean): Float {
                return when (horizontal) {
                    true -> engine.containerWidth * DEFAULT_OVERPAN_FACTOR
                    false -> engine.containerHeight * DEFAULT_OVERPAN_FACTOR
                }
            }
        }
    }
}