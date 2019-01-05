package com.otaliastudios.zoom

/**
 * This class represents a scaled point on the ZoomEngine canvas (or beyond it's bounds)
 *
 * Note that these values depend on the current zoomlevel
 */
data class ScaledPoint(
        @ZoomApi.ScaledPan var x: Float = 0F,
        @ZoomApi.ScaledPan var y: Float = 0F) {

    /**
     * Copy constructor
     *
     * @param point point to duplicate
     */
    constructor(point: ScaledPoint) : this(point.x, point.y)

    /**
     * Set new coordinates
     *
     * @param x x-axis value
     * @param y y-axis value
     */
    @JvmOverloads
    fun set(@ZoomApi.ScaledPan x: Number = this.x, @ZoomApi.ScaledPan y: Number = this.y) {
        this.x = x.toFloat()
        this.y = y.toFloat()
    }

    /**
     * Set new coordinates
     *
     * @param p the [ScaledPoint] to copy values from
     */
    fun set(p: ScaledPoint) {
        set(p.x, p.y)
    }

    /**
     * Substract a point from another point
     *
     * @param scaledPoint the point to substract
     */
    operator fun minus(scaledPoint: ScaledPoint): ScaledPoint {
        return ScaledPoint(this.x - scaledPoint.x, this.y - scaledPoint.y)
    }

    /**
     * Negate a point
     *
     * @return the negative value of this point
     */
    operator fun unaryMinus(): ScaledPoint {
        return ScaledPoint(-this.x, -this.y)
    }

    /**
     * Add a point to another point
     *
     * @param scaledPoint the point to add
     */
    operator fun plus(scaledPoint: ScaledPoint): ScaledPoint {
        return ScaledPoint(this.x + scaledPoint.x, this.y + scaledPoint.y)
    }

    /**
     * Multiply every value in the point by a given factor
     *
     * @param factor the factor to use
     * @return the multiplied point
     */
    operator fun times(factor: Number): ScaledPoint {
        return ScaledPoint(factor.toFloat() * this.x, factor.toFloat() * this.y)
    }

}