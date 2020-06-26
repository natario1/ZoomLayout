package com.otaliastudios.zoom

/**
 * Defines the allowed range for overpan.
 */
interface OverPanRangeProvider {

    /**
     * Calculates the maximum overpan range
     *
     * @param engine the zoom engine
     * @param horizontal true when horizontal range should be calculated, false for vertical
     * @return the maximum overpan to allow
     */
    @ZoomApi.ScaledPan
    fun getOverPanRange(engine: ZoomEngine, horizontal: Boolean): Float

    companion object {
        // TODO add OverScrollCallback and OverPinchCallback.
        // Should notify the user when the boundaries are reached.
        // TODO expose friction parameters, use an interpolator.
        // TODO Make public, add API.
        /**
         * The default overscrolling factor
         */
        private const val DEFAULT_OVERPAN_FACTOR = 0.10f
        val DEFAULT = object : OverPanRangeProvider {
            override fun getOverPanRange(engine: ZoomEngine, horizontal: Boolean): Float {
                return engine.containerHeight * DEFAULT_OVERPAN_FACTOR
            }
        }
    }

}