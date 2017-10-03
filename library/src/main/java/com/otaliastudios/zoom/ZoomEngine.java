package com.otaliastudios.zoom;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Build;
import android.support.annotation.IntDef;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.OverScroller;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


/**
 * A low level class that listens to touch events and posts zoom and pan updates.
 * The most useful output is a {@link Matrix} that can be used to do pretty much everything,
 * from canvas drawing to View hierarchies translations.
 *
 * Users are required to:
 * - Pass the container view in the constructor
 * - Notify the helper of the content size, using {@link #setContentSize(RectF)}
 * - Pass touch events to {@link #onInterceptTouchEvent(MotionEvent)} and {@link #onTouchEvent(MotionEvent)}
 *
 * This class will try to keep the content centered. It also starts with a "center inside" policy
 * that will apply a base zoom to the content, so that it fits inside the view container.
 */
public final class ZoomEngine implements ViewTreeObserver.OnGlobalLayoutListener/*, View.OnTouchListener*/ {

    private static final String TAG = ZoomEngine.class.getSimpleName();
    private static final Interpolator INTERPOLATOR = new AccelerateDecelerateInterpolator();
    private static final int ANIMATION_DURATION = 280;
    private static final ZoomLogger LOG = ZoomLogger.create(TAG);

    /**
     * An interface to listen for updates in the inner matrix. This will be called
     * typically on animation frames.
     */
    interface Listener {

        /**
         * Notifies that the inner matrix was updated. The passed matrix can be changed,
         * but is not guaranteed to be stable. For a long lasting value it is recommended
         * to make a copy of it using {@link Matrix#set(Matrix)}.
         *
         * @param engine the engine hosting the matrix
         * @param matrix a matrix with the given updates
         */
        void onUpdate(ZoomEngine engine, Matrix matrix);

        /**
         * Notifies that the engine is in an idle state. This means that (most probably)
         * running animations have completed and there are no touch actions in place.
         *
         * @param engine this engine
         */
        void onIdle(ZoomEngine engine);
    }

    private static final int NONE = 0;
    private static final int SCROLLING = 1;
    private static final int PINCHING = 2;
    private static final int ANIMATING = 3;
    private static final int FLINGING = 4;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({NONE, SCROLLING, PINCHING, ANIMATING, FLINGING})
    private @interface Mode {}

    /**
     * Flag for zoom constraints and settings.
     * With TYPE_ZOOM the constraint is measured over the zoom in {@link #getZoom()}.
     * This is not the actual matrix scale value.
     *
     * @see #getZoom()
     * @see #getRealZoom()
     */
    public static final int TYPE_ZOOM = 0;

    /**
     * Flag for zoom constraints and settings.
     * With TYPE_REAL_ZOOM the constraint is measured over the zoom in {@link #getRealZoom()},
     * which is the actual scale you get in the matrix.
     *
     * @see #getZoom()
     * @see #getRealZoom()
     */
    public static final int TYPE_REAL_ZOOM = 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({TYPE_ZOOM, TYPE_REAL_ZOOM})
    public @interface ZoomType {}

    private View mView;
    private Listener mListener;
    private Matrix mMatrix = new Matrix();
    private Matrix mOutMatrix = new Matrix();
    @Mode private int mMode = NONE;
    private float mViewWidth;
    private float mViewHeight;
    private boolean mInitialized;
    private RectF mContentRect = new RectF();
    private RectF mContentBaseRect = new RectF();
    private float mMinZoom = 0.8f;
    private int mMinZoomMode = TYPE_ZOOM;
    private float mMaxZoom = 2.5f;
    private int mMaxZoomMode = TYPE_ZOOM;
    private float mZoom = 1f; // Not necessarily equal to the matrix scale.
    private float mBaseZoom; // mZoom * mBaseZoom matches the matrix scale.
    private boolean mOverScrollable = true;
    private boolean mOverPinchable = true;
    private boolean mClearAnimation;
    private OverScroller mFlingScroller;
    private int[] mTemp = new int[3];

    private ScaleGestureDetector mScaleDetector;
    private GestureDetector mFlingDragDetector;

    /**
     * Constructs an helper instance.
     *
     * @param context a valid context
     * @param container the view hosting the zoomable content
     * @param listener a listener for events
     */
    public ZoomEngine(Context context, View container, Listener listener) {
        mView = container;
        mListener = listener;

        mFlingScroller = new OverScroller(context);
        mScaleDetector = new ScaleGestureDetector(context, new PinchListener());
        if (Build.VERSION.SDK_INT >= 19) mScaleDetector.setQuickScaleEnabled(false);
        mFlingDragDetector = new GestureDetector(context, new FlingScrollListener());
        container.getViewTreeObserver().addOnGlobalLayoutListener(this);
    }

    /**
     * Returns the current matrix. This can be changed from the outside, but is not
     * guaranteed to remain stable.
     *
     * @return the current matrix.
     */
    public Matrix getMatrix() {
        mOutMatrix.set(mMatrix);
        return mOutMatrix;
    }

    private static String ms(@Mode int mode) {
        switch (mode) {
            case NONE: return "NONE";
            case FLINGING: return "FLINGING";
            case SCROLLING: return "SCROLLING";
            case PINCHING: return "PINCHING";
            case ANIMATING: return "ANIMATING";
        }
        return "";
    }

    // Returns true if we should go to that mode.
    private boolean setMode(@Mode int mode) {
        LOG.i("setMode:", ms(mode));
        if (!mInitialized) return false;
        if (mode == mMode) return true;
        int oldMode = mMode;

        switch (oldMode) {
            case FLINGING:
                mFlingScroller.forceFinished(true);
                break;
            case ANIMATING:
                mClearAnimation = true;
                break;
        }

        switch (mode) {
            case SCROLLING:
                if (oldMode == PINCHING || oldMode == ANIMATING) return false;
                break;
            case FLINGING:
                if (oldMode == ANIMATING) return false;
                break;
            case PINCHING:
                if (oldMode == ANIMATING) return false;
                break;
            case NONE:
                dispatchOnIdle();
                break;
        }
        mMode = mode;
        return true;
    }

    //region Overscroll

    /**
     * Controls whether the content should be overScrollable.
     * If it is, drag and fling events can scroll the content outside the safe area,
     * then return to safe values.
     *
     * @param overScrollable whether to allow over scrolling
     */
    public void setOverScrollable(boolean overScrollable) {
        mOverScrollable = overScrollable;
    }

    /**
     * Controls whether the content should be overPinchable.
     * If it is, pinch events can change the zoom outside the safe bounds,
     * than return to safe values.
     *
     * @param overPinchable whether to allow over pinching
     */
    public void setOverPinchable(boolean overPinchable) {
        mOverPinchable = overPinchable;
    }

    private int getCurrentOverScroll() {
        float overX = (mViewWidth / 20f) * mZoom;
        float overY = (mViewHeight / 20f) * mZoom;
        return (int) Math.min(overX, overY);
    }

    private float getCurrentOverPinch() {
        return 0.1f * (resolveZoom(mMaxZoom, mMaxZoomMode) - resolveZoom(mMinZoom, mMinZoomMode));
    }

    //endregion

    //region Initialize

    @Override
    public void onGlobalLayout() {
        int width = mView.getWidth();
        int height = mView.getHeight();
        if (width <= 0 || height <= 0) return;
        if (width != mViewWidth || height != mViewHeight) {
            init(width, height, mContentBaseRect);
        }
    }

    /**
     * Notifies the helper of the content size (be it a child View, a Bitmap, or whatever else).
     * This is needed for the helper to start working.
     *
     * @param rect the content rect
     */
    public void setContentSize(RectF rect) {
        if (rect.width() <= 0 || rect.height() <= 0) return;
        if (!rect.equals(mContentBaseRect)) {
            init(mViewWidth, mViewHeight, rect);
        }
    }

    private void init(float viewWidth, float viewHeight, RectF rect) {
        mViewWidth = viewWidth;
        mViewHeight = viewHeight;
        mContentBaseRect.set(rect);
        mContentRect.set(rect);

        if (rect.width() <= 0 || rect.height() <= 0 || viewWidth <= 0 || viewHeight <= 0) return;
        LOG.i("init:", "viewWdith:", viewWidth, "viewHeight:", viewHeight,
                "rectWidth:", rect.width(), "rectHeight:", rect.height());

        if (mInitialized) {
            // Content dimensions changed.
            setMode(NONE);

            // Base zoom makes no sense anymore. We must recompute it.
            // We must also compute a new zoom value so that real zoom (that is, the matrix scale)
            // is kept the same as before. (So, no matrix updates here).
            LOG.i("init:", "was initialized. Trying to keep real zoom to", getRealZoom());
            LOG.i("init:", "keepRealZoom: oldBaseZoom:", mBaseZoom, "oldZoom:" + mZoom);
            float realZoom = getRealZoom();
            mBaseZoom = computeBaseZoom();
            mZoom = realZoom / mBaseZoom;
            LOG.i("init:", "keepRealZoom: newBaseZoom:", mBaseZoom, "newZoom:", mZoom);

            // Now sync the content rect with the current matrix since we are trying to keep it.
            // This is to have consistent values for other calls here.
            mMatrix.mapRect(mContentRect, mContentBaseRect);

            // If the new zoom value is invalid, though, we must bring it to the valid place.
            // This is a possible matrix update.
            float newZoom = ensureScaleBounds(mZoom, false);
            LOG.i("init:", "ensureScaleBounds:", "we need a correction of", (newZoom - mZoom));
            if (newZoom != mZoom) moveTo(newZoom, 0, 0, false, false);

            // If there was any, pan should be kept. I think there's nothing to do here:
            // If the matrix is kept, and real zoom is kept, then also the real pan is kept.
            // I am not 100% sure of this though.
            ensureCurrentTranslationBounds(false);
            dispatchOnMatrix();

        } else {
            // First time. Apply base zoom, dispatch first event and return.
            // Auto scale to center-inside.
            mBaseZoom = computeBaseZoom();
            mMatrix.setScale(mBaseZoom, mBaseZoom);
            mMatrix.mapRect(mContentRect, mContentBaseRect);
            mZoom = 1f;

            LOG.i("init:", "was not initialized.", "Setting baseZoom:", mBaseZoom, "zoom:", mZoom);

            ensureCurrentTranslationBounds(false);
            dispatchOnMatrix();
            mInitialized = true;
        }
    }

    /**
     * Clears the current state, and stops dispatching matrix events
     * until the view is laid out again and {@link #setContentSize(RectF)}
     * is called.
     */
    public void clear() {
        mViewHeight = 0;
        mViewWidth = 0;
        mZoom = 1;
        mBaseZoom = 0;
        mContentRect = new RectF();
        mContentBaseRect = new RectF();
        mMatrix = new Matrix();
        mInitialized = false;
    }

    private float computeBaseZoom() {
        float scaleX = mViewWidth / mContentRect.width();
        float scaleY = mViewHeight / mContentRect.height();
        LOG.v("computeBaseZoom", "scaleX:", scaleX, "scaleY:", scaleY);
        return Math.min(scaleX, scaleY);
    }

    //endregion

    //region Private helpers

    private void dispatchOnMatrix() {
        if (mListener != null) mListener.onUpdate(this, getMatrix());
    }

    private void dispatchOnIdle() {
        if (mListener != null) mListener.onIdle(this);
    }

    private float ensureScaleBounds(float value, boolean allowOverPinch) {
        float minZoom = resolveZoom(mMinZoom, mMinZoomMode);
        float maxZoom = resolveZoom(mMaxZoom, mMaxZoomMode);
        if (allowOverPinch && mOverPinchable) {
            minZoom -= getCurrentOverPinch();
            maxZoom += getCurrentOverPinch();
        }
        if (value < minZoom) value = minZoom;
        if (value > maxZoom) value = maxZoom;
        return value;
    }

    private void ensureCurrentTranslationBounds(boolean allowOverScroll) {
        float fixX = ensureTranslationBounds(0, true, allowOverScroll);
        float fixY = ensureTranslationBounds(0, false, allowOverScroll);
        if (fixX != 0 || fixY != 0) {
            mMatrix.postTranslate(fixX, fixY);
            mMatrix.mapRect(mContentRect, mContentBaseRect);
        }
    }

    // Checks against the translation value to ensure it is inside our acceptable bounds.
    // If allowOverScroll, overScroll value might be considered to allow "invalid" value.
    private float ensureTranslationBounds(float delta, boolean width, boolean allowOverScroll) {
        float value = width ? getScaledPanX() : getScaledPanY();
        float viewSize = width ? mViewWidth : mViewHeight;
        float contentSize = width ? mContentRect.width() : mContentRect.height();
        return getTranslationCorrection(value + delta, viewSize, contentSize, allowOverScroll);
    }

    private float getTranslationCorrection(float value, float viewSize, float contentSize, boolean allowOverScroll) {
        int tolerance = (allowOverScroll && mOverScrollable) ? getCurrentOverScroll() : 0;
        float min, max;
        if (contentSize <= viewSize) {
            // If contentSize <= viewSize, we want to stay centered.
            // Need a positive translation, that shows some background.
            min = (viewSize - contentSize) / 2f;
            max = (viewSize - contentSize) / 2f;
        } else {
            // If contentSize is bigger, we just don't want to go outside.
            // Need a negative translation, that hides content.
            min = viewSize - contentSize;
            max = 0;
        }
        min -= tolerance;
        max += tolerance;
        float desired = value;
        if (desired < min) desired = min;
        if (desired > max) desired = max;
        return desired - value;
    }

    private float resolveZoom(float zoom, @ZoomType int mode) {
        switch (mode) {
            case TYPE_ZOOM: return zoom;
            case TYPE_REAL_ZOOM: return zoom / mBaseZoom;
        }
        return -1;
    }

    //endregion

    //region Touch events and Gesture Listeners

    // Might make these public some day?
    private final static int TOUCH_NO = 0;
    private final static int TOUCH_LISTEN = 1;
    private final static int TOUCH_STEAL = 2;

    /**
     * This is required when the content is a View that has clickable hierarchies inside.
     * If true is returned, implementors should not pass the call to super.
     *
     * @param ev the motion event
     * @return whether we want to intercept the event
     */
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return processTouchEvent(ev) > TOUCH_LISTEN;
    }

    /**
     * Process the given touch event.
     * If true is returned, implementors should not pass the call to super.
     *
     * @param ev the motion event
     * @return whether we want to steal the event
     */
    public boolean onTouchEvent(MotionEvent ev) {
        return processTouchEvent(ev) > TOUCH_NO;
    }

    private int processTouchEvent(MotionEvent event) {
        LOG.v("processTouchEvent:", "start.");
        if (mMode == ANIMATING) return TOUCH_STEAL;

        boolean result = mScaleDetector.onTouchEvent(event);
        LOG.v("processTouchEvent:", "scaleResult:", result);

        // Pinch detector always returns true. If we actually started a pinch,
        // Don't pass to fling detector.
        if (mMode != PINCHING) {
            result = result | mFlingDragDetector.onTouchEvent(event);
            LOG.v("processTouchEvent:", "flingResult:", result);
        }

        // Detect scroll ends, this appears to be the only way.
        if (mMode == SCROLLING) {
            int a = event.getActionMasked();
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                LOG.i("processTouchEvent:", "up event while scrolling, dispatching onScrollEnd.");
                onScrollEnd();
            }
        }

        if (result && mMode != NONE) {
            LOG.v("processTouchEvent:", "returning: TOUCH_STEAL");
            return TOUCH_STEAL;
        } else if (result) {
            LOG.v("processTouchEvent:", "returning: TOUCH_LISTEN");
            return TOUCH_LISTEN;
        } else {
            LOG.v("processTouchEvent:", "returning: TOUCH_NO");
            setMode(NONE);
            return TOUCH_NO;
        }
    }

    private class PinchListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (setMode(PINCHING)) {
                float factor = detector.getScaleFactor();
                float desiredDeltaLeft = -(detector.getFocusX() - mViewWidth / 2f);
                float desiredDeltaTop = -(detector.getFocusY() - mViewHeight / 2f);

                // Reduce the pan strength.
                LOG.v("onScale:", "deltaLeft:", desiredDeltaLeft, "deltaTop1:", desiredDeltaTop);
                desiredDeltaLeft /= 4;
                desiredDeltaTop /= 4;

                // Don't pan if we reached the zoom bounds.
                float newZoom = mZoom * factor;
                if (newZoom != ensureScaleBounds(newZoom, true)) {
                    desiredDeltaLeft = 0;
                    desiredDeltaTop = 0;
                }

                // Having both overPinch and overScroll is hard to manage, there are lots of bugs if we do.
                moveTo(newZoom, desiredDeltaLeft, desiredDeltaTop, false, true);
                return true;
            }
            return false;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            if (mOverPinchable) {
                // We might have over pinched. Animate back to reasonable value.
                float zoom = 0f;
                float maxZoom = resolveZoom(mMaxZoom, mMaxZoomMode);
                float minZoom = resolveZoom(mMinZoom, mMinZoomMode);
                if (getZoom() < minZoom) zoom = minZoom;
                if (getZoom() > maxZoom) zoom = maxZoom;
                if (zoom > 0) {
                    animateTo(zoom, 0, 0, false, true);
                    return;
                }
            }
            setMode(NONE);
        }
    }


    private class FlingScrollListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDown(MotionEvent e) {
            return true; // We are interested in the gesture.
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            return startFling((int) velocityX, (int) velocityY);
        }


        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (setMode(SCROLLING)) {
                // Allow overScroll. Will be reset in onScrollEnd().
                moveTo(getZoom(), -distanceX, -distanceY, true, false);
                return true;
            }
            return false;
        }
    }

    private void onScrollEnd() {
        if (mOverScrollable) {
            // We might have over scrolled. Animate back to reasonable value.
            float fixX = ensureTranslationBounds(0, true, false);
            float fixY = ensureTranslationBounds(0, false, false);
            if (fixX != 0 || fixY != 0) {
                animateTo(getZoom(), fixX, fixY, true, false);
                return;
            }
        }
        setMode(NONE);
    }

    //endregion

    //region Position APIs

    /**
     * A low level API that can animate both zoom and pan at the same time.
     * Zoom might not be the actual matrix scale, see {@link #getZoom()} and {@link #getRealZoom()}.
     * The coordinates are referred to the content size passed in {@link #setContentSize(RectF)}
     * so they do not depend on current zoom.
     *
     * @param zoom the desired zoom value
     * @param x the desired left coordinate
     * @param y the desired top coordinate
     * @param animate whether to animate the transition
     */
    public void moveTo(float zoom, float x, float y, boolean animate) {
        if (!mInitialized) return;
        if (animate) {
            animateTo(zoom, x - getPanX(), y - getPanY(), false, false);
        } else {
            moveTo(zoom, x - getPanX(), y - getPanY(), false, false);
        }
    }

    /**
     * Pans the content until the top-left coordinates match the given x-y
     * values. These are referred to the content size passed in {@link #setContentSize(RectF)},
     * so they do not depend on current zoom.
     *
     * @param x the desired left coordinate
     * @param y the desired top coordinate
     * @param animate whether to animate the transition
     */
    public void panTo(float x, float y, boolean animate) {
        panBy(x - getPanX(), y - getPanY(), animate);
    }

    /**
     * Pans the content by the given quantity in dx-dy values.
     * These are referred to the content size passed in {@link #setContentSize(RectF)},
     * so they do not depend on current zoom.
     *
     * In other words, asking to pan by 1 pixel might result in a bigger pan, if the content
     * was zoomed in.
     *
     * @param dx the desired delta x
     * @param dy the desired delta y
     * @param animate whether to animate the transition
     */
    public void panBy(float dx, float dy, boolean animate) {
        dx *= getRealZoom();
        dy *= getRealZoom();
        if (!mInitialized) return;
        if (animate) {
            animateTo(mZoom, dx, dy, false, false);
        } else {
            moveTo(mZoom, dx, dy, false, false);
        }
    }

    /**
     * Zooms to the given scale. This might not be the actual matrix zoom,
     * see {@link #getZoom()} and {@link #getRealZoom()}.
     *
     * @param zoom the new scale value
     * @param animate whether to animate the transition
     */
    public void zoomTo(float zoom, boolean animate) {
        if (!mInitialized) return;
        if (animate) {
            animateTo(zoom, 0, 0, false, false);
        } else {
            moveTo(zoom, 0, 0, false, false);
        }
    }

    /**
     * Applies the given factor to the current zoom.
     *
     * @param zoomFactor a multiplicative factor
     * @param animate whether to animate the transition
     */
    public void zoomBy(float zoomFactor, boolean animate) {
        zoomTo(mZoom * zoomFactor, animate);
    }

    /**
     * Animates the actual matrix zoom to the given value.
     *
     * @param realZoom the new real zoom value
     * @param animate whether to animate the transition
     */
    public void realZoomTo(float realZoom, boolean animate) {
        zoomTo(realZoom / mBaseZoom, animate);
    }

    /**
     * Which is the max zoom that should be allowed.
     * If {@link #setOverPinchable(boolean)} is set to true, this can be over-pinched
     * for a brief time.
     *
     * @see #getZoom()
     * @see #getRealZoom()
     * @see #TYPE_ZOOM
     * @see #TYPE_REAL_ZOOM
     * @param maxZoom the max zoom
     * @param type the constraint mode
     */
    public void setMaxZoom(float maxZoom, @ZoomType int type) {
        if (maxZoom < 0) {
            throw new IllegalArgumentException("Max zoom should be >= 0.");
        }
        mMaxZoom = maxZoom;
        mMaxZoomMode = type;
        if (mZoom > resolveZoom(maxZoom, type)) {
            zoomTo(resolveZoom(maxZoom, type), true);
        }
    }

    /**
     * Which is the min zoom that should be allowed.
     * If {@link #setOverPinchable(boolean)} is set to true, this can be over-pinched
     * for a brief time.
     *
     * @see #getZoom()
     * @see #getRealZoom()
     * @param minZoom the min zoom
     * @param type the constraint mode
     */
    public void setMinZoom(float minZoom, @ZoomType int type) {
        if (minZoom < 0) {
            throw new IllegalArgumentException("Min zoom should be >= 0");
        }
        mMinZoom = minZoom;
        mMinZoomMode = type;
        if (mZoom <= resolveZoom(minZoom, type)) {
            zoomTo(resolveZoom(minZoom, type), true);
        }
    }

    /**
     * Gets the current zoom value, which can be used as a reference when calling
     * {@link #zoomTo(float, boolean)} or {@link #zoomBy(float, boolean)}.
     *
     * This can be different than the actual scale you get in the matrix, because at startup
     * we apply a base zoom to respect the "center inside" policy.
     * All zoom calls, including min zoom and max zoom, refer to this axis, where zoom is set to 1
     * right after the initial transformation.
     *
     * @see #getRealZoom()
     * @return the current zoom
     */
    public float getZoom() {
        return mZoom;
    }

    /**
     * Gets the current zoom value, including the base zoom that was eventually applied when
     * initializing to respect the "center inside" policy. This will match the scaleX - scaleY
     * values you get into the {@link Matrix}, and is the actual scale value of the content
     * from its original size.
     *
     * @return the real zoom
     */
    public float getRealZoom() {
        return mZoom * mBaseZoom;
    }

    /**
     * Returns the current horizontal pan value, in content coordinates
     * (that is, as if there was no zoom at all) referring to what was passed
     * to {@link #setContentSize(RectF)}.
     *
     * @return the current horizontal pan
     */
    public float getPanX() {
        return getScaledPanX() / getRealZoom();
    }

    /**
     * Returns the current vertical pan value, in content coordinates
     * (that is, as if there was no zoom at all) referring to what was passed
     * to {@link #setContentSize(RectF)}.
     *
     * @return the current vertical pan
     */
    public float getPanY() {
        return getScaledPanY() / getRealZoom();
    }

    private float getScaledPanX() {
        return mContentRect.left;
    }

    private float getScaledPanY() {
        return mContentRect.top;
    }

    private void animateTo(float newZoom, final float deltaX, float deltaY,
                           final boolean allowOverScroll, final boolean allowOverPinch) {
        newZoom = ensureScaleBounds(newZoom, allowOverScroll);
        if (setMode(ANIMATING)) {
            mClearAnimation = false;
            final long startTime = System.currentTimeMillis();
            final float startZoom = mZoom;
            final float endZoom = newZoom;
            final float startX = getPanX(); // getScaledPanX();
            final float startY = getPanY(); // getScaledPanY();
            final float endX = startX + deltaX;
            final float endY = startY + deltaY;
            LOG.i("animateTo:", "starting.", "startX:", startX, "endX:", endX, "startY:", startY, "endY:", endY);
            LOG.i("animateTo:", "starting.", "startZoom:", startZoom, "endZoom:", endZoom);
            mView.post(new Runnable() {
                @Override
                public void run() {
                    if (mClearAnimation) return;
                    float time = interpolateAnimationTime(startTime);
                    LOG.v("animateTo:", "animationStep:", time);
                    float zoom = startZoom + time * (endZoom - startZoom);
                    float x = startX + time * (endX - startX);
                    float y = startY + time * (endY - startY);
                    // moveTo(zoom, x - getScaledPanX(), y - getScaledPanY(), allowOverScroll, allowOverPinch);
                    moveTo(zoom, x - getPanX(), y - getPanY(), allowOverScroll, allowOverPinch);
                    if (time >= 1f) {
                        setMode(NONE);
                    } else {
                        mView.postOnAnimation(this);
                    }
                }
            });
        }
    }

    private void moveTo(float newZoom, float deltaX, float deltaY, boolean allowOverScroll, boolean allowOverPinch) {
        // Translation
        mMatrix.postTranslate(deltaX, deltaY);
        mMatrix.mapRect(mContentRect, mContentBaseRect);

        // Scale
        newZoom = ensureScaleBounds(newZoom, allowOverPinch);
        float scaleFactor = newZoom / mZoom;
        mMatrix.postScale(scaleFactor, scaleFactor, mViewWidth / 2f, mViewHeight / 2f);
        mZoom = newZoom;

        ensureCurrentTranslationBounds(allowOverScroll);
        dispatchOnMatrix();
    }

    private float interpolateAnimationTime(long startTime) {
        float time = ((float) (System.currentTimeMillis() - startTime)) / (float) ANIMATION_DURATION;
        time = Math.min(1f, time);
        time = INTERPOLATOR.getInterpolation(time);
        return time;
    }

    //endregion

    //region Fling

    // Puts min, start and max values in the mTemp array.
    // Since axes are shifted (pans are negative), min values are related to bottom-right,
    // while max values are related to top-left.
    private boolean computeScrollerValues(boolean width) {
        int currentPan = (int) (width ? getScaledPanX() : getScaledPanY());
        int viewDim = (int) (width ? mViewWidth : mViewHeight);
        int contentDim = (int) (width ? mContentRect.width() : mContentRect.height());
        int fix = (int) ensureTranslationBounds(0, width, false);
        if (viewDim >= contentDim) {
            // Content is smaller, we are showing some boundary.
            // We can't move in any direction (but we can overScroll).
            mTemp[0] = currentPan + fix;
            mTemp[1] = currentPan;
            mTemp[2] = currentPan + fix;
        } else {
            // Content is bigger, we can move.
            // in this case minPan + viewDim = contentDim
            mTemp[0] = -(contentDim - viewDim);
            mTemp[1] = currentPan;
            mTemp[2] = 0;
        }
        return fix != 0;
    }

    private boolean startFling(int velocityX, int velocityY) {
        if (!setMode(FLINGING)) return false;

        // Using actual pan values for the scroller.
        // Note: these won't make sense if zoom changes.
        boolean overScrolled;
        overScrolled = computeScrollerValues(true);
        int minX = mTemp[0];
        int startX = mTemp[1];
        int maxX = mTemp[2];
        overScrolled = overScrolled | computeScrollerValues(false);
        int minY = mTemp[0];
        int startY = mTemp[1];
        int maxY = mTemp[2];

        boolean go = overScrolled || mOverScrollable || minX < maxX || minY < maxY;
        if (!go) return false;

        int overScroll = mOverScrollable ? getCurrentOverScroll() : 0;
        LOG.i("startFling", "flingX:", "min:", minX, "max:", maxX, "start:", startX, "overScroll:", overScroll);
        LOG.i("startFling", "flingY:", "min:", minY, "max:", maxY, "start:", startY, "overScroll:", overScroll);
        mFlingScroller.fling(startX, startY, velocityX, velocityY,
                minX, maxX, minY, maxY,
                overScroll, overScroll);

        mView.post(new Runnable() {
            @Override
            public void run() {
                if (mFlingScroller.isFinished()) {
                    setMode(NONE);
                } else if (mFlingScroller.computeScrollOffset()) {
                    final int newPanX = mFlingScroller.getCurrX();
                    final int newPanY = mFlingScroller.getCurrY();
                    // OverScroller will eventually go back to our bounds.
                    moveTo(getZoom(), newPanX - getScaledPanX(), newPanY - getScaledPanY(), true, false);
                    mView.postOnAnimation(this);
                }
            }
        });
        return true;
    }

    //endregion
}