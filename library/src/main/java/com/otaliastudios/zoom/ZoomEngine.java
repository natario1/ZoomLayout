package com.otaliastudios.zoom;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Build;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.OverScroller;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;


/**
 * A low level class that listens to touch events and posts zoom and pan updates.
 * The most useful output is a {@link Matrix} that can be used to do pretty much everything,
 * from canvas drawing to View hierarchies translations.
 * <p>
 * Users are required to:
 * - Pass the container view in the constructor
 * - Notify the helper of the content size, using {@link #setContentSize(float, float)}
 * - Pass touch events to {@link #onInterceptTouchEvent(MotionEvent)} and {@link #onTouchEvent(MotionEvent)}
 * <p>
 * This class will apply a base transformation to the content, see {@link #setTransformation(int, int)},
 * so that it is laid out initially as we wish.
 * <p>
 * When the scaling makes the content smaller than our viewport, the engine will always try
 * to keep the content centered.
 */
public final class ZoomEngine implements ZoomApi {

    public static final long DEFAULT_ANIMATION_DURATION = 280;
    // TODO Make public, add API. Use androidx.Interpolator?
    private static final Interpolator INTERPOLATOR = new AccelerateDecelerateInterpolator();

    private static final String TAG = ZoomEngine.class.getSimpleName();
    private static final ZoomLogger LOG = ZoomLogger.create(TAG);

    /**
     * An interface to listen for updates in the inner matrix. This will be called
     * typically on animation frames.
     */
    public interface Listener {

        /**
         * Notifies that the inner matrix was updated. The passed matrix can be changed,
         * but is not guaranteed to be stable. For a long lasting value it is recommended
         * to make a copy of it using {@link Matrix#set(Matrix)}.
         *
         * @param engine the engine hosting the matrix
         * @param matrix a matrix with the given updates
         */
        void onUpdate(@NonNull ZoomEngine engine, @NonNull Matrix matrix);

        /**
         * Notifies that the engine is in an idle state. This means that (most probably)
         * running animations have completed and there are no touch actions in place.
         *
         * @param engine this engine
         */
        void onIdle(@NonNull ZoomEngine engine);
    }

    /**
     * A simple implementation of {@link Listener} that will extract the translation
     * and scale values from the output matrix.
     */
    @SuppressWarnings("unused")
    public abstract static class SimpleListener implements Listener {

        private float[] mMatrixValues = new float[9];

        @Override
        public final void onUpdate(@NonNull ZoomEngine engine, @NonNull Matrix matrix) {
            matrix.getValues(mMatrixValues);
            float panX = mMatrixValues[Matrix.MTRANS_X];
            float panY = mMatrixValues[Matrix.MTRANS_Y];
            float scaleX = mMatrixValues[Matrix.MSCALE_X];
            float scaleY = mMatrixValues[Matrix.MSCALE_Y];
            float scale = (scaleX + scaleY) / 2F; // These should always be equal.
            onUpdate(engine, panX, panY, scale);
        }

        /**
         * Notifies that the engine has computed new updates for some of the pan or scale values.
         *
         * @param engine the engine
         * @param panX the new horizontal pan value
         * @param panY the new vertical pan value
         * @param zoom the new scale value
         */
        abstract void onUpdate(@NonNull ZoomEngine engine, float panX, float panY, float zoom);
    }

    private static final int NONE = 0;
    private static final int SCROLLING = 1;
    private static final int PINCHING = 2;
    private static final int ANIMATING = 3;
    private static final int FLINGING = 4;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({NONE, SCROLLING, PINCHING, ANIMATING, FLINGING})
    private @interface State {
    }

    private ArrayList<Listener> mListeners = new ArrayList<>();
    private Matrix mMatrix = new Matrix();
    private Matrix mOutMatrix = new Matrix();
    @State
    private int mMode = NONE;
    private View mContainer;
    private float mContainerWidth;
    private float mContainerHeight;
    private boolean mInitialized;
    private RectF mTransformedRect = new RectF();
    private RectF mContentRect = new RectF();
    private float mMinZoom = 0.8f;
    private int mMinZoomMode = TYPE_ZOOM;
    private float mMaxZoom = 2.5f;
    private int mMaxZoomMode = TYPE_ZOOM;
    @Zoom
    private float mZoom = 1f; // Not necessarily equal to the matrix scale.
    private float mBaseZoom; // mZoom * mBaseZoom matches the matrix scale.
    private int mTransformation = TRANSFORMATION_CENTER_INSIDE;
    private int mTransformationGravity = Gravity.CENTER;
    private boolean mOverScrollHorizontal = true;
    private boolean mOverScrollVertical = true;
    private boolean mHorizontalPanEnabled = true;
    private boolean mVerticalPanEnabled = true;
    private boolean mOverPinchable = true;
    private boolean mZoomEnabled = true;
    private boolean mClearAnimation;
    private OverScroller mFlingScroller;
    private int[] mTemp = new int[3];
    private long mAnimationDuration = DEFAULT_ANIMATION_DURATION;

    private ScaleGestureDetector mScaleDetector;
    private GestureDetector mFlingDragDetector;

    /**
     * Constructs an helper instance.
     * Deprecated: use {@link #addListener(Listener)} to add a listener.
     *
     * @param context   a valid context
     * @param container the view hosting the zoomable content
     * @param listener  a listener for events
     */
    @Deprecated
    public ZoomEngine(Context context, View container, Listener listener) {
        this(context, container);
        addListener(listener);
    }

    /**
     * Constructs an helper instance.
     * Deprecated: use {@link #addListener(Listener)} to add a listener.
     *
     * @param context   a valid context
     * @param container the view hosting the zoomable content
     */
    public ZoomEngine(@NonNull Context context, @NonNull final View container) {
        mContainer = container;
        mFlingScroller = new OverScroller(context);
        mScaleDetector = new ScaleGestureDetector(context, new PinchListener());
        if (Build.VERSION.SDK_INT >= 19) mScaleDetector.setQuickScaleEnabled(false);
        GestureDetector gestureDetector = new GestureDetector(context, new FlingScrollListener());
        gestureDetector.setOnDoubleTapListener(null);
        mFlingDragDetector = gestureDetector;
        container.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                setContainerSize(container.getWidth(), container.getHeight());
            }
        });
    }

    /**
     * Registers a new {@link Listener} to be notified of matrix updates.
     * @param listener the new listener
     */
    @SuppressWarnings("WeakerAccess")
    public void addListener(@NonNull Listener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    /**
     * Removes a previously registered listener.
     * @param listener the listener to be removed
     */
    @SuppressWarnings("unused")
    public void removeListener(@NonNull Listener listener) {
        mListeners.remove(listener);
    }

    /**
     * Returns the current matrix. This can be changed from the outside, but is not
     * guaranteed to remain stable.
     *
     * @return the current matrix.
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    public Matrix getMatrix() {
        mOutMatrix.set(mMatrix);
        return mOutMatrix;
    }

    private static String ms(@State int mode) {
        switch (mode) {
            case NONE:
                return "NONE";
            case FLINGING:
                return "FLINGING";
            case SCROLLING:
                return "SCROLLING";
            case PINCHING:
                return "PINCHING";
            case ANIMATING:
                return "ANIMATING";
        }
        return "";
    }

    // Returns true if we should go to that mode.
    @SuppressLint("SwitchIntDef")
    private boolean setState(@State int mode) {
        LOG.v("trySetState:", ms(mode));
        if (!mInitialized) return false;
        if (mode == mMode) return true;
        int oldMode = mMode;

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

        // Now that it succeeded, do some cleanup.
        switch (oldMode) {
            case FLINGING:
                mFlingScroller.forceFinished(true);
                break;
            case ANIMATING:
                mClearAnimation = true;
                break;
        }

        LOG.i("setState:", ms(mode));
        mMode = mode;
        return true;
    }

    //region Overscroll

    /**
     * Controls whether the content should be over-scrollable horizontally.
     * If it is, drag and fling horizontal events can scroll the content outside the safe area,
     * then return to safe values.
     *
     * @param overScroll whether to allow horizontal over scrolling
     */
    @Override
    public void setOverScrollHorizontal(boolean overScroll) {
        mOverScrollHorizontal = overScroll;
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
        mOverScrollVertical = overScroll;
    }

    /**
     * Controls whether horizontal panning using gestures is enabled.
     *
     * @param enabled true enables horizontal panning, false disables it
     */
    @Override
    public void setHorizontalPanEnabled(boolean enabled) {
        mHorizontalPanEnabled = enabled;
    }

    /**
     * Controls whether vertical panning using gestures is enabled.
     *
     * @param enabled true enables vertical panning, false disables it
     */
    @Override
    public void setVerticalPanEnabled(boolean enabled) {
        mVerticalPanEnabled = enabled;
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
        mOverPinchable = overPinchable;
    }

    @ScaledPan
    private int getCurrentOverScroll() {
        float overX = (mContainerWidth / 20f) * mZoom;
        float overY = (mContainerHeight / 20f) * mZoom;
        return (int) Math.min(overX, overY);
    }

    @Zoom
    private float getCurrentOverPinch() {
        return 0.1f * (resolveZoom(mMaxZoom, mMaxZoomMode) - resolveZoom(mMinZoom, mMinZoomMode));
    }

    /**
     * Controls whether zoom using pinch gesture is enabled or not.
     *
     * @param enabled true enables zooming, false disables it
     */
    @Override
    public void setZoomEnabled(boolean enabled) {
        mZoomEnabled = enabled;
    }

    //endregion

    //region Initialize

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
        mTransformation = transformation;
        mTransformationGravity = gravity;
    }

    /**
     * Notifies the helper of the content size (be it a child View, a Bitmap, or whatever else).
     * This is needed for the helper to start working.
     *
     * @deprecated use {@link #setContentSize(float, float)} instead.
     */
    @Deprecated
    public void setContentSize(RectF rect) {
        setContentSize(rect.width(), rect.height());
    }

    /**
     * Notifies the helper of the content size (be it a child View, a Bitmap, or whatever else).
     * This is needed for the helper to start working.
     * Shorthand for {@link #setContentSize(float, float, boolean)} without applying transformations.
     *
     * @param width the content width
     * @param height the content height
     */
    @SuppressWarnings("WeakerAccess")
    public void setContentSize(float width, float height) {
        setContentSize(width, height, false);
    }

    /**
     * Notifies the helper of the content size (be it a child View, a Bitmap, or whatever else).
     * This is needed for the helper to start working.
     *
     * @param width the content width
     * @param height the content height
     * @param applyTransformation whether to apply the transformation defined by {@link #setTransformation(int, int)}
     */
    @SuppressWarnings("WeakerAccess")
    public void setContentSize(float width, float height, boolean applyTransformation) {
        if (width <= 0 || height <= 0) return;
        if (mContentRect.width() != width || mContentRect.height() != height || applyTransformation) {
            mContentRect.set(0, 0, width, height);
            onSizeChanged(applyTransformation);
        }
    }

    /**
     * Sets the size of the container view. Normally you don't need to call this because the size
     * is detected from the container passed to the constructor using a global layout listener.
     *
     * However, there are cases where you might want to update it, for example during
     * {@link View#onSizeChanged(int, int, int, int)} (called during shared element transitions).
     *
     * Shorthand for {@link #setContainerSize(float, float, boolean)} with no transformation.
     *
     * @param width the container width
     * @param height the container height
     */
    @SuppressWarnings("WeakerAccess")
    public void setContainerSize(float width, float height) {
        setContainerSize(width, height, false);
    }

    /**
     * Sets the size of the container view. Normally you don't need to call this because the size
     * is detected from the container passed to the constructor using a global layout listener.
     *
     * However, there are cases where you might want to update it, for example during
     * {@link View#onSizeChanged(int, int, int, int)} (called during shared element transitions).
     *
     * @param width the container width
     * @param height the container height
     * @param applyTransformation whether to apply the transformation defined by {@link #setTransformation(int, int)}
     */
    @SuppressWarnings({"WeakerAccess", "unused"})
    public void setContainerSize(float width, float height, boolean applyTransformation) {
        if (width <= 0 || height <= 0) return;
        if (width != mContainerWidth || height != mContainerHeight || applyTransformation) {
            mContainerWidth = width;
            mContainerHeight = height;
            onSizeChanged(applyTransformation);
        }
    }

    private void onSizeChanged(boolean applyTransformation) {
        // We will sync them later using matrix.mapRect.
        mTransformedRect.set(mContentRect);

        if (mContentRect.width() <= 0
                || mContentRect.height() <= 0
                || mContainerWidth <= 0
                || mContainerHeight <= 0) return;

        LOG.w("onSizeChanged:", "containerWidth:", mContainerWidth,
                "containerHeight:", mContainerHeight,
                "contentWidth:", mContentRect.width(),
                "contentHeight:", mContentRect.height());

        // See if we need to apply the transformation. This is the easier case, because
        // if we don't want to apply it, we must do extra computations to keep the appearance unchanged.
        setState(NONE);
        boolean apply = !mInitialized || applyTransformation;
        LOG.w("onSizeChanged: will apply?", apply, "transformation?", mTransformation);
        if (apply) {
            // First time. Apply base zoom, dispatch first event and return.
            mBaseZoom = computeBaseZoom();
            mMatrix.setScale(mBaseZoom, mBaseZoom);
            mMatrix.mapRect(mTransformedRect, mContentRect);
            mZoom = 1f;
            LOG.i("onSizeChanged: newBaseZoom:", mBaseZoom, "newZoom:", mZoom);
            @Zoom float newZoom = ensureScaleBounds(mZoom, false);
            LOG.i("onSizeChanged: scaleBounds:", "we need a zoom correction of", (newZoom - mZoom));
            if (newZoom != mZoom) applyZoom(newZoom, false);

            // pan based on transformation gravity.
            @ScaledPan float[] newPan = computeBasePan();
            @ScaledPan float deltaX = newPan[0] - getScaledPanX();
            @ScaledPan float deltaY = newPan[1] - getScaledPanY();
            if (deltaX != 0 || deltaY != 0) applyScaledPan(deltaX, deltaY, false);

            ensureCurrentTranslationBounds(false);
            dispatchOnMatrix();
            if (!mInitialized) {
                mInitialized = true;
            }
        } else {
            // We were initialized, but some size changed. Since applyTransformation is false,
            // we must do extra work: recompute the baseZoom (since size changed, it makes no sense)
            // but also compute a new zoom such that the real zoom is kept unchanged.
            // So, this method triggers no Matrix updates.
            LOG.i("onSizeChanged: Trying to keep real zoom to", getRealZoom());
            LOG.i("onSizeChanged: oldBaseZoom:", mBaseZoom, "oldZoom:" + mZoom);
            @RealZoom float realZoom = getRealZoom();
            mBaseZoom = computeBaseZoom();
            mZoom = realZoom / mBaseZoom;
            LOG.i("onSizeChanged: newBaseZoom:", mBaseZoom, "newZoom:", mZoom);

            // Now sync the content rect with the current matrix since we are trying to keep it.
            // This is to have consistent values for other calls here.
            mMatrix.mapRect(mTransformedRect, mContentRect);

            // If the new zoom value is invalid, though, we must bring it to the valid place.
            // This is a possible matrix update.
            @Zoom float newZoom = ensureScaleBounds(mZoom, false);
            LOG.i("onSizeChanged: scaleBounds:", "we need a zoom correction of", (newZoom - mZoom));
            if (newZoom != mZoom) applyZoom(newZoom, false);

            // If there was any, pan should be kept. I think there's nothing to do here:
            // If the matrix is kept, and real zoom is kept, then also the real pan is kept.
            // I am not 100% sure of this though, so I prefer to call a useless dispatch.
            ensureCurrentTranslationBounds(false);
            dispatchOnMatrix();
        }
    }

    /**
     * Clears the current state, and stops dispatching matrix events
     * until the view is laid out again and {@link #setContentSize(float, float)}
     * is called.
     */
    public void clear() {
        mContainerHeight = 0;
        mContainerWidth = 0;
        mZoom = 1;
        mBaseZoom = 0;
        mTransformedRect = new RectF();
        mContentRect = new RectF();
        mMatrix = new Matrix();
        mInitialized = false;
    }

    private float computeBaseZoom() {
        switch (mTransformation) {
            case TRANSFORMATION_CENTER_INSIDE: {
                float scaleX = mContainerWidth / mTransformedRect.width();
                float scaleY = mContainerHeight / mTransformedRect.height();
                LOG.v("computeBaseZoom", "centerInside", "scaleX:", scaleX, "scaleY:", scaleY);
                return Math.min(scaleX, scaleY);
            }
            case TRANSFORMATION_CENTER_CROP: {
                float scaleX = mContainerWidth / mTransformedRect.width();
                float scaleY = mContainerHeight / mTransformedRect.height();
                LOG.v("computeBaseZoom", "centerCrop", "scaleX:", scaleX, "scaleY:", scaleY);
                return Math.max(scaleX, scaleY);
            }
            case TRANSFORMATION_NONE:
            default:
                return 1f;
        }
    }

    // TODO support START and END correctly.
    @SuppressLint("RtlHardcoded")
    @ScaledPan
    private float[] computeBasePan() {
        float[] result = new float[]{0f, 0f};
        float extraWidth = mTransformedRect.width() - mContainerWidth;
        float extraHeight = mTransformedRect.height() - mContainerHeight;
        if (extraWidth > 0) {
            // Honour the horizontal gravity indication.
            switch (mTransformationGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                case Gravity.LEFT:
                    result[0] = 0;
                    break;
                case Gravity.CENTER_HORIZONTAL:
                    result[0] = -0.5F * extraWidth;
                    break;
                case Gravity.RIGHT:
                    result[0] = -extraWidth;
                    break;
            }
        }
        if (extraHeight > 0) {
            // Honour the vertical gravity indication.
            switch (mTransformationGravity & Gravity.VERTICAL_GRAVITY_MASK) {
                case Gravity.TOP:
                    result[1] = 0;
                    break;
                case Gravity.CENTER_VERTICAL:
                    result[1] = -0.5F * extraHeight;
                    break;
                case Gravity.BOTTOM:
                    result[1] = -extraHeight;
                    break;
            }
        }
        return result;
    }

    //endregion

    //region Private helpers

    private void dispatchOnMatrix() {
        Matrix matrix = getMatrix();
        for (Listener listener : mListeners) {
            listener.onUpdate(this, matrix);
        }
    }

    private void dispatchOnIdle() {
        for (Listener listener : mListeners) {
            listener.onIdle(this);
        }
    }

    @Zoom
    private float ensureScaleBounds(@Zoom float value, boolean allowOverPinch) {
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
        @ScaledPan float fixX = ensureTranslationBounds(0, true, allowOverScroll);
        @ScaledPan float fixY = ensureTranslationBounds(0, false, allowOverScroll);
        if (fixX != 0 || fixY != 0) {
            mMatrix.postTranslate(fixX, fixY);
            mMatrix.mapRect(mTransformedRect, mContentRect);
        }
    }

    // Checks against the translation value to ensure it is inside our acceptable bounds.
    // If allowOverScroll, overScroll value might be considered to allow "invalid" value.
    @ScaledPan
    private float ensureTranslationBounds(@SuppressWarnings("SameParameterValue") @ScaledPan float delta, boolean horizontal, boolean allowOverScroll) {
        @ScaledPan float value = horizontal ? getScaledPanX() : getScaledPanY();
        float viewSize = horizontal ? mContainerWidth : mContainerHeight;
        @ScaledPan float contentSize = horizontal ? mTransformedRect.width() : mTransformedRect.height();

        boolean overScrollable = horizontal ? mOverScrollHorizontal : mOverScrollVertical;
        @ScaledPan float overScroll = (overScrollable && allowOverScroll) ? getCurrentOverScroll() : 0;
        return getTranslationCorrection(value + delta, viewSize, contentSize, overScroll);
    }

    @ScaledPan
    private float getTranslationCorrection(@ScaledPan float value, float viewSize,
                                           @ScaledPan float contentSize, @ScaledPan float overScroll) {
        @ScaledPan int tolerance = (int) overScroll;
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

    @Zoom
    private float resolveZoom(float zoom, @ZoomType int mode) {
        switch (mode) {
            case TYPE_ZOOM:
                return zoom;
            case TYPE_REAL_ZOOM:
                return zoom / mBaseZoom;
        }
        return -1;
    }

    @ScaledPan
    private float resolvePan(@AbsolutePan float pan) {
        return pan * getRealZoom();
    }

    @AbsolutePan
    private float unresolvePan(@ScaledPan float pan) {
        return pan / getRealZoom();
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
    @SuppressWarnings("WeakerAccess")
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
    @SuppressWarnings("WeakerAccess")
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
            setState(NONE);
            return TOUCH_NO;
        }
    }

    private class PinchListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        private @AbsolutePan
        float mAbsTargetX = 0;
        private @AbsolutePan
        float mAbsTargetY = 0;

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (!mZoomEnabled) {
                return false;
            }

            if (setState(PINCHING)) {
                float eps = 0.0001f;
                if (Math.abs(mAbsTargetX) < eps || Math.abs(mAbsTargetY) < eps) {
                    // We want to interpret this as a scaled value, to work with the *actual* zoom.
                    @ScaledPan float scaledFocusX = -detector.getFocusX();
                    @ScaledPan float scaledFocusY = -detector.getFocusY();
                    LOG.i("onScale:", "Setting focus.", "detectorFocusX:", scaledFocusX, "detectorFocusX:", scaledFocusY);

                    // Account for current pan.
                    scaledFocusX += getScaledPanX();
                    scaledFocusY += getScaledPanY();

                    // Transform to an absolute, scale-independent value.
                    mAbsTargetX = unresolvePan(scaledFocusX);
                    mAbsTargetY = unresolvePan(scaledFocusY);
                    LOG.i("onScale:", "Setting focus.", "absTargetX:", mAbsTargetX, "absTargetY:", mAbsTargetY);
                }

                // Having both overPinch and overScroll is hard to manage, there are lots of bugs if we do.
                float factor = detector.getScaleFactor();
                float newZoom = mZoom * factor;
                applyPinch(newZoom, mAbsTargetX, mAbsTargetY, true);
                return true;
            }
            return false;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            LOG.i("onScaleEnd:", "mAbsTargetX:", mAbsTargetX, "mAbsTargetY:",
                    mAbsTargetY, "mOverPinchable;", mOverPinchable);
            mAbsTargetX = 0;
            mAbsTargetY = 0;
            if (mOverPinchable) {
                // We might have over pinched. Animate back to reasonable value.
                @Zoom float zoom = 0f;
                @Zoom float maxZoom = resolveZoom(mMaxZoom, mMaxZoomMode);
                @Zoom float minZoom = resolveZoom(mMinZoom, mMinZoomMode);
                if (getZoom() < minZoom) zoom = minZoom;
                if (getZoom() > maxZoom) zoom = maxZoom;
                LOG.i("onScaleEnd:", "zoom:", getZoom(), "max:",
                        maxZoom, "min;", minZoom);
                if (zoom > 0) {
                    animateZoom(zoom, true);
                    return;
                }
            }
            setState(NONE);
        }
    }


    private class FlingScrollListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDown(MotionEvent e) {
            return true; // We are interested in the gesture.
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            int vX = (int) (mHorizontalPanEnabled ? velocityX : 0);
            int vY = (int) (mVerticalPanEnabled ? velocityY : 0);
            return startFling(vX, vY);
        }


        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                @AbsolutePan float distanceX, @AbsolutePan float distanceY) {
            if (setState(SCROLLING)) {
                // Allow overScroll. Will be reset in onScrollEnd().
                distanceX = mHorizontalPanEnabled ? -distanceX : 0;
                distanceY = mVerticalPanEnabled ? -distanceY : 0;

                applyScaledPan(distanceX, distanceY, true);
                // applyZoomAndAbsolutePan(getZoom(), getPanX() + distanceX, getPanY() + distanceY, true);
                return true;
            }
            return false;
        }
    }

    private void onScrollEnd() {
        if (mOverScrollHorizontal || mOverScrollVertical) {
            // We might have over scrolled. Animate back to reasonable value.
            @ScaledPan float fixX = ensureTranslationBounds(0, true, false);
            @ScaledPan float fixY = ensureTranslationBounds(0, false, false);
            if (fixX != 0 || fixY != 0) {
                animateScaledPan(fixX, fixY, true);
                return;
            }
        }
        setState(NONE);
    }

    //endregion

    //region Position APIs

    /**
     * A low level API that can animate both zoom and pan at the same time.
     * Zoom might not be the actual matrix scale, see {@link #getZoom()} and {@link #getRealZoom()}.
     * The coordinates are referred to the content size passed in {@link #setContentSize(float, float)}
     * so they do not depend on current zoom.
     *
     * @param zoom    the desired zoom value
     * @param x       the desired left coordinate
     * @param y       the desired top coordinate
     * @param animate whether to animate the transition
     */
    @Override
    public void moveTo(@Zoom float zoom, @AbsolutePan float x, @AbsolutePan float y, boolean animate) {
        if (!mInitialized) return;
        if (animate) {
            animateZoomAndAbsolutePan(zoom, x, y, false);
        } else {
            applyZoomAndAbsolutePan(zoom, x, y, false);
        }
    }

    /**
     * Pans the content until the top-left coordinates match the given x-y
     * values. These are referred to the content size passed in {@link #setContentSize(float, float)},
     * so they do not depend on current zoom.
     *
     * @param x       the desired left coordinate
     * @param y       the desired top coordinate
     * @param animate whether to animate the transition
     */
    @Override
    public void panTo(@AbsolutePan float x, @AbsolutePan float y, boolean animate) {
        panBy(x - getPanX(), y - getPanY(), animate);
    }

    /**
     * Pans the content by the given quantity in dx-dy values.
     * These are referred to the content size passed in {@link #setContentSize(float, float)},
     * so they do not depend on current zoom.
     * <p>
     * In other words, asking to pan by 1 pixel might result in a bigger pan, if the content
     * was zoomed in.
     *
     * @param dx      the desired delta x
     * @param dy      the desired delta y
     * @param animate whether to animate the transition
     */
    @Override
    public void panBy(@AbsolutePan float dx, @AbsolutePan float dy, boolean animate) {
        if (!mInitialized) return;
        if (animate) {
            animateZoomAndAbsolutePan(mZoom, getPanX() + dx, getPanY() + dy, false);
        } else {
            applyZoomAndAbsolutePan(mZoom, getPanX() + dx, getPanY() + dy, false);
        }
    }

    /**
     * Zooms to the given scale. This might not be the actual matrix zoom,
     * see {@link #getZoom()} and {@link #getRealZoom()}.
     *
     * @param zoom    the new scale value
     * @param animate whether to animate the transition
     */
    @Override
    public void zoomTo(@Zoom float zoom, boolean animate) {
        if (!mInitialized) return;
        if (animate) {
            animateZoom(zoom, false);
        } else {
            applyZoom(zoom, false);
        }
    }

    /**
     * Applies the given factor to the current zoom.
     *
     * @param zoomFactor a multiplicative factor
     * @param animate    whether to animate the transition
     */
    @Override
    public void zoomBy(float zoomFactor, boolean animate) {
        @Zoom float newZoom = mZoom * zoomFactor;
        zoomTo(newZoom, animate);
    }


    /**
     * Applies a small, animated zoom-in.
     * Shorthand for {@link #zoomBy(float, boolean)} with factor 1.3.
     */
    @Override
    public void zoomIn() {
        zoomBy(1.3F, true);
    }

    /**
     * Applies a small, animated zoom-out.
     * Shorthand for {@link #zoomBy(float, boolean)} with factor 0.7.
     */
    @Override
    public void zoomOut() {
        zoomBy(0.7F, true);
    }

    /**
     * Animates the actual matrix zoom to the given value.
     *
     * @param realZoom the new real zoom value
     * @param animate  whether to animate the transition
     */
    @Override
    public void realZoomTo(float realZoom, boolean animate) {
        zoomTo(resolveZoom(realZoom, TYPE_REAL_ZOOM), animate);
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
     * @see #TYPE_ZOOM
     * @see #TYPE_REAL_ZOOM
     */
    @Override
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
     * @param minZoom the min zoom
     * @param type    the constraint mode
     * @see #getZoom()
     * @see #getRealZoom()
     */
    @Override
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
     * <p>
     * This can be different than the actual scale you get in the matrix, because at startup
     * we apply a base transformation, see {@link #setTransformation(int, int)}.
     * All zoom calls, including min zoom and max zoom, refer to this axis, where zoom is set to 1
     * right after the initial transformation.
     *
     * @return the current zoom
     * @see #getRealZoom()
     */
    @Override
    @Zoom
    public float getZoom() {
        return mZoom;
    }

    /**
     * Gets the current zoom value, including the base zoom that was eventually applied during
     * the starting transformation, see {@link #setTransformation(int, int)}.
     * This value will match the scaleX - scaleY values you get into the {@link Matrix},
     * and is the actual scale value of the content from its original size.
     *
     * @return the real zoom
     */
    @Override
    @RealZoom
    public float getRealZoom() {
        return mZoom * mBaseZoom;
    }

    /**
     * Returns the current horizontal pan value, in content coordinates
     * (that is, as if there was no zoom at all) referring to what was passed
     * to {@link #setContentSize(float, float)}.
     *
     * @return the current horizontal pan
     */
    @Override
    @AbsolutePan
    public float getPanX() {
        return getScaledPanX() / getRealZoom();
    }

    /**
     * Returns the current vertical pan value, in content coordinates
     * (that is, as if there was no zoom at all) referring to what was passed
     * to {@link #setContentSize(float, float)}.
     *
     * @return the current vertical pan
     */
    @Override
    @AbsolutePan
    public float getPanY() {
        return getScaledPanY() / getRealZoom();
    }

    @ScaledPan
    private float getScaledPanX() {
        return mTransformedRect.left;
    }

    @ScaledPan
    private float getScaledPanY() {
        return mTransformedRect.top;
    }

    //endregion

    //region Apply values

    /**
     * Calls {@link #applyZoom(float, boolean)} repeatedly
     * until the final zoom is reached, interpolating.
     *
     * @param newZoom        the new zoom
     * @param allowOverPinch whether overpinching is allowed
     */
    private void animateZoom(@Zoom float newZoom, final boolean allowOverPinch) {
        newZoom = ensureScaleBounds(newZoom, allowOverPinch);
        if (setState(ANIMATING)) {
            mClearAnimation = false;
            final long startTime = System.currentTimeMillis();
            @Zoom final float startZoom = mZoom;
            @Zoom final float endZoom = newZoom;
            mContainer.post(new Runnable() {
                @Override
                public void run() {
                    if (mClearAnimation) return;
                    float time = interpolateAnimationTime(System.currentTimeMillis() - startTime);
                    LOG.v("animateZoomAndAbsolutePan:", "animationStep:", time);
                    @Zoom float zoom = startZoom + time * (endZoom - startZoom);
                    applyZoom(zoom, allowOverPinch);
                    if (time >= 1f) {
                        setState(NONE);
                    } else {
                        mContainer.postOnAnimation(this);
                    }
                }
            });
        }
    }

    /**
     * Calls {@link #applyZoomAndAbsolutePan(float, float, float, boolean)} repeatedly
     * until the final position is reached, interpolating.
     *
     * @param newZoom         new zoom
     * @param x               final abs pan
     * @param y               final abs pan
     * @param allowOverScroll whether to overscroll
     */
    private void animateZoomAndAbsolutePan(@Zoom float newZoom,
                                           @AbsolutePan final float x, @AbsolutePan final float y,
                                           @SuppressWarnings("SameParameterValue") final boolean allowOverScroll) {
        newZoom = ensureScaleBounds(newZoom, allowOverScroll);
        if (setState(ANIMATING)) {
            mClearAnimation = false;
            final long startTime = System.currentTimeMillis();
            @Zoom final float startZoom = mZoom;
            @Zoom final float endZoom = newZoom;
            @AbsolutePan final float startX = getPanX();
            @AbsolutePan final float startY = getPanY();
            LOG.i("animateZoomAndAbsolutePan:", "starting.", "startX:", startX, "endX:", x, "startY:", startY, "endY:", y);
            LOG.i("animateZoomAndAbsolutePan:", "starting.", "startZoom:", startZoom, "endZoom:", endZoom);
            mContainer.post(new Runnable() {
                @Override
                public void run() {
                    if (mClearAnimation) return;
                    float time = interpolateAnimationTime(System.currentTimeMillis() - startTime);
                    LOG.v("animateZoomAndAbsolutePan:", "animationStep:", time);
                    @Zoom float zoom = startZoom + time * (endZoom - startZoom);
                    @AbsolutePan float targetX = startX + time * (x - startX);
                    @AbsolutePan float targetY = startY + time * (y - startY);
                    applyZoomAndAbsolutePan(zoom, targetX, targetY, allowOverScroll);
                    if (time >= 1f) {
                        setState(NONE);
                    } else {
                        mContainer.postOnAnimation(this);
                    }
                }
            });
        }
    }

    /**
     * Calls {@link #applyScaledPan(float, float, boolean)} repeatedly
     * until the final delta is applied, interpolating.
     *
     * @param deltaX          a scaled delta
     * @param deltaY          a scaled delta
     * @param allowOverScroll whether to overscroll
     */
    private void animateScaledPan(@ScaledPan float deltaX, @ScaledPan float deltaY,
                                  @SuppressWarnings("SameParameterValue") final boolean allowOverScroll) {
        if (setState(ANIMATING)) {
            mClearAnimation = false;
            final long startTime = System.currentTimeMillis();
            @ScaledPan final float startX = getScaledPanX();
            @ScaledPan final float startY = getScaledPanY();
            @ScaledPan final float endX = startX + deltaX;
            @ScaledPan final float endY = startY + deltaY;
            mContainer.post(new Runnable() {
                @Override
                public void run() {
                    if (mClearAnimation) return;
                    float time = interpolateAnimationTime(System.currentTimeMillis() - startTime);
                    LOG.v("animateScaledPan:", "animationStep:", time);
                    @ScaledPan float x = startX + time * (endX - startX);
                    @ScaledPan float y = startY + time * (endY - startY);
                    applyScaledPan(x - getScaledPanX(), y - getScaledPanY(), allowOverScroll);
                    if (time >= 1f) {
                        setState(NONE);
                    } else {
                        mContainer.postOnAnimation(this);
                    }
                }
            });
        }
    }

    @Override
    public void setAnimationDuration(long duration) {
        mAnimationDuration = duration;
    }

    private float interpolateAnimationTime(long delta) {
        float time = Math.min(1, (float) delta / (float) mAnimationDuration);
        return INTERPOLATOR.getInterpolation(time);
    }

    /**
     * Applies the given zoom value, meant as a {@link Zoom} value
     * (so not a {@link RealZoom}).
     * The zoom is applied so that the center point is kept in its place
     *
     * @param newZoom        the new zoom value
     * @param allowOverPinch whether to overpinch
     */
    private void applyZoom(@Zoom float newZoom, boolean allowOverPinch) {
        newZoom = ensureScaleBounds(newZoom, allowOverPinch);
        float scaleFactor = newZoom / mZoom;
        mMatrix.postScale(scaleFactor, scaleFactor,
                mContainerWidth / 2f, mContainerHeight / 2f);
        mMatrix.mapRect(mTransformedRect, mContentRect);
        mZoom = newZoom;
        ensureCurrentTranslationBounds(false);
        dispatchOnMatrix();
    }

    /**
     * Applies both zoom and absolute pan. This is like specifying a position.
     * The semantics of this are that after the position is applied, the zoom corresponds
     * to the given value, getPanX() returns x, getPanY() returns y.
     * <p>
     * Absolute panning is achieved through {@link Matrix#preTranslate(float, float)},
     * which works in the original coordinate system.
     *
     * @param newZoom         the new zoom value
     * @param x               the final left absolute pan
     * @param y               the final top absolute pan
     * @param allowOverScroll whether to overscroll
     */
    private void applyZoomAndAbsolutePan(@Zoom float newZoom,
                                         @AbsolutePan float x, @AbsolutePan float y,
                                         boolean allowOverScroll) {
        // Translation
        @AbsolutePan float deltaX = x - getPanX();
        @AbsolutePan float deltaY = y - getPanY();
        mMatrix.preTranslate(deltaX, deltaY);
        mMatrix.mapRect(mTransformedRect, mContentRect);

        // Scale
        newZoom = ensureScaleBounds(newZoom, false);
        float scaleFactor = newZoom / mZoom;
        // TODO: This used to work but I am not sure about it.
        // mMatrix.postScale(scaleFactor, scaleFactor, getScaledPanX(), getScaledPanY());
        // It keeps the pivot point at the scaled values 0, 0 (see applyPinch).
        // I think we should keep the current top, left.. Let's try:
        mMatrix.postScale(scaleFactor, scaleFactor, 0, 0);
        mMatrix.mapRect(mTransformedRect, mContentRect);
        mZoom = newZoom;

        ensureCurrentTranslationBounds(allowOverScroll);
        dispatchOnMatrix();
    }

    /**
     * Applies the given scaled translation.
     * <p>
     * Scaled translation are applied through {@link Matrix#postTranslate(float, float)},
     * which acts on the actual dimension of the rect.
     *
     * @param deltaX          the x translation
     * @param deltaY          the y translation
     * @param allowOverScroll whether to overScroll
     */
    private void applyScaledPan(@ScaledPan float deltaX, @ScaledPan float deltaY, boolean allowOverScroll) {
        mMatrix.postTranslate(deltaX, deltaY);
        mMatrix.mapRect(mTransformedRect, mContentRect);
        ensureCurrentTranslationBounds(allowOverScroll);
        dispatchOnMatrix();
    }

    /**
     * Helper for pinch gestures. In these cases what we know is the detector focus,
     * and we can use it in {@link Matrix#postScale(float, float, float, float)} to avoid
     * buggy translations.
     *
     * @param newZoom        the new zoom
     * @param targetX        the target X in abs value
     * @param targetY        the target Y in abs value
     * @param allowOverPinch whether to overPinch
     */
    private void applyPinch(@Zoom float newZoom, @AbsolutePan float targetX, @AbsolutePan float targetY,
                            @SuppressWarnings("SameParameterValue") boolean allowOverPinch) {
        // The pivotX and pivotY options of postScale refer (obviously!) to the visible
        // portion of the screen, since the (0,0) point is remapped to be in top-left of the view.
        // The right coordinates to use are the view coordinates.
        // This means we should use scaled coordinates, but remove the current pan.

        @ScaledPan float scaledX = resolvePan(targetX);
        @ScaledPan float scaledY = resolvePan(targetY);
        newZoom = ensureScaleBounds(newZoom, allowOverPinch);
        float scaleFactor = newZoom / mZoom;
        mMatrix.postScale(scaleFactor, scaleFactor,
                getScaledPanX() - scaledX,
                getScaledPanY() - scaledY);

        mMatrix.mapRect(mTransformedRect, mContentRect);
        mZoom = newZoom;
        ensureCurrentTranslationBounds(false);
        dispatchOnMatrix();
    }

    //endregion

    //region Fling

    // Puts min, start and max values in the mTemp array.
    // Since axes are shifted (pans are negative), min values are related to bottom-right,
    // while max values are related to top-left.
    private boolean computeScrollerValues(boolean horizontal) {
        @ScaledPan int currentPan = (int) (horizontal ? getScaledPanX() : getScaledPanY());
        int viewDim = (int) (horizontal ? mContainerWidth : mContainerHeight);
        @ScaledPan int contentDim = (int) (horizontal ? mTransformedRect.width() : mTransformedRect.height());
        int fix = (int) ensureTranslationBounds(0, horizontal, false);
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

    private boolean startFling(@ScaledPan int velocityX, @ScaledPan int velocityY) {
        if (!setState(FLINGING)) return false;

        // Using actual pan values for the scroller.
        // Note: these won't make sense if zoom changes.
        boolean overScrolled;
        overScrolled = computeScrollerValues(true);
        @ScaledPan int minX = mTemp[0];
        @ScaledPan int startX = mTemp[1];
        @ScaledPan int maxX = mTemp[2];
        overScrolled = overScrolled | computeScrollerValues(false);
        @ScaledPan int minY = mTemp[0];
        @ScaledPan int startY = mTemp[1];
        @ScaledPan int maxY = mTemp[2];

        boolean go = overScrolled || mOverScrollHorizontal || mOverScrollVertical || minX < maxX || minY < maxY;
        if (!go) {
            setState(NONE);
            return false;
        }

        @ScaledPan int overScrollX = mOverScrollHorizontal ? getCurrentOverScroll() : 0;
        @ScaledPan int overScrollY = mOverScrollVertical ? getCurrentOverScroll() : 0;
        LOG.i("startFling", "velocityX:", velocityX, "velocityY:", velocityY);
        LOG.i("startFling", "flingX:", "min:", minX, "max:", maxX, "start:", startX, "overScroll:", overScrollY);
        LOG.i("startFling", "flingY:", "min:", minY, "max:", maxY, "start:", startY, "overScroll:", overScrollX);
        mFlingScroller.fling(startX, startY,
                velocityX, velocityY,
                minX, maxX, minY, maxY,
                overScrollX, overScrollY);

        mContainer.post(new Runnable() {
            @Override
            public void run() {
                if (mFlingScroller.isFinished()) {
                    setState(NONE);
                } else if (mFlingScroller.computeScrollOffset()) {
                    @ScaledPan final int newPanX = mFlingScroller.getCurrX();
                    @ScaledPan final int newPanY = mFlingScroller.getCurrY();
                    // OverScroller will eventually go back to our bounds.
                    applyScaledPan(newPanX - getScaledPanX(), newPanY - getScaledPanY(), true);
                    mContainer.postOnAnimation(this);
                }
            }
        });
        return true;
    }

    //endregion

    //region scrollbars helpers

    /**
     * Helper for implementing {@link View#computeHorizontalScrollOffset()}
     * in custom views.
     *
     * @return the horizontal scroll offset.
     */
    @SuppressWarnings("WeakerAccess")
    public int computeHorizontalScrollOffset() {
        return (int) (-1 * getPanX() * getRealZoom());
    }

    /**
     * Helper for implementing {@link View#computeHorizontalScrollRange()}
     * in custom views.
     *
     * @return the horizontal scroll range.
     */
    @SuppressWarnings("WeakerAccess")
    public int computeHorizontalScrollRange() {
        // TODO is this simply mTransformedRect.width? I think so.
        return (int) (mContentRect.width() * getRealZoom());
    }

    /**
     * Helper for implementing {@link View#computeVerticalScrollOffset()}
     * in custom views.
     *
     * @return the vertical scroll offset.
     */
    @SuppressWarnings("WeakerAccess")
    public int computeVerticalScrollOffset() {
        return (int) (-1 * getPanY() * getRealZoom());
    }

    /**
     * Helper for implementing {@link View#computeVerticalScrollRange()}
     * in custom views.
     *
     * @return the vertical scroll range.
     */
    @SuppressWarnings("WeakerAccess")
    public int computeVerticalScrollRange() {
        // TODO is this simply mTransformedRect.height? I think so.
        return (int) (mContentRect.height() * getRealZoom());
    }

    //endregion
}