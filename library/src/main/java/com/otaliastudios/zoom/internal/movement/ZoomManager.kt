package com.otaliastudios.zoom.internal.movement

import com.otaliastudios.zoom.OverZoomRangeProvider
import com.otaliastudios.zoom.ZoomApi
import com.otaliastudios.zoom.ZoomEngine
import com.otaliastudios.zoom.ZoomLogger
import com.otaliastudios.zoom.internal.matrix.MatrixController
import java.lang.IllegalStateException

/**
 * Contains:
 *
 * - utilities for transforming zoom
 * - the min and max zoom values
 * - the zoom settings (whether it's enabled or not).
 *
 * Does NOT hold the current zoom value, which is done by the [MatrixController].
 * Holds the current [transformationZoom] so we can convert zoom types.
 */
internal class ZoomManager(
        private val engine: ZoomEngine,
        provider: () -> MatrixController) : MovementManager(provider) {

    internal var transformationZoom = 0F

    var minZoom = ZoomApi.MIN_ZOOM_DEFAULT
    var minZoomMode = ZoomApi.MIN_ZOOM_DEFAULT_TYPE
    var maxZoom = ZoomApi.MAX_ZOOM_DEFAULT
    var maxZoomMode = ZoomApi.MAX_ZOOM_DEFAULT_TYPE

    internal var overZoomRangeProvider: OverZoomRangeProvider = OverZoomRangeProvider.DEFAULT

    override var isEnabled = true
    override var isOverEnabled = true

    /**
     * Clears the current variable state, that is,
     * resets [transformationZoom].
     */
    override fun clear() {
        transformationZoom = 0F
    }

    /**
     * Transforms a [ZoomApi.RealZoom] into a [ZoomApi.Zoom].
     */
    @ZoomApi.Zoom
    internal fun realZoomToZoom(@ZoomApi.RealZoom realZoom: Float): Float {
        return realZoom / transformationZoom
    }

    /**
     * Transforms a [ZoomApi.Zoom] into a [ZoomApi.RealZoom].
     */
    @ZoomApi.RealZoom
    internal fun zoomToRealZoom(@ZoomApi.Zoom zoom: Float): Float {
        return zoom * transformationZoom
    }

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
     * The amount of overzoom that is allowed in inwards direction.
     * This value is calculated by the [overZoomRangeProvider].
     */
    @ZoomApi.RealZoom
    internal val maxOverZoomIn: Float
        get() {
            var value = overZoomRangeProvider.getOverZoom(engine, inwards = true)
            if (value < 0F) {
                LOG.w("Received negative maxOverZoomIn value, coercing to 0")
                value = value.coerceAtLeast(0F)
            }
            return value
        }

    /**
     * The amount of overzoom that is allowed in outwards direction.
     * This value is calculated by the [overZoomRangeProvider].
     */
    @ZoomApi.RealZoom
    internal val maxOverZoomOut: Float
        get() {
            var value = overZoomRangeProvider.getOverZoom(engine, inwards = false)
            if (value < 0F) {
                LOG.w("Received negative maxOverZoomOut value, coercing to 0")
                value = value.coerceAtLeast(0F)
            }
            return value
        }

    /**
     * Returns the current minimum zoom as a [ZoomApi.RealZoom] value.
     */
    @ZoomApi.RealZoom
    internal fun getMinZoom(): Float {
        return when (minZoomMode) {
            ZoomApi.TYPE_REAL_ZOOM -> minZoom
            ZoomApi.TYPE_ZOOM -> zoomToRealZoom(minZoom)
            else -> throw IllegalArgumentException("Unknown ZoomType $minZoomMode")
        }
    }

    /**
     * Returns the current maximum zoom as a [ZoomApi.RealZoom] value.
     */
    @ZoomApi.RealZoom
    internal fun getMaxZoom(): Float {
        return when (maxZoomMode) {
            ZoomApi.TYPE_REAL_ZOOM -> maxZoom
            ZoomApi.TYPE_ZOOM -> zoomToRealZoom(maxZoom)
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
    @ZoomApi.RealZoom
    internal fun checkBounds(@ZoomApi.RealZoom value: Float, allowOverZoom: Boolean): Float {
        var minZoom = getMinZoom()
        var maxZoom = getMaxZoom()
        if (allowOverZoom && isOverEnabled) {
            minZoom -= maxOverZoomOut
            maxZoom += maxOverZoomIn
        }

        if (maxZoom < minZoom) {
            if (maxZoomMode == minZoomMode) {
                throw IllegalStateException("maxZoom is less than minZoom: $maxZoom < $minZoom")
            } else {
                // align REAL_ZOOM value to ZOOM value
                if (maxZoomMode == ZoomApi.TYPE_ZOOM) {
                    minZoom = maxZoom
                } else {
                    maxZoom = minZoom
                }
            }
        }

        return value.coerceIn(minZoom, maxZoom)
    }

    companion object {

        private val TAG = ZoomManager::class.java.simpleName
        private val LOG = ZoomLogger.create(TAG)

    }

}