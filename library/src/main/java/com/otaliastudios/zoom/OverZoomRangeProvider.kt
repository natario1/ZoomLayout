package com.otaliastudios.zoom

/**
 * Defines the allowed range for overzoom.
 */
interface OverZoomRangeProvider {

    /**
     * Calculates the maximum overzoom to allow
     *
     * @param engine the zoom engine
     * @param inwards true for inwards, false for outwards
     * @return the maximum overzoom to allow
     */
    @ZoomApi.RealZoom
    fun getOverZoom(engine: ZoomEngine, inwards: Boolean): Float

    companion object {
        private const val DEFAULT_OVERZOOM_FACTOR = 0.1f
        val DEFAULT = object : OverZoomRangeProvider {
            override fun getOverZoom(engine: ZoomEngine, inwards: Boolean): Float {
                return DEFAULT_OVERZOOM_FACTOR * (engine.getMaxZoom() - engine.getMinZoom())
            }
        }
    }

}