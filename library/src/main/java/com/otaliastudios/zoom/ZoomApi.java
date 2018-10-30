package com.otaliastudios.zoom;

import android.graphics.Matrix;
import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


/**
 * An interface for zoom controls.
 */
public interface ZoomApi {

    @Retention(RetentionPolicy.SOURCE)
    @interface RealZoom {
    }

    @Retention(RetentionPolicy.SOURCE)
    @interface Zoom {
    }

    @Retention(RetentionPolicy.SOURCE)
    @interface AbsolutePan {
    }

    @Retention(RetentionPolicy.SOURCE)
    @interface ScaledPan {
    }

    /**
     * Flag for zoom constraints and settings.
     * With TYPE_ZOOM the constraint is measured over the zoom in {@link #getZoom()}.
     * This is not the actual matrix scale value.
     *
     * @see #getZoom()
     * @see #getRealZoom()
     */
    int TYPE_ZOOM = 0;

    /**
     * Flag for zoom constraints and settings.
     * With TYPE_REAL_ZOOM the constraint is measured over the zoom in {@link #getRealZoom()},
     * which is the actual scale you get in the matrix.
     *
     * @see #getZoom()
     * @see #getRealZoom()
     */
    int TYPE_REAL_ZOOM = 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({TYPE_ZOOM, TYPE_REAL_ZOOM})
    @interface ZoomType {
    }

    /**
     * Constant for {@link #setTransformation(int, int)}.
     * The content will be zoomed so that it fits completely inside the container.
     */
    int TRANSFORMATION_CENTER_INSIDE = 0;

    /**
     * Constant for {@link #setTransformation(int, int)}.
     * The content will be zoomed so that its smaller side fits exactly inside the container.
     * The larger side will be partially cropped.
     */
    int TRANSFORMATION_CENTER_CROP = 1;

    /**
     * Constant for {@link #setTransformation(int, int)}.
     * No transformation will be applied, which means that both {@link #getZoom()} and
     * {@link #getRealZoom()} will return the same value.
     */
    int TRANSFORMATION_NONE = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({TRANSFORMATION_CENTER_INSIDE, TRANSFORMATION_CENTER_CROP, TRANSFORMATION_NONE})
    @interface Transformation {
    }

    /**
     * Controls whether the content should be over-scrollable horizontally.
     * If it is, drag and fling horizontal events can scroll the content outside the safe area,
     * then return to safe values.
     *
     * @param overScroll whether to allow horizontal over scrolling
     */
    void setOverScrollHorizontal(boolean overScroll);

    /**
     * Controls whether the content should be over-scrollable vertically.
     * If it is, drag and fling vertical events can scroll the content outside the safe area,
     * then return to safe values.
     *
     * @param overScroll whether to allow vertical over scrolling
     */
    void setOverScrollVertical(boolean overScroll);

    /**
     * Controls whether horizontal panning using gestures is enabled.
     *
     * @param enabled true enables horizontal panning, false disables it
     */
    void setHorizontalPanEnabled(boolean enabled);

    /**
     * Controls whether vertical panning using gestures is enabled.
     *
     * @param enabled true enables vertical panning, false disables it
     */
    void setVerticalPanEnabled(boolean enabled);

    /**
     * Controls whether the content should be overPinchable.
     * If it is, pinch events can change the zoom outside the safe bounds,
     * than return to safe values.
     *
     * @param overPinchable whether to allow over pinching
     */
    void setOverPinchable(boolean overPinchable);

    /**
     * Controls whether zoom using pinch gesture is enabled or not.
     *
     * @param enabled true enables zooming, false disables it
     */
    void setZoomEnabled(boolean enabled);

    /**
     * Sets the base transformation to be applied to the content.
     * Defaults to {@link #TRANSFORMATION_CENTER_INSIDE} with {@link android.view.Gravity#CENTER},
     * which means that the content will be zoomed so that it fits completely inside the container.
     *
     * @param transformation the transformation type
     * @param gravity        the transformation gravity. Might be ignored for some transformations
     */
    void setTransformation(@Transformation int transformation, int gravity);

    /**
     * A low level API that can animate both zoom and pan at the same time.
     * Zoom might not be the actual matrix scale, see {@link #getZoom()} and {@link #getRealZoom()}.
     * The coordinates are referred to the content size so they do not depend on current zoom.
     *
     * @param zoom    the desired zoom value
     * @param x       the desired left coordinate
     * @param y       the desired top coordinate
     * @param animate whether to animate the transition
     */
    void moveTo(@Zoom float zoom, @AbsolutePan float x, @AbsolutePan float y, boolean animate);

    /**
     * Pans the content until the top-left coordinates match the given x-y
     * values. These are referred to the content size so they do not depend on current zoom.
     *
     * @param x       the desired left coordinate
     * @param y       the desired top coordinate
     * @param animate whether to animate the transition
     */
    void panTo(@AbsolutePan float x, @AbsolutePan float y, boolean animate);

    /**
     * Pans the content by the given quantity in dx-dy values.
     * These are referred to the content size so they do not depend on current zoom.
     * <p>
     * In other words, asking to pan by 1 pixel might result in a bigger pan, if the content
     * was zoomed in.
     *
     * @param dx      the desired delta x
     * @param dy      the desired delta y
     * @param animate whether to animate the transition
     */
    void panBy(@AbsolutePan float dx, @AbsolutePan float dy, boolean animate);

    /**
     * Zooms to the given scale. This might not be the actual matrix zoom,
     * see {@link #getZoom()} and {@link #getRealZoom()}.
     *
     * @param zoom    the new scale value
     * @param animate whether to animate the transition
     */
    void zoomTo(@Zoom float zoom, boolean animate);

    /**
     * Applies the given factor to the current zoom.
     *
     * @param zoomFactor a multiplicative factor
     * @param animate    whether to animate the transition
     */
    void zoomBy(float zoomFactor, boolean animate);

    /**
     * Applies a small, animated zoom-in.
     */
    void zoomIn();

    /**
     * Applies a small, animated zoom-out.
     */
    void zoomOut();

    /**
     * Animates the actual matrix zoom to the given value.
     *
     * @param realZoom the new real zoom value
     * @param animate  whether to animate the transition
     */
    void realZoomTo(float realZoom, boolean animate);

    /**
     * Which is the max zoom that should be allowed.
     * If {@link #setOverPinchable(boolean)} is set to true, this can be over-pinched
     * for a brief time.
     *
     * @param maxZoom the max zoom
     * @param type    the constraint mode
     * @see #getZoom()
     * @see #getRealZoom()
     */
    void setMaxZoom(float maxZoom, @ZoomType int type);

    /**
     * Which is the min zoom that should be allowed.
     * If {@link #setOverPinchable(boolean)} is set to true, this can be over-pinched
     * for a brief time.
     *
     * @param minZoom the min zoom
     * @param type    the constraint mode
     * @see #getZoom()
     * @see #getRealZoom()
     */
    void setMinZoom(float minZoom, @ZoomType int type);

    /**
     * Gets the current zoom value, which can be used as a reference when calling
     * {@link #zoomTo(float, boolean)} or {@link #zoomBy(float, boolean)}.
     * <p>
     * This can be different than the actual scale you get in the matrix, because at startup
     * we apply a base zoom to respect the "center inside" policy.
     * All zoom calls, including min zoom and max zoom, refer to this axis, where zoom is set to 1
     * right after the initial transformation.
     *
     * @return the current zoom
     * @see #getRealZoom()
     */
    @Zoom
    float getZoom();

    /**
     * Gets the current zoom value, including the base zoom that was eventually applied when
     * initializing to respect the "center inside" policy. This will match the scaleX - scaleY
     * values you get into the {@link Matrix}, and is the actual scale value of the content
     * from its original size.
     *
     * @return the real zoom
     */
    @RealZoom
    float getRealZoom();

    /**
     * Returns the current horizontal pan value, in content coordinates
     * (that is, as if there was no zoom at all).
     *
     * @return the current horizontal pan
     */
    @AbsolutePan
    float getPanX();

    /**
     * Returns the current vertical pan value, in content coordinates
     * (that is, as if there was no zoom at all).
     *
     * @return the current vertical pan
     */
    @AbsolutePan
    float getPanY();

    /**
     * Sets the duration of animations triggered by zoom and pan APIs.
     * Defaults to {@link ZoomEngine#DEFAULT_ANIMATION_DURATION}.
     *
     * @param duration new animation duration
     */
    void setAnimationDuration(long duration);
}