package com.otaliastudios.zoom

import android.graphics.Matrix
import androidx.annotation.IntDef


/**
 * An interface for zoom controls.
 */
interface ZoomApi {

    /**
     * Gets the current zoom value, which can be used as a reference when calling
     * [ZoomApi.zoomTo] or [ZoomApi.zoomBy].
     *
     *
     * This can be different than the actual scale you get in the matrix, because at startup
     * we apply a base zoom to respect the "center inside" policy.
     * All zoom calls, including min zoom and max zoom, refer to this axis, where zoom is set to 1
     * right after the initial transformation.
     *
     * @return the current zoom
     * @see realZoom
     */
    @Zoom
    val zoom: Float

    /**
     * Gets the current zoom value, including the base zoom that was eventually applied when
     * initializing to respect the "center inside" policy. This will match the scaleX - scaleY
     * values you get into the [Matrix], and is the actual scale value of the content
     * from its original size.
     *
     * @return the real zoom
     * @see zoom
     */
    @RealZoom
    val realZoom: Float


    /**
     * The current pan as an [AbsolutePoint].
     * This field will be updated according to current pan when accessed.
     */
    val pan: AbsolutePoint

    /**
     * Returns the current horizontal pan value, in content coordinates
     * (that is, as if there was no zoom at all).
     *
     * @return the current horizontal pan
     */
    @AbsolutePan
    val panX: Float

    /**
     * Returns the current vertical pan value, in content coordinates
     * (that is, as if there was no zoom at all).
     *
     * @return the current vertical pan
     */
    @AbsolutePan
    val panY: Float

    /**
     * Annotation to indicate a RealZoom value.
     *
     * @see realZoom
     */
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    annotation class RealZoom

    /**
     * Annotation to indicate a zoom value.
     *
     * @see zoom
     */
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Zoom

    /**
     * Annotation to indicate an AbsolutePan value.
     *
     * @see panX
     * @see panY
     * @see ScaledPan
     */
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    annotation class AbsolutePan

    /**
     * Annotation to indicate a ScaledPan value.
     *
     * @see panX
     * @see panY
     * @see AbsolutePan
     */
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    annotation class ScaledPan

    /**
     * Defines the available zoom types
     *
     * @see zoom
     * @see realZoom
     */
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(TYPE_ZOOM, TYPE_REAL_ZOOM)
    annotation class ZoomType

    /**
     * Defines the available transformation types
     */
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(TRANSFORMATION_CENTER_INSIDE, TRANSFORMATION_CENTER_CROP, TRANSFORMATION_NONE)
    annotation class Transformation

    /**
     * Defines the available alignments
     */
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(
        com.otaliastudios.zoom.Alignment.BOTTOM,
        com.otaliastudios.zoom.Alignment.CENTER_VERTICAL,
        com.otaliastudios.zoom.Alignment.NONE_VERTICAL,
        com.otaliastudios.zoom.Alignment.TOP,
        com.otaliastudios.zoom.Alignment.LEFT,
        com.otaliastudios.zoom.Alignment.CENTER_HORIZONTAL,
        com.otaliastudios.zoom.Alignment.NONE_HORIZONTAL,
        com.otaliastudios.zoom.Alignment.RIGHT,
        com.otaliastudios.zoom.Alignment.CENTER,
        com.otaliastudios.zoom.Alignment.NONE,
        flag = true
    )
    annotation class Alignment

    /**
     * Controls whether the content should be over-scrollable horizontally.
     * If it is, drag and fling horizontal events can scroll the content outside the safe area,
     * then return to safe values.
     *
     * @param overScroll whether to allow horizontal over scrolling
     */
    fun setOverScrollHorizontal(overScroll: Boolean)

    /**
     * Controls whether the content should be over-scrollable vertically.
     * If it is, drag and fling vertical events can scroll the content outside the safe area,
     * then return to safe values.
     *
     * @param overScroll whether to allow vertical over scrolling
     */
    fun setOverScrollVertical(overScroll: Boolean)

    /**
     * Controls whether horizontal panning using gestures is enabled.
     *
     * @param enabled true enables horizontal panning, false disables it
     */
    fun setHorizontalPanEnabled(enabled: Boolean)

    /**
     * Controls whether vertical panning using gestures is enabled.
     *
     * @param enabled true enables vertical panning, false disables it
     */
    fun setVerticalPanEnabled(enabled: Boolean)

    /**
     * Controls whether the content should be overPinchable.
     * If it is, pinch events can change the zoom outside the safe bounds,
     * than return to safe values.
     *
     * @param overPinchable whether to allow over pinching
     */
    fun setOverPinchable(overPinchable: Boolean)

    /**
     * Controls whether zoom using pinch gesture is enabled or not.
     *
     * @param enabled true enables zooming, false disables it
     */
    fun setZoomEnabled(enabled: Boolean)

    /**
     * Controls whether fling gesture is enabled or not.
     *
     * @param enabled true enables fling gesture, false disables it
     */
    fun setFlingEnabled(enabled: Boolean)

    /**
     * Controls whether fling events are allowed when the view is in an overscrolled state.
     *
     * @param allow true allows fling in overscroll, false disables it
     */
    fun setAllowFlingInOverscroll(allow: Boolean)

    /**
     * Sets the base transformation to be applied to the content.
     * See [setTransformation].
     *
     * @param transformation the transformation type
     */
    fun setTransformation(@Transformation transformation: Int) {
        setTransformation(transformation, TRANSFORMATION_GRAVITY_AUTO)
    }

    /**
     * Sets the base transformation to be applied to the content.
     * Defaults to [TRANSFORMATION_CENTER_INSIDE] with [android.view.Gravity.CENTER],
     * which means that the content will be zoomed so that it fits completely inside the container.
     *
     * @param transformation the transformation type
     * @param gravity        the transformation gravity. Might be ignored for some transformations
     */
    fun setTransformation(@Transformation transformation: Int, gravity: Int)

    /**
     * Sets the content alignment. Can be any of the constants defined in [com.otaliastudios.zoom.Alignment].
     * The content will be aligned and forced to the specified side of the container.
     * Defaults to [ALIGNMENT_DEFAULT].
     *
     * Keep in mind that this is disabled when the content is larger than the container,
     * because a forced alignment in this case would result in part of the content being unreachable.
     *
     * @param alignment the new alignment
     */
    fun setAlignment(@Alignment alignment: Int)

    /**
     * A low level API that can animate both zoom and pan at the same time.
     * Zoom might not be the actual matrix scale, see [ZoomApi.zoom] and [ZoomApi.realZoom].
     * The coordinates are referred to the content size so they do not depend on current zoom.
     *
     * @param zoom    the desired zoom value
     * @param x       the desired left coordinate
     * @param y       the desired top coordinate
     * @param animate whether to animate the transition
     */
    fun moveTo(@Zoom zoom: Float, @AbsolutePan x: Float, @AbsolutePan y: Float, animate: Boolean)

    /**
     * Pans the content until the top-left coordinates match the given x-y
     * values. These are referred to the content size so they do not depend on current zoom.
     *
     * @param x       the desired left coordinate
     * @param y       the desired top coordinate
     * @param animate whether to animate the transition
     */
    fun panTo(@AbsolutePan x: Float, @AbsolutePan y: Float, animate: Boolean)

    /**
     * Pans the content by the given quantity in dx-dy values.
     * These are referred to the content size so they do not depend on current zoom.
     *
     *
     * In other words, asking to pan by 1 pixel might result in a bigger pan, if the content
     * was zoomed in.
     *
     * @param dx      the desired delta x
     * @param dy      the desired delta y
     * @param animate whether to animate the transition
     */
    fun panBy(@AbsolutePan dx: Float, @AbsolutePan dy: Float, animate: Boolean)

    /**
     * Zooms to the given scale. This might not be the actual matrix zoom,
     * see [ZoomApi.zoom] and [ZoomApi.realZoom].
     *
     * @param zoom    the new scale value
     * @param animate whether to animate the transition
     */
    fun zoomTo(@Zoom zoom: Float, animate: Boolean)

    /**
     * Applies the given factor to the current zoom.
     *
     * @param zoomFactor a multiplicative factor
     * @param animate    whether to animate the transition
     */
    fun zoomBy(zoomFactor: Float, animate: Boolean)

    /**
     * Applies a small, animated zoom-in.
     */
    fun zoomIn()

    /**
     * Applies a small, animated zoom-out.
     */
    fun zoomOut()

    /**
     * Animates the actual matrix zoom to the given value.
     *
     * @param realZoom the new real zoom value
     * @param animate  whether to animate the transition
     */
    fun realZoomTo(@RealZoom realZoom: Float, animate: Boolean)

    /**
     * Which is the max zoom that should be allowed.
     * If [ZoomApi.setOverPinchable] is set to true, this can be over-pinched
     * for a brief time.
     *
     * @param maxZoom the max zoom
     * @param type    the constraint mode
     * @see zoom
     * @see realZoom
     */
    fun setMaxZoom(maxZoom: Float, @ZoomType type: Int)

    /**
     * Which is the min zoom that should be allowed.
     * If [ZoomApi.setOverPinchable] is set to true, this can be over-pinched
     * for a brief time.
     *
     * @param minZoom the min zoom
     * @param type    the constraint mode
     * @see zoom
     * @see realZoom
     */
    fun setMinZoom(minZoom: Float, @ZoomType type: Int)

    /**
     * Sets the duration of animations triggered by zoom and pan APIs.
     * Defaults to [ZoomEngine.DEFAULT_ANIMATION_DURATION].
     *
     * @param duration new animation duration
     */
    fun setAnimationDuration(duration: Long)

    companion object {

        /**
         * Flag for zoom constraints and settings.
         * With [ZoomApi.TYPE_ZOOM] the constraint is measured over the zoom in [ZoomApi.zoom].
         * This is not the actual matrix scale value.
         *
         * @see zoom
         * @see realZoom
         */
        const val TYPE_ZOOM = 0

        /**
         * Flag for zoom constraints and settings.
         * With [ZoomApi.TYPE_REAL_ZOOM] the constraint is measured over the zoom in [ZoomApi.realZoom],
         * which is the actual scale you get in the matrix.
         *
         * @see zoom
         * @see realZoom
         */
        const val TYPE_REAL_ZOOM = 1

        /**
         * Constant for [ZoomApi.setTransformation].
         * The content will be zoomed so that it fits completely inside the container.
         */
        const val TRANSFORMATION_CENTER_INSIDE = 0

        /**
         * Constant for [ZoomApi.setTransformation].
         * The content will be zoomed so that its smaller side fits exactly inside the container.
         * The larger side will be partially cropped.
         */
        const val TRANSFORMATION_CENTER_CROP = 1

        /**
         * Constant for [ZoomApi.setTransformation].
         * No transformation will be applied, which means that both [ZoomApi.zoom] and
         * [ZoomApi.realZoom] will return the same value.
         */
        const val TRANSFORMATION_NONE = 2

        /**
         * Constant for [ZoomApi.setTransformation] gravity.
         * This means that the gravity will be inferred from the alignment or
         * fallback to a reasonable default.
         */
        const val TRANSFORMATION_GRAVITY_AUTO = 0

        /**
         * The default [setMinZoom] applied by the engine if none is specified.
         */
        const val MIN_ZOOM_DEFAULT = 0.8F

        /**
         * The default [setMinZoom] type applied by the engine if none is specified.
         */
        const val MIN_ZOOM_DEFAULT_TYPE = TYPE_ZOOM

        /**
         * The default [setMaxZoom] applied by the engine if none is specified.
         */
        const val MAX_ZOOM_DEFAULT = 2.5F

        /**
         * The default [setMaxZoom] type applied by the engine if none is specified.
         */
        const val MAX_ZOOM_DEFAULT_TYPE = TYPE_ZOOM

        /**
         * The default value for [setAlignment].
         */
        const val ALIGNMENT_DEFAULT = com.otaliastudios.zoom.Alignment.CENTER
    }
}