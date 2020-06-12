package com.otaliastudios.zoom

/**
 * Defines the allowed range for overzoom.
 */
interface OverZoomRangeProvider {

    /**
     * @return the maximum overzoom to allow
     */
    fun getOverZoomRange(engine: ZoomEngine): Float

}