package com.otaliastudios.zoom

/**
 * Defines the allowed range for overpan.
 */
interface OverPanRangeProvider {

    /**
     * @return the maximum horizontal overpan to allow
     */
    @ZoomApi.ScaledPan
    fun getHorizontalOverPanRange(engine: ZoomEngine): Float

    /**
     * @return the maximum vertical overpan to allow
     */
    @ZoomApi.ScaledPan
    fun getVerticalOverPanRange(engine: ZoomEngine): Float

}