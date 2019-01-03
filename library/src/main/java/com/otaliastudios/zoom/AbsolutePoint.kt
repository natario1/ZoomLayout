package com.otaliastudios.zoom

/**
 * This class represents an absolute point on the ZoomEngine canvas (or beyond it's bounds)
 */
data class AbsolutePoint(
        @ZoomApi.AbsolutePan var x: Float = 0F,
        @ZoomApi.AbsolutePan var y: Float = 0F) {

    /**
     * Set new coordinates
     *
     * @param x x-axis value
     * @param y y-axis value
     */
    @JvmOverloads
    fun set(@ZoomApi.AbsolutePan x: Float = this.x, @ZoomApi.AbsolutePan y: Float = this.y) {
        this.x = x
        this.y = y
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

}