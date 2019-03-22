package com.otaliastudios.zoom

/**
 * This class represents a point on the [ZoomEngine] content surface.
 *
 * It is absolute because it is defined with respect to the content own size & coordinate system,
 * meaning that 0, 0 represents the content top-left corner.
 */
data class AbsolutePoint(
        @ZoomApi.AbsolutePan var x: Float = 0F,
        @ZoomApi.AbsolutePan var y: Float = 0F) {

    /**
     * Copy constructor
     *
     * @param point point to duplicate
     */
    constructor(point: AbsolutePoint) : this(point.x, point.y)

    /**
     * Set new coordinates
     *
     * @param x x-axis value
     * @param y y-axis value
     */
    @JvmOverloads
    fun set(@ZoomApi.AbsolutePan x: Number = this.x, @ZoomApi.AbsolutePan y: Number = this.y) {
        this.x = x.toFloat()
        this.y = y.toFloat()
    }

    /**
     * Set new coordinates
     *
     * @param p the [AbsolutePoint] to copy values from
     */
    fun set(p: AbsolutePoint) {
        set(p.x, p.y)
    }

    /**
     * Substract a point from another point
     *
     * @param absolutePoint the point to substract
     */
    operator fun minus(absolutePoint: AbsolutePoint): AbsolutePoint {
        return AbsolutePoint(this.x - absolutePoint.x, this.y - absolutePoint.y)
    }

    /**
     * Negate a point
     *
     * @return the negative value of this point
     */
    operator fun unaryMinus(): AbsolutePoint {
        return AbsolutePoint(-this.x, -this.y)
    }

    /**
     * Add a point to another point
     *
     * @param absolutePoint the point to add
     */
    operator fun plus(absolutePoint: AbsolutePoint): AbsolutePoint {
        return AbsolutePoint(this.x + absolutePoint.x, this.y + absolutePoint.y)
    }

    /**
     * Multiply every value in the point by a given factor
     *
     * @param factor the factor to use
     * @return the multiplied point
     */
    operator fun times(factor: Number): AbsolutePoint {
        return AbsolutePoint(factor.toFloat() * this.x, factor.toFloat() * this.y)
    }

    /**
     * Returns a [ScaledPoint] for this point, assuming that
     * the current zoom level is [scale].
     */
    internal fun toScaled(scale: Float, outPoint: ScaledPoint = ScaledPoint()): ScaledPoint {
        outPoint.set(x * scale, y * scale)
        return outPoint
    }

}