package com.otaliastudios.zoom;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;


/**
 * Uses {@link ZoomEngine} to allow zooming and pan events onto a view hierarchy.
 * The hierarchy must be contained in a single view, added to this layout
 * (like what you do with a ScrollView).
 * <p>
 * If the hierarchy has clickable children that should react to touch events, you are
 * required to call {@link #setHasClickableChildren(boolean)} or use the attribute.
 * This is off by default because it is more expensive in terms of performance.
 * <p>
 * Currently padding to this view / margins to the child view are NOT supported.
 * <p>
 * TODO: support padding (from inside ZoomEngine that gets the view)
 * TODO: support layout_margin (here)
 */
public class ZoomLayout extends FrameLayout implements ZoomEngine.Listener, ZoomApi {

    private final static String TAG = ZoomLayout.class.getSimpleName();
    private final static ZoomLogger LOG = ZoomLogger.create(TAG);

    private ZoomEngine mEngine;
    private Matrix mMatrix = new Matrix();
    private float[] mMatrixValues = new float[9];
    private RectF mChildRect = new RectF();
    private boolean mHasClickableChildren;

    public ZoomLayout(@NonNull Context context) {
        this(context, null);
    }

    public ZoomLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomLayout(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.ZoomEngine, defStyleAttr, 0);
        boolean overScrollHorizontal = a.getBoolean(R.styleable.ZoomEngine_overScrollHorizontal, true);
        boolean overScrollVertical = a.getBoolean(R.styleable.ZoomEngine_overScrollVertical, true);
        boolean horizontalPanEnabled = a.getBoolean(R.styleable.ZoomEngine_horizontalPanEnabled, true);
        boolean verticalPanEnabled = a.getBoolean(R.styleable.ZoomEngine_verticalPanEnabled, true);
        boolean overPinchable = a.getBoolean(R.styleable.ZoomEngine_overPinchable, true);
        boolean zoomEnabled = a.getBoolean(R.styleable.ZoomEngine_zoomEnabled, true);
        boolean hasChildren = a.getBoolean(R.styleable.ZoomEngine_hasClickableChildren, false);
        float minZoom = a.getFloat(R.styleable.ZoomEngine_minZoom, -1);
        float maxZoom = a.getFloat(R.styleable.ZoomEngine_maxZoom, -1);
        @ZoomType int minZoomMode = a.getInteger(R.styleable.ZoomEngine_minZoomType, TYPE_ZOOM);
        @ZoomType int maxZoomMode = a.getInteger(R.styleable.ZoomEngine_maxZoomType, TYPE_ZOOM);
        int transformation = a.getInteger(R.styleable.ZoomEngine_transformation, TRANSFORMATION_CENTER_INSIDE);
        int transformationGravity = a.getInt(R.styleable.ZoomEngine_transformationGravity, Gravity.CENTER);
        a.recycle();

        mEngine = new ZoomEngine(context, this, this);
        setTransformation(transformation, transformationGravity);
        setOverScrollHorizontal(overScrollHorizontal);
        setOverScrollVertical(overScrollVertical);
        setHorizontalPanEnabled(horizontalPanEnabled);
        setVerticalPanEnabled(verticalPanEnabled);
        setOverPinchable(overPinchable);
        setZoomEnabled(zoomEnabled);
        if (minZoom > -1) setMinZoom(minZoom, minZoomMode);
        if (maxZoom > -1) setMaxZoom(maxZoom, maxZoomMode);
        setHasClickableChildren(hasChildren);

        setWillNotDraw(false);
    }

    //region Internal

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        // Measure ourselves as MATCH_PARENT
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        if (widthMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.UNSPECIFIED) {
            throw new RuntimeException(TAG + " must be used with fixed dimensions (e.g. match_parent)");
        }
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(widthSize, heightSize);

        // Measure our child as unspecified.
        int spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        measureChildren(spec, spec);
    }

    @Override
    public void addView(final View child, int index, ViewGroup.LayoutParams params) {
        if (getChildCount() == 0) {
            child.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mChildRect.set(0, 0,
                            child.getWidth(),
                            child.getHeight());
                    mEngine.setContentSize(mChildRect);
                }
            });
            super.addView(child, index, params);
        } else {
            throw new RuntimeException(TAG + " accepts only a single child.");
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return mEngine.onInterceptTouchEvent(ev) || (mHasClickableChildren && super.onInterceptTouchEvent(ev));
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return mEngine.onTouchEvent(ev) || (mHasClickableChildren && super.onTouchEvent(ev));
    }

    @Override
    public void onUpdate(ZoomEngine helper, Matrix matrix) {
        mMatrix.set(matrix);
        if (mHasClickableChildren) {
            if (getChildCount() > 0) {
                View child = getChildAt(0);

                // child.getMatrix().getValues(mMatrixValues);
                // Log.e(TAG, "values 0:" + Arrays.toString(mMatrixValues));
                // mMatrix.getValues(mMatrixValues);
                // Log.e(TAG, "values 1:" + Arrays.toString(mMatrixValues));

                mMatrix.getValues(mMatrixValues);
                child.setPivotX(0);
                child.setPivotY(0);
                child.setTranslationX(mMatrixValues[Matrix.MTRANS_X]);
                child.setTranslationY(mMatrixValues[Matrix.MTRANS_Y]);
                child.setScaleX(mMatrixValues[Matrix.MSCALE_X]);
                child.setScaleY(mMatrixValues[Matrix.MSCALE_Y]);

                // child.getMatrix().getValues(mMatrixValues);
                // Log.e(TAG, "values 2:" + Arrays.toString(mMatrixValues));
            }
        } else {
            invalidate();
        }

        if ((isHorizontalScrollBarEnabled() || isVerticalScrollBarEnabled()) && !awakenScrollBars()) {
            invalidate();
        }
    }

    @Override
    public void onIdle(ZoomEngine engine) {
    }

    @Override
    protected int computeHorizontalScrollOffset() {
        return (int) (-1 * mEngine.getPanX() * mEngine.getRealZoom());
    }

    @Override
    protected int computeHorizontalScrollRange() {
        return (int) (mChildRect.width() * mEngine.getRealZoom());
    }

    @Override
    protected int computeVerticalScrollOffset() {
        return (int) (-1 * mEngine.getPanY() * mEngine.getRealZoom());
    }

    @Override
    protected int computeVerticalScrollRange() {
        return (int) (mChildRect.height() * mEngine.getRealZoom());
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean result;

        if (!mHasClickableChildren) {
            int save = canvas.save();
            canvas.setMatrix(mMatrix);
            result = super.drawChild(canvas, child, drawingTime);
            canvas.restoreToCount(save);
        } else {
            result = super.drawChild(canvas, child, drawingTime);
        }

        return result;
    }

    //endregion

    //region APIs

    /**
     * Whether the view hierarchy inside has (or will have) clickable children.
     * This is false by default.
     *
     * @param hasClickableChildren whether we have clickable children
     */
    public void setHasClickableChildren(boolean hasClickableChildren) {
        LOG.i("setHasClickableChildren:", "old:", mHasClickableChildren, "new:", hasClickableChildren);
        if (mHasClickableChildren && !hasClickableChildren) {
            // Revert any transformation that was applied to our child.
            if (getChildCount() > 0) {
                View child = getChildAt(0);
                child.setScaleX(1);
                child.setScaleY(1);
                child.setTranslationX(0);
                child.setTranslationY(0);
            }
        }
        mHasClickableChildren = hasClickableChildren;

        // Update if we were laid out already.
        if (getWidth() > 0 && getHeight() > 0) {
            if (mHasClickableChildren) {
                onUpdate(mEngine, mMatrix);
            } else {
                invalidate();
            }
        }
    }

    /**
     * Gets the backing {@link ZoomEngine} so you can access its APIs.
     *
     * @return the backing engine
     */
    public ZoomEngine getEngine() {
        return mEngine;
    }

    //endregion

    //region ZoomApis

    /**
     * Controls whether the content should be over-scrollable horizontally.
     * If it is, drag and fling horizontal events can scroll the content outside the safe area,
     * then return to safe values.
     *
     * @param overScroll whether to allow horizontal over scrolling
     */
    @Override
    public void setOverScrollHorizontal(boolean overScroll) {
        getEngine().setOverScrollHorizontal(overScroll);
    }

    /**
     * Controls whether the content should be over-scrollable vertically.
     * If it is, drag and fling vertical events can scroll the content outside the safe area,
     * then return to safe values.
     *
     * @param overScroll whether to allow vertical over scrolling
     */
    @Override
    public void setOverScrollVertical(boolean overScroll) {
        getEngine().setOverScrollVertical(overScroll);
    }

    /**
     * Controls whether horizontal panning using gestures is enabled.
     *
     * @param enabled true enables horizontal panning, false disables it
     */
    @Override
    public void setHorizontalPanEnabled(boolean enabled) {
        getEngine().setHorizontalPanEnabled(enabled);
    }

    /**
     * Controls whether vertical panning using gestures is enabled.
     *
     * @param enabled true enables vertical panning, false disables it
     */
    @Override
    public void setVerticalPanEnabled(boolean enabled) {
        getEngine().setVerticalPanEnabled(enabled);
    }

    /**
     * Controls whether the content should be overPinchable.
     * If it is, pinch events can change the zoom outside the safe bounds,
     * than return to safe values.
     *
     * @param overPinchable whether to allow over pinching
     */
    @Override
    public void setOverPinchable(boolean overPinchable) {
        getEngine().setOverPinchable(overPinchable);
    }

    /**
     * Controls whether zoom using pinch gesture is enabled or not.
     *
     * @param enabled true enables zooming, false disables it
     */
    @Override
    public void setZoomEnabled(boolean enabled) {
        getEngine().setZoomEnabled(enabled);
    }

    /**
     * Sets the base transformation to be applied to the content.
     * Defaults to {@link #TRANSFORMATION_CENTER_INSIDE} with {@link Gravity#CENTER},
     * which means that the content will be zoomed so that it fits completely inside the container.
     *
     * @param transformation the transformation type
     * @param gravity        the transformation gravity. Might be ignored for some transformations
     */
    @Override
    public void setTransformation(int transformation, int gravity) {
        getEngine().setTransformation(transformation, gravity);
    }

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
    @Override
    public void moveTo(float zoom, float x, float y, boolean animate) {
        getEngine().moveTo(zoom, x, y, animate);
    }

    /**
     * Pans the content until the top-left coordinates match the given x-y
     * values. These are referred to the content size so they do not depend on current zoom.
     *
     * @param x       the desired left coordinate
     * @param y       the desired top coordinate
     * @param animate whether to animate the transition
     */
    @Override
    public void panTo(float x, float y, boolean animate) {
        getEngine().panTo(x, y, animate);
    }

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
    @Override
    public void panBy(float dx, float dy, boolean animate) {
        getEngine().panBy(dx, dy, animate);
    }

    /**
     * Zooms to the given scale. This might not be the actual matrix zoom,
     * see {@link #getZoom()} and {@link #getRealZoom()}.
     *
     * @param zoom    the new scale value
     * @param animate whether to animate the transition
     */
    @Override
    public void zoomTo(float zoom, boolean animate) {
        getEngine().zoomTo(zoom, animate);
    }

    /**
     * Applies the given factor to the current zoom.
     *
     * @param zoomFactor a multiplicative factor
     * @param animate    whether to animate the transition
     */
    @Override
    public void zoomBy(float zoomFactor, boolean animate) {
        getEngine().zoomBy(zoomFactor, animate);
    }

    /**
     * Applies a small, animated zoom-in.
     */
    @Override
    public void zoomIn() {
        getEngine().zoomIn();
    }

    /**
     * Applies a small, animated zoom-out.
     */
    @Override
    public void zoomOut() {
        getEngine().zoomOut();
    }

    /**
     * Animates the actual matrix zoom to the given value.
     *
     * @param realZoom the new real zoom value
     * @param animate  whether to animate the transition
     */
    @Override
    public void realZoomTo(float realZoom, boolean animate) {
        getEngine().realZoomTo(realZoom, animate);
    }

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
    @Override
    public void setMaxZoom(float maxZoom, int type) {
        getEngine().setMaxZoom(maxZoom, type);
    }

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
    @Override
    public void setMinZoom(float minZoom, int type) {
        getEngine().setMinZoom(minZoom, type);
    }

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
    @Override
    public float getZoom() {
        return getEngine().getZoom();
    }

    /**
     * Gets the current zoom value, including the base zoom that was eventually applied when
     * initializing to respect the "center inside" policy. This will match the scaleX - scaleY
     * values you get into the {@link Matrix}, and is the actual scale value of the content
     * from its original size.
     *
     * @return the real zoom
     */
    @Override
    public float getRealZoom() {
        return getEngine().getRealZoom();
    }

    /**
     * Returns the current horizontal pan value, in content coordinates
     * (that is, as if there was no zoom at all).
     *
     * @return the current horizontal pan
     */
    @Override
    public float getPanX() {
        return getEngine().getPanX();
    }

    /**
     * Returns the current vertical pan value, in content coordinates
     * (that is, as if there was no zoom at all).
     *
     * @return the current vertical pan
     */
    @Override
    public float getPanY() {
        return getEngine().getPanY();
    }

    //endregion
}
