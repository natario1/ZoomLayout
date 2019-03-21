package com.otaliastudios.zoom.internal.movement

import android.annotation.SuppressLint
import android.view.Gravity
import com.otaliastudios.zoom.*

/**
 * Contains:
 *
 * - utilities for transforming zoom
 * - the min and max zoom values
 * - the zoom settings (whether it's enabled or not).
 *
 * Does NOT hold the current zoom value, which is done by the [engine].
 */
internal class ZoomManager(private val engine: ZoomEngine) {

    internal var overZoomEnabled = true
    internal var zoomEnabled = true

    private var minZoom = ZoomApi.MIN_ZOOM_DEFAULT
    private var minZoomMode = ZoomApi.MIN_ZOOM_DEFAULT_TYPE
    private var maxZoom = ZoomApi.MAX_ZOOM_DEFAULT
    private var maxZoomMode = ZoomApi.MAX_ZOOM_DEFAULT_TYPE

    /**
     * Sets the maximum zoom and type allowed.
     */
    internal fun setMaxZoom(maxZoom: Float, @ZoomApi.ZoomType type: Int) {
        if (maxZoom < 0) {
            throw IllegalArgumentException("Max zoom should be >= 0.")
        }
        this.maxZoom = maxZoom
        this.maxZoomMode = type
    }

    /**
     * Sets the minimum zoom and type allowed.
     */
    internal fun setMinZoom(minZoom: Float, @ZoomApi.ZoomType type: Int) {
        if (minZoom < 0) {
            throw IllegalArgumentException("Min zoom should be >= 0")
        }
        this.minZoom = minZoom
        this.minZoomMode = type
    }

    /**
     * The amount of overzoom that is allowed in both directions. This is currently
     * a fixed value, but might be made configurable in the future.
     */
    @ZoomApi.Zoom
    internal val maxOverZoom: Float
        get() = DEFAULT_OVERZOOM_FACTOR * (getMaxZoom() - getMinZoom())


    /**
     * Returns the current minimum zoom as a [ZoomApi.Zoom] value, so not including
     * the transformationZoom.
     */
    @ZoomApi.Zoom
    internal fun getMinZoom(): Float {
        return when (minZoomMode) {
            ZoomApi.TYPE_ZOOM -> minZoom
            ZoomApi.TYPE_REAL_ZOOM -> minZoom / engine.transformationZoom
            else -> throw IllegalArgumentException("Unknown ZoomType $minZoomMode")
        }
    }

    /**
     * Returns the current maximum zoom as a [ZoomApi.Zoom] value, so not including
     * the transformationZoom.
     */
    @ZoomApi.Zoom
    internal fun getMaxZoom(): Float {
        return when (maxZoomMode) {
            ZoomApi.TYPE_ZOOM -> maxZoom
            ZoomApi.TYPE_REAL_ZOOM -> maxZoom / engine.transformationZoom
            else -> throw IllegalArgumentException("Unknown ZoomType $maxZoomMode")
        }
    }

    /**
     * Checks if the passed in zoom level is in expected bounds.
     *
     * @param value the zoom level to check
     * @param allowOverZoom set to true if zoom values within overpinch range should be considered valid
     * @return the zoom level that will lead into a valid state when applied.
     */
    @ZoomApi.Zoom
    internal fun checkBounds(@ZoomApi.Zoom value: Float, allowOverZoom: Boolean): Float {
        var minZoom = getMinZoom()
        var maxZoom = getMaxZoom()
        if (allowOverZoom && overZoomEnabled) {
            minZoom -= maxOverZoom
            maxZoom += maxOverZoom
        }
        return value.coerceIn(minZoom, maxZoom)
    }

    companion object {
        private const val DEFAULT_OVERZOOM_FACTOR = 0.1f
    }
}