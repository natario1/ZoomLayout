package com.otaliastudios.zoom.internal.matrix

import com.otaliastudios.zoom.*

/**
 * Represents an update request.
 * Use [obtain] to create a new one.
 */
internal class MatrixUpdate private constructor(
        internal val zoom: Float,
        internal val isZoomRelative: Boolean,
        internal val canOverZoom: Boolean,
        internal val pan: AbsolutePoint?,
        internal val scaledPan: ScaledPoint?,
        internal val isPanRelative: Boolean,
        internal val canOverPan: Boolean,
        internal val pivotX: Float?,
        internal val pivotY: Float?,
        internal val notify: Boolean
) {

    init {
        if (pan != null && scaledPan != null) {
            throw IllegalStateException("Can only use either pan or scaledPan")
        }
    }

    /**
     * Whether this update updates zoom.
     */
    internal val hasZoom get() = !zoom.isNaN()

    /**
     * Whether this update updates pan.
     */
    internal val hasPan = pan != null || scaledPan != null

    /**
     * Helps constructing a new update.
     */
    internal class Builder {

        private var zoom: Float = Float.NaN
        private var zoomRelative = false
        @Suppress("MemberVisibilityCanBePrivate")
        internal var overZoom: Boolean = false

        private var pan: AbsolutePoint? = null
        private var scaledPan: ScaledPoint? = null
        private var panRelative = false
        internal var overPan: Boolean = false

        private var pivotX: Float? = null
        private var pivotY: Float? = null
        internal var notify: Boolean = true

        internal fun zoomTo(zoom: Float, overZoom: Boolean) {
            this.zoom = zoom
            this.zoomRelative = false
            this.overZoom = overZoom
        }

        @Suppress("unused")
        internal fun zoomBy(zoom: Float, overZoom: Boolean) {
            this.zoom = zoom
            this.zoomRelative = true
            this.overZoom = overZoom
        }

        internal fun panBy(delta: AbsolutePoint?, overPan: Boolean) {
            this.scaledPan = null
            this.pan = delta
            this.panRelative = true
            this.overPan = overPan
        }

        internal fun panTo(pan: AbsolutePoint?, overPan: Boolean) {
            this.scaledPan = null
            this.pan = pan
            this.panRelative = false
            this.overPan = overPan
        }

        internal fun panBy(delta: ScaledPoint?, overPan: Boolean) {
            this.scaledPan = delta
            this.pan = null
            this.panRelative = true
            this.overPan = overPan
        }

        internal fun panTo(pan: ScaledPoint?, overPan: Boolean) {
            this.scaledPan = pan
            this.pan = null
            this.panRelative = false
            this.overPan = overPan
        }

        internal fun pivot(pivotX: Float?, pivotY: Float?) {
            this.pivotX = pivotX
            this.pivotY = pivotY
        }


        /**
         * Builds a new [MatrixUpdate] with the current
         * options set.
         */
        internal fun build(): MatrixUpdate {
            return MatrixUpdate(zoom, zoomRelative, overZoom,
                    pan, scaledPan, panRelative, overPan,
                    pivotX, pivotY, notify)
        }
    }

    companion object {
        private val TAG = MatrixUpdate::class.java.simpleName
        @Suppress("unused")
        private val LOG = ZoomLogger.create(TAG)

        /**
         * Creates a new update by acting on the given [Builder].
         */
        internal fun obtain(builder: Builder.() -> Unit): MatrixUpdate {
            return Builder().apply(builder).build()
        }
    }
}