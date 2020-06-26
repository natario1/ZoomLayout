package com.otaliastudios.zoom

/**
 * Defines the allowed range for overzoom.
 */
interface OverZoomRangeProvider {

    /**
     * @return the maximum inwards overzoom to allow
     */
    @ZoomApi.RealZoom
    fun getOverZoomIn(engine: ZoomEngine): Float

    /**
     * @return the maximum outwards overzoom to allow
     */
    @ZoomApi.RealZoom
    fun getOverZoomOut(engine: ZoomEngine): Float

}