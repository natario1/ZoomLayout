package com.otaliastudios.zoom;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ImageView;


/**
 * Uses {@link ZoomEngine} to allow zooming and pan events to the inner drawable.
 *
 * TODO: support padding (from inside ZoomEngine that gets the view)
 */
@SuppressLint("AppCompatCustomView")
public class ZoomImageView extends ImageView implements ZoomEngine.Listener {

    private final static String TAG = ZoomImageView.class.getSimpleName();

    private ZoomEngine mEngine;
    private Matrix mMatrix = new Matrix();
    private RectF mDrawableRect = new RectF();

    public ZoomImageView(@NonNull Context context) {
        this(context, null);
    }

    public ZoomImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomImageView(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.ZoomEngine, defStyleAttr, 0);
        boolean overScrollable = a.getBoolean(R.styleable.ZoomEngine_overScrollable, true);
        boolean overPinchable = a.getBoolean(R.styleable.ZoomEngine_overPinchable, true);
        float minZoom = a.getFloat(R.styleable.ZoomEngine_minZoom, -1);
        float maxZoom = a.getFloat(R.styleable.ZoomEngine_maxZoom, -1);
        @ZoomEngine.ZoomType int minZoomMode = a.getInteger(
                R.styleable.ZoomEngine_minZoomType, ZoomEngine.TYPE_ZOOM);
        @ZoomEngine.ZoomType int maxZoomMode = a.getInteger(
                R.styleable.ZoomEngine_maxZoomType, ZoomEngine.TYPE_ZOOM);

        a.recycle();

        mEngine = new ZoomEngine(context, this, this);
        mEngine.setOverScrollable(overScrollable);
        mEngine.setOverPinchable(overPinchable);
        if (minZoom > -1) mEngine.setMinZoom(minZoom, minZoomMode);
        if (maxZoom > -1) mEngine.setMaxZoom(maxZoom, maxZoomMode);

        setImageMatrix(mMatrix);
        setScaleType(ScaleType.MATRIX);
    }

    //region Internal

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        init();
    }

    private void init() {
        Drawable drawable = getDrawable();
        if (drawable != null) {
            mDrawableRect.set(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            mEngine.setContentSize(mDrawableRect);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return mEngine.onTouchEvent(ev) || super.onTouchEvent(ev);
    }

    @Override
    public void onUpdate(ZoomEngine helper, Matrix matrix) {
        mMatrix.set(matrix);
        setImageMatrix(mMatrix);
    }

    @Override
    public void onIdle(ZoomEngine engine) {
    }

    //endregion

    //region APIs


    /**
     * Gets the backing {@link ZoomEngine} so you can access its APIs.
     * @return the backing engine
     */
    public ZoomEngine getEngine() {
        return mEngine;
    }

    //endregion
}
