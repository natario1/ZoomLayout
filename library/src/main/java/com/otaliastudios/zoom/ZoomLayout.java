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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;


/**
 * Uses {@link ZoomEngine} to allow zooming and pan events onto a view hierarchy.
 * The hierarchy must be contained in a single view, added to this layout
 * (like what you do with a ScrollView).
 *
 * If the hierarchy has clickable children that should react to touch events, you are
 * required to call {@link #setHasClickableChildren(boolean)} or use the attribute.
 * This is off by default because it is more expensive in terms of performance.
 *
 * Currently padding to this view / margins to the child view are NOT supported.
 *
 * TODO: support padding (from inside ZoomEngine that gets the view)
 * TODO: support layout_margin (here)
 */
public class ZoomLayout extends FrameLayout implements ZoomEngine.Listener {

    private final static String TAG = ZoomLayout.class.getSimpleName();

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
        boolean overScrollable = a.getBoolean(R.styleable.ZoomEngine_overScrollable, true);
        boolean overPinchable = a.getBoolean(R.styleable.ZoomEngine_overPinchable, true);
        boolean hasChildren = a.getBoolean(R.styleable.ZoomEngine_hasClickableChildren, false);
        float minZoom = a.getFloat(R.styleable.ZoomEngine_minZoom, -1);
        float maxZoom = a.getFloat(R.styleable.ZoomEngine_maxZoom, -1);
        a.recycle();

        mEngine = new ZoomEngine(context, this, this);
        mEngine.setOverScrollable(overScrollable);
        mEngine.setOverPinchable(overPinchable);
        if (minZoom > -1) mEngine.setMinZoom(minZoom);
        if (maxZoom > -1) mEngine.setMaxZoom(maxZoom);
        setHasClickableChildren(hasChildren);
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
                    mChildRect.set(0, 0, child.getWidth(), child.getHeight());
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
        return mEngine.onInterceptTouchEvent(ev) || super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return mEngine.onTouchEvent(ev) || super.onTouchEvent(ev);
    }

    @Override
    public void onUpdate(ZoomEngine helper, Matrix matrix) {
        mMatrix.set(matrix);
        if (mHasClickableChildren && getChildCount() > 0) {
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
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (!mHasClickableChildren) {
            int save = canvas.save();
            canvas.setMatrix(mMatrix);
            boolean result = super.drawChild(canvas, child, drawingTime);
            canvas.restoreToCount(save);
            return result;
        } else {
            return super.drawChild(canvas, child, drawingTime);
        }
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
        if (mHasClickableChildren && !hasClickableChildren) {
            // Revert any transformation that was applied to our child.
            if (getChildCount() > 0) {
                View child = getChildAt(0);
                child.setScaleX(0);
                child.setScaleY(0);
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
     * @return the backing engine
     */
    public ZoomEngine getEngine() {
        return mEngine;
    }

    //endregion
}
