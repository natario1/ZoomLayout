package com.otaliastudios.zoom

/**
 * This class represents a scaled point on the ZoomEngine canvas (or beyond it's bounds)
 *
 * Note that these values depend on the current zoomlevel
 */
data class ScaledPoint(
        @ZoomApi.ScaledPan var x: Float,
        @ZoomApi.ScaledPan var y: Float) {

    /**
     * Add the given values to this point
     *
     * @param x x-axis offset
     * @param y y-axis offset
     */
    fun offset(@ZoomApi.ScaledPan offsetX: Float = 0F, @ZoomApi.ScaledPan offsetY: Float = 0F) {
        this.x += offsetX
        this.y += offsetY
    }

}