package com.otaliastudios.zoom

import android.animation.*
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.os.Build
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.OverScroller
import androidx.annotation.IntDef
import com.otaliastudios.zoom.ZoomApi.*


/**
 * A low level class that listens to touch events and posts zoom and pan updates.
 * The most useful output is a [Matrix] that can be used to do pretty much everything,
 * from canvas drawing to View hierarchies translations.
 *
 *
 * Users are required to:
 * - Pass the container view in the constructor
 * - Notify the helper of the content size, using [setContentSize]
 * - Pass touch events to [onInterceptTouchEvent] and [onTouchEvent]
 *
 *
 * This class will apply a base transformation to the content, see [setTransformation],
 * so that it is laid out initially as we wish.
 *
 *
 * When the scaling makes the content smaller than our viewport, the engine will always try
 * to keep the content centered.
 */
class ZoomEngine
/**
 * Constructs an helper instance.
 *
 * @param context   a valid context
 */
internal constructor(context: Context) : ViewTreeObserver.OnGlobalLayoutListener, ZoomApi {

    /**
     * Constructs an helper instance.
     *
     * @param context   a valid context
     * @param container the view hosting the zoomable content
     **/
    constructor(context: Context, container: View) : this(context) {
        setContainer(container)
    }

    /**
     * Constructs an helper instance.
     * Deprecated: use [addListener] to add a listener.
     *
     * @param context   a valid context
     * @param container the view hosting the zoomable content
     * @param listener a listener for events
     **/
    @Deprecated("Use [addListener] to add a listener.",
            replaceWith = ReplaceWith("constructor(context, container)"))
    constructor(context: Context, container: View, listener: Listener) : this(context, container) {
        addListener(listener)
    }

    private val mListeners = mutableListOf<Listener>()
    private var mMatrix = Matrix()
    private val mOutMatrix = Matrix()
    @State
    private var mState = NONE
    private lateinit var mContainer: View
    private var mContainerWidth = 0F
    private var mContainerHeight = 0F
    private var mInitialized = false
    private var mTransformedRect = RectF()
    private var mContentRect = RectF()
    private var mMinZoom = 0.8f
    private var mMinZoomMode = ZoomApi.TYPE_ZOOM
    private var mMaxZoom = 2.5f
    private var mMaxZoomMode = ZoomApi.TYPE_ZOOM

    /**
     * Gets the current zoom value, which can be used as a reference when calling
     * [zoomTo] or [zoomBy].
     *
     *
     * This can be different than the actual scale you get in the matrix, because at startup
     * we apply a base transformation, see [setTransformation].
     * All zoom calls, including min zoom and max zoom, refer to this axis, where zoom is set to 1
     * right after the initial transformation.
     *
     * @return the current zoom
     * @see realZoom
     */
    @Zoom
    override var zoom = 1f // Not necessarily equal to the matrix scale.
        private set
    private var mBaseZoom = 0.toFloat() // mZoom * mBaseZoom matches the matrix scale.
    private var mTransformation = ZoomApi.TRANSFORMATION_CENTER_INSIDE
    private var mTransformationGravity = Gravity.CENTER
    private var mOverScrollHorizontal = true
    private var mOverScrollVertical = true
    private var mHorizontalPanEnabled = true
    private var mVerticalPanEnabled = true
    private var mOverPinchable = true
    private var mZoomEnabled = true
    private var mFlingEnabled = true
    private var mAllowFlingInOverscroll = false
    private var mClearAnimation = false
    private val mFlingScroller = OverScroller(context)
    private var mAnimationDuration = DEFAULT_ANIMATION_DURATION

    private val mScaleDetector = ScaleGestureDetector(context, PinchListener())
    private val mFlingDragDetector = GestureDetector(context, FlingScrollListener())

    /**
     * Returns the current matrix. This can be changed from the outside, but is not
     * guaranteed to remain stable.
     *
     * @return the current matrix.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    val matrix: Matrix
        get() {
            mOutMatrix.set(mMatrix)
            return mOutMatrix
        }

    @ScaledPan
    private val maxOverScroll: Int
        get() {
            val overX = mContainerWidth * DEFAULT_OVERSCROLL_FACTOR
            val overY = mContainerHeight * DEFAULT_OVERSCROLL_FACTOR
            return Math.min(overX, overY).toInt()
        }

    @Zoom
    private val maxOverPinch: Float
        get() = 0.1f * (getMaxZoom() - getMinZoom())

    /**
     * Gets the current zoom value, including the base zoom that was eventually applied during
     * the starting transformation, see [setTransformation].
     * This value will match the scaleX - scaleY values you get into the [Matrix],
     * and is the actual scale value of the content from its original size.
     *
     * @return the real zoom
     */
    @RealZoom
    override val realZoom: Float
        get() = zoom * mBaseZoom

    /**
     * The current pan as an [AbsolutePoint]
     */
    private val pan: AbsolutePoint
        get() = AbsolutePoint(panX, panY)

    /**
     * Returns the current horizontal pan value, in content coordinates
     * (that is, as if there was no zoom at all) referring to what was passed
     * to [setContentSize].
     *
     * @return the current horizontal pan
     */
    @AbsolutePan
    override val panX: Float
        get() = scaledPanX / realZoom

    /**
     * Returns the current vertical pan value, in content coordinates
     * (that is, as if there was no zoom at all) referring to what was passed
     * to [setContentSize].
     *
     * @return the current vertical pan
     */
    @AbsolutePan
    override val panY: Float
        get() = scaledPanY / realZoom

    /**
     * The current pan as a [ScaledPoint]
     */
    private val scaledPan: ScaledPoint
        get() = ScaledPoint(scaledPanX, scaledPanY)

    @ScaledPan
    private val scaledPanX: Float
        get() = mTransformedRect.left

    @ScaledPan
    private val scaledPanY: Float
        get() = mTransformedRect.top

    private val mScrollerValuesX = ScrollerValues()
    private val mScrollerValuesY = ScrollerValues()

    /**
     * An interface to listen for updates in the inner matrix. This will be called
     * typically on animation frames.
     */
    interface Listener {

        /**
         * Notifies that the inner matrix was updated. The passed matrix can be changed,
         * but is not guaranteed to be stable. For a long lasting value it is recommended
         * to make a copy of it using [Matrix.set].
         *
         * @param engine the engine hosting the matrix
         * @param matrix a matrix with the given updates
         */
        fun onUpdate(engine: ZoomEngine, matrix: Matrix)

        /**
         * Notifies that the engine is in an idle state. This means that (most probably)
         * running animations have completed and there are no touch actions in place.
         *
         * @param engine this engine
         */
        fun onIdle(engine: ZoomEngine)
    }

    /**
     * A simple implementation of [Listener] that will extract the translation
     * and scale values from the output matrix.
     */
    @Suppress("unused")
    abstract class SimpleListener : Listener {

        private val mMatrixValues = FloatArray(9)

        override fun onUpdate(engine: ZoomEngine, matrix: Matrix) {
            matrix.getValues(mMatrixValues)
            val panX = mMatrixValues[Matrix.MTRANS_X]
            val panY = mMatrixValues[Matrix.MTRANS_Y]
            val scaleX = mMatrixValues[Matrix.MSCALE_X]
            val scaleY = mMatrixValues[Matrix.MSCALE_Y]
            val scale = (scaleX + scaleY) / 2f // These should always be equal.
            onUpdate(engine, panX, panY, scale)
        }

        /**
         * Notifies that the engine has computed new updates for some of the pan or scale values.
         *
         * @param engine the engine
         * @param panX the new horizontal pan value
         * @param panY the new vertical pan value
         * @param zoom the new scale value
         */
        internal abstract fun onUpdate(engine: ZoomEngine, panX: Float, panY: Float, zoom: Float)
    }

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(NONE, SCROLLING, PINCHING, ANIMATING, FLINGING)
    private annotation class State

    init {
        if (Build.VERSION.SDK_INT >= 19) mScaleDetector.isQuickScaleEnabled = false
        mFlingDragDetector.setOnDoubleTapListener(null)
    }

    /**
     * Registers a new [Listener] to be notified of matrix updates.
     * @param listener the new listener
     */
    fun addListener(listener: Listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener)
        }
    }

    /**
     * Removes a previously registered listener.
     * @param listener the listener to be removed
     */
    @Suppress("unused")
    fun removeListener(listener: Listener) {
        mListeners.remove(listener)
    }

    // Returns true if we should go to that mode.
    @SuppressLint("SwitchIntDef")
    private fun setState(@State state: Int): Boolean {
        LOG.v("trySetState:", getStateName(state))
        if (!mInitialized) return false
        if (state == mState) return true
        val oldMode = mState

        when (state) {
            SCROLLING -> if (oldMode == PINCHING || oldMode == ANIMATING) return false
            FLINGING -> if (oldMode == ANIMATING) return false
            PINCHING -> if (oldMode == ANIMATING) return false
            NONE -> dispatchOnIdle()
        }

        // Now that it succeeded, do some cleanup.
        when (oldMode) {
            FLINGING -> mFlingScroller.forceFinished(true)
            ANIMATING -> mClearAnimation = true
        }

        LOG.i("setState:", getStateName(state))
        mState = state
        return true
    }

    /**
     * Set a container to perform transformations on.
     * This method should only be called once at initialization time.
     *
     * @param container view
     */
    internal fun setContainer(container: View) {
        mContainer = container
        mContainer.viewTreeObserver.addOnGlobalLayoutListener(this)
    }

    //region Overscroll

    /**
     * Controls whether the content should be over-scrollable horizontally.
     * If it is, drag and fling horizontal events can scroll the content outside the safe area,
     * then return to safe values.
     *
     * @param overScroll whether to allow horizontal over scrolling
     */
    override fun setOverScrollHorizontal(overScroll: Boolean) {
        mOverScrollHorizontal = overScroll
    }

    /**
     * Controls whether the content should be over-scrollable vertically.
     * If it is, drag and fling vertical events can scroll the content outside the safe area,
     * then return to safe values.
     *
     * @param overScroll whether to allow vertical over scrolling
     */
    override fun setOverScrollVertical(overScroll: Boolean) {
        mOverScrollVertical = overScroll
    }

    /**
     * Controls whether horizontal panning using gestures is enabled.
     *
     * @param enabled true enables horizontal panning, false disables it
     */
    override fun setHorizontalPanEnabled(enabled: Boolean) {
        mHorizontalPanEnabled = enabled
    }

    /**
     * Controls whether vertical panning using gestures is enabled.
     *
     * @param enabled true enables vertical panning, false disables it
     */
    override fun setVerticalPanEnabled(enabled: Boolean) {
        mVerticalPanEnabled = enabled
    }

    /**
     * Controls whether the content should be overPinchable.
     * If it is, pinch events can change the zoom outside the safe bounds,
     * than return to safe values.
     *
     * @param overPinchable whether to allow over pinching
     */
    override fun setOverPinchable(overPinchable: Boolean) {
        mOverPinchable = overPinchable
    }

    /**
     * Controls whether zoom using pinch gesture is enabled or not.
     *
     * @param enabled true enables zooming, false disables it
     */
    override fun setZoomEnabled(enabled: Boolean) {
        mZoomEnabled = enabled
    }

    /**
     * Controls whether fling gesture is enabled or not.
     *
     * @param enabled true enables fling gesture, false disables it
     */
    override fun setFlingEnabled(enabled: Boolean) {
        mFlingEnabled = enabled
    }

    /**
     * Controls whether fling events are allowed when the view is in an overscrolled state.
     *
     * @param allow true allows fling in overscroll, false disables it
     */
    override fun setAllowFlingInOverscroll(allow: Boolean) {
        mAllowFlingInOverscroll = allow
    }

    //endregion

    //region Initialize

    /**
     * Sets the base transformation to be applied to the content.
     * Defaults to [ZoomApi.TRANSFORMATION_CENTER_INSIDE] with [Gravity.CENTER],
     * which means that the content will be zoomed so that it fits completely inside the container.
     *
     * @param transformation the transformation type
     * @param gravity        the transformation gravity. Might be ignored for some transformations
     */
    override fun setTransformation(transformation: Int, gravity: Int) {
        mTransformation = transformation
        mTransformationGravity = gravity
    }

    override fun onGlobalLayout() {
        setContainerSize(mContainer.width.toFloat(), mContainer.height.toFloat())
    }

    /**
     * Notifies the helper of the content size (be it a child View, a Bitmap, or whatever else).
     * This is needed for the helper to start working.
     *
     * @param rect the content rect
     */
    @Deprecated("Deprecated in favor of not using RectF class",
            replaceWith = ReplaceWith("setContentSize(width, height)"))
    fun setContentSize(rect: RectF) {
        setContentSize(rect.width(), rect.height())
    }


    /**
     * Notifies the helper of the content size (be it a child View, a Bitmap, or whatever else).
     * This is needed for the helper to start working.
     *
     * @param width the content width
     * @param height the content height
     * @param applyTransformation whether to apply the transformation defined by [setTransformation]
     */
    @JvmOverloads
    fun setContentSize(width: Float, height: Float, applyTransformation: Boolean = false) {
        if (width <= 0 || height <= 0) return
        if (mContentRect.width() != width || mContentRect.height() != height || applyTransformation) {
            mContentRect.set(0f, 0f, width, height)
            onSizeChanged(applyTransformation)
        }
    }

    /**
     * Sets the size of the container view. Normally you don't need to call this because the size
     * is detected from the container passed to the constructor using a global layout listener.
     *
     * However, there are cases where you might want to update it, for example during
     * [View.onSizeChanged] (called during shared element transitions).
     *
     * @param width the container width
     * @param height the container height
     * @param applyTransformation whether to apply the transformation defined by [setTransformation]
     */
    @JvmOverloads
    fun setContainerSize(width: Float, height: Float, applyTransformation: Boolean = false) {
        if (width <= 0 || height <= 0) return
        if (width != mContainerWidth || height != mContainerHeight || applyTransformation) {
            mContainerWidth = width
            mContainerHeight = height
            onSizeChanged(applyTransformation)
        }
    }

    private fun onSizeChanged(applyTransformation: Boolean) {
        // We will sync them later using matrix.mapRect.
        mTransformedRect.set(mContentRect)

        if (mContentRect.width() <= 0
                || mContentRect.height() <= 0
                || mContainerWidth <= 0
                || mContainerHeight <= 0)
            return

        LOG.w("onSizeChanged:", "containerWidth:", mContainerWidth,
                "containerHeight:", mContainerHeight,
                "contentWidth:", mContentRect.width(),
                "contentHeight:", mContentRect.height())

        // See if we need to apply the transformation. This is the easier case, because
        // if we don't want to apply it, we must do extra computations to keep the appearance unchanged.
        setState(NONE)
        val apply = !mInitialized || applyTransformation
        LOG.w("onSizeChanged: will apply?", apply, "transformation?", mTransformation)
        if (apply) {
            // First time. Apply base zoom, dispatch first event and return.
            mBaseZoom = computeBaseZoom()
            mMatrix.setScale(mBaseZoom, mBaseZoom)
            mMatrix.mapRect(mTransformedRect, mContentRect)
            zoom = 1f
            LOG.i("onSizeChanged: newBaseZoom:", mBaseZoom, "newZoom:", zoom)
            @Zoom val newZoom = checkZoomBounds(zoom, false)
            LOG.i("onSizeChanged: scaleBounds:", "we need a zoom correction of", newZoom - zoom)
            if (newZoom != zoom) applyZoom(newZoom, allowOverPinch = false)

            // pan based on transformation gravity.
            @ScaledPan val newPan = computeBasePan()
            @ScaledPan val deltaX = newPan[0] - scaledPanX
            @ScaledPan val deltaY = newPan[1] - scaledPanY
            if (deltaX != 0f || deltaY != 0f) applyScaledPan(deltaX, deltaY, false)

            ensurePanBounds(allowOverScroll = false)
            dispatchOnMatrix()
            if (!mInitialized) {
                mInitialized = true
            }
        } else {
            // We were initialized, but some size changed. Since applyTransformation is false,
            // we must do extra work: recompute the baseZoom (since size changed, it makes no sense)
            // but also compute a new zoom such that the real zoom is kept unchanged.
            // So, this method triggers no Matrix updates.
            LOG.i("onSizeChanged: Trying to keep real zoom to", realZoom)
            LOG.i("onSizeChanged: oldBaseZoom:", mBaseZoom, "oldZoom:$zoom")
            @RealZoom val realZoom = realZoom
            mBaseZoom = computeBaseZoom()
            zoom = realZoom / mBaseZoom
            LOG.i("onSizeChanged: newBaseZoom:", mBaseZoom, "newZoom:", zoom)

            // Now sync the content rect with the current matrix since we are trying to keep it.
            // This is to have consistent values for other calls here.
            mMatrix.mapRect(mTransformedRect, mContentRect)

            // If the new zoom value is invalid, though, we must bring it to the valid place.
            // This is a possible matrix update.
            @Zoom val newZoom = checkZoomBounds(zoom, false)
            LOG.i("onSizeChanged: scaleBounds:", "we need a zoom correction of", newZoom - zoom)
            if (newZoom != zoom) applyZoom(newZoom, allowOverPinch = false)

            // If there was any, pan should be kept. I think there's nothing to do here:
            // If the matrix is kept, and real zoom is kept, then also the real pan is kept.
            // I am not 100% sure of this though, so I prefer to call a useless dispatch.
            ensurePanBounds(allowOverScroll = false)
            dispatchOnMatrix()
        }
    }

    /**
     * Clears the current state, and stops dispatching matrix events
     * until the view is laid out again and [ZoomEngine.setContentSize]
     * is called.
     */
    fun clear() {
        mContainerHeight = 0f
        mContainerWidth = 0f
        zoom = 1f
        mBaseZoom = 0f
        mTransformedRect = RectF()
        mContentRect = RectF()
        mMatrix = Matrix()
        mInitialized = false
    }

    private fun computeBaseZoom(): Float {
        when (mTransformation) {
            ZoomApi.TRANSFORMATION_CENTER_INSIDE -> {
                val scaleX = mContainerWidth / mTransformedRect.width()
                val scaleY = mContainerHeight / mTransformedRect.height()
                LOG.v("computeBaseZoom", "centerInside", "scaleX:", scaleX, "scaleY:", scaleY)
                return Math.min(scaleX, scaleY)
            }
            ZoomApi.TRANSFORMATION_CENTER_CROP -> {
                val scaleX = mContainerWidth / mTransformedRect.width()
                val scaleY = mContainerHeight / mTransformedRect.height()
                LOG.v("computeBaseZoom", "centerCrop", "scaleX:", scaleX, "scaleY:", scaleY)
                return Math.max(scaleX, scaleY)
            }
            ZoomApi.TRANSFORMATION_NONE -> return 1f
            else -> return 1f
        }
    }

    // TODO support START and END correctly.
    @SuppressLint("RtlHardcoded")
    @ScaledPan
    private fun computeBasePan(): FloatArray {
        val result = floatArrayOf(0f, 0f)
        val extraWidth = mTransformedRect.width() - mContainerWidth
        val extraHeight = mTransformedRect.height() - mContainerHeight
        if (extraWidth > 0) {
            // Honour the horizontal gravity indication.
            when (mTransformationGravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
                Gravity.LEFT -> result[0] = 0f
                Gravity.CENTER_HORIZONTAL -> result[0] = -0.5f * extraWidth
                Gravity.RIGHT -> result[0] = -extraWidth
            }
        }
        if (extraHeight > 0) {
            // Honour the vertical gravity indication.
            when (mTransformationGravity and Gravity.VERTICAL_GRAVITY_MASK) {
                Gravity.TOP -> result[1] = 0f
                Gravity.CENTER_VERTICAL -> result[1] = -0.5f * extraHeight
                Gravity.BOTTOM -> result[1] = -extraHeight
            }
        }
        return result
    }

    //endregion

    //region Private helpers

    private fun dispatchOnMatrix() {
        mListeners.forEach {
            it.onUpdate(this, matrix)
        }
    }

    private fun dispatchOnIdle() {
        mListeners.forEach {
            it.onIdle(this)
        }
    }

    /**
     * Checks if the passed in zoom level is in expected bounds.
     *
     * @param value the zoom level to check
     * @param allowOverPinch set to true if zoom values within overpinch range should be considered valid
     * @return the zoom level that will lead into a valid state when applied.
     */
    @Zoom
    private fun checkZoomBounds(@Zoom value: Float, allowOverPinch: Boolean): Float {
        var minZoom = getMinZoom()
        var maxZoom = getMaxZoom()
        if (allowOverPinch && mOverPinchable) {
            minZoom -= maxOverPinch
            maxZoom += maxOverPinch
        }

        return value.coerceIn(minZoom, maxZoom)
    }

    /**
     * Uses [checkPanBounds] to calculate the pan correction that
     * needs to be applied to get into a valid state.
     *
     * @return the scaled pan fix values for x- and y-axis
     */
    private fun getPanCorrection(): ScaledPoint {
        return ScaledPoint(
                checkPanBounds(horizontal = true, allowOverScroll = false),
                checkPanBounds(horizontal = false, allowOverScroll = false)
        )
    }

    /**
     * Checks the current pan state.
     *
     * @param horizontal true when checking horizontal pan, false for vertical
     * @param allowOverScroll set to true if pan values within overscroll range should be considered valid
     * @return 0 if we are in a valid state, otherwise the pan correction to be applied to get into a valid state again.
     */
    @ScaledPan
    private fun checkPanBounds(horizontal: Boolean, allowOverScroll: Boolean): Float {
        @ScaledPan val value = if (horizontal) scaledPanX else scaledPanY
        val viewSize = if (horizontal) mContainerWidth else mContainerHeight
        @ScaledPan val contentSize = if (horizontal) mTransformedRect.width() else mTransformedRect.height()

        val overScrollable = if (horizontal) mOverScrollHorizontal else mOverScrollVertical
        @ScaledPan val overScroll = if (overScrollable && allowOverScroll) maxOverScroll else 0
        return getPanCorrection(value, viewSize, contentSize, overScroll)
    }

    @ScaledPan
    private fun getPanCorrection(@ScaledPan value: Float, viewSize: Float,
                                 @ScaledPan contentSize: Float, @ScaledPan overscrollTolerance: Int): Float {
        var min: Float
        var max: Float
        if (contentSize <= viewSize) {
            // If contentSize <= viewSize, we want to stay centered.
            // Need a positive translation, that shows some background.
            min = (viewSize - contentSize) / 2f
            max = (viewSize - contentSize) / 2f
        } else {
            // If contentSize is bigger, we just don't want to go outside.
            // Need a negative translation, that hides content.
            min = viewSize - contentSize
            max = 0f
        }
        min -= overscrollTolerance
        max += overscrollTolerance
        val desired = value.coerceIn(min, max)
        return desired - value
    }

    /**
     * Calls [checkPanBounds] on both directions
     * and applies the correction to the matrix if needed.
     */
    private fun ensurePanBounds(allowOverScroll: Boolean) {
        @ScaledPan val fixX = checkPanBounds(true, allowOverScroll)
        @ScaledPan val fixY = checkPanBounds(false, allowOverScroll)
        if (fixX != 0f || fixY != 0f) {
            mMatrix.postTranslate(fixX, fixY)
            mMatrix.mapRect(mTransformedRect, mContentRect)
        }
    }

    /**
     * Converts a [RealZoom] value to a [Zoom] value
     */
    @Zoom
    private fun Float.toZoom(@ZoomType inputZoomType: Int): Float {
        when (inputZoomType) {
            ZoomApi.TYPE_ZOOM -> return this
            ZoomApi.TYPE_REAL_ZOOM -> return this / mBaseZoom
        }
        throw IllegalArgumentException("Unknown ZoomType $inputZoomType")
    }

    /**
     * Converts a [Zoom] value to a [RealZoom] value
     */
    @RealZoom
    private fun Float.toRealZoom(@ZoomType inputZoomType: Int): Float {
        when (inputZoomType) {
            ZoomApi.TYPE_ZOOM -> return this * mBaseZoom
            ZoomApi.TYPE_REAL_ZOOM -> return this
        }
        throw IllegalArgumentException("Unknown ZoomType $inputZoomType")
    }

    /**
     * This is required when the content is a View that has clickable hierarchies inside.
     * If true is returned, implementors should not pass the call to super.
     *
     * @param ev the motion event
     * @return whether we want to intercept the event
     */
    fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return processTouchEvent(ev) > TOUCH_LISTEN
    }

    /**
     * Process the given touch event.
     * If true is returned, implementors should not pass the call to super.
     *
     * @param ev the motion event
     * @return whether we want to steal the event
     */
    fun onTouchEvent(ev: MotionEvent): Boolean {
        return processTouchEvent(ev) > TOUCH_NO
    }

    private fun processTouchEvent(event: MotionEvent): Int {
        LOG.v("processTouchEvent:", "start.")
        if (mState == ANIMATING) return TOUCH_STEAL

        var result = mScaleDetector.onTouchEvent(event)
        LOG.v("processTouchEvent:", "scaleResult:", result)

        // Pinch detector always returns true. If we actually started a pinch,
        // Don't pass to fling detector.
        if (mState != PINCHING) {
            result = result or mFlingDragDetector.onTouchEvent(event)
            LOG.v("processTouchEvent:", "flingResult:", result)
        }

        // Detect scroll ends, this appears to be the only way.
        if (mState == SCROLLING) {
            val a = event.actionMasked
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                LOG.i("processTouchEvent:", "up event while scrolling, dispatching onScrollEnd.")
                onScrollEnd()
            }
        }

        if (result && mState != NONE) {
            LOG.v("processTouchEvent:", "returning: TOUCH_STEAL")
            return TOUCH_STEAL
        } else if (result) {
            LOG.v("processTouchEvent:", "returning: TOUCH_LISTEN")
            return TOUCH_LISTEN
        } else {
            LOG.v("processTouchEvent:", "returning: TOUCH_NO")
            setState(NONE)
            return TOUCH_NO
        }
    }

    private inner class PinchListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {

        /**
         * Point holding a [AbsolutePan] coordinate
         */
        private var mInitialAbsFocusPoint: AbsolutePoint = AbsolutePoint(Float.NaN, Float.NaN)
        /**
         * Indicating the current pan offset introduced by a pinch focus shift as [AbsolutePan] values
         */
        private var mCurrentAbsFocusOffset: AbsolutePoint = AbsolutePoint(0F, 0F)

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (!mZoomEnabled) {
                return false
            }

            if (setState(PINCHING)) {
                // get the absolute pan position of the detector focus point
                val newAbsFocusPoint = viewCoordinateToAbsolutePoint(detector.focusX, detector.focusY)

                if (mInitialAbsFocusPoint.x.isNaN()) {
                    mInitialAbsFocusPoint.set(newAbsFocusPoint)
                    LOG.i("onScale:", "Setting initial focus.",
                            "absTarget:", mInitialAbsFocusPoint)
                } else {
                    // when the initial focus point is set, use it to
                    // calculate the location difference to the current focus point
                    mCurrentAbsFocusOffset.set(mInitialAbsFocusPoint - newAbsFocusPoint)
                }

                val factor = detector.scaleFactor
                val newZoom = zoom * factor

                applyZoomAndAbsolutePan(newZoom,
                        panX + mCurrentAbsFocusOffset.x, panY + mCurrentAbsFocusOffset.y,
                        allowOverScroll = true,
                        allowOverPinch = true,
                        zoomTargetX = detector.focusX,
                        zoomTargetY = detector.focusY)
                return true
            }
            return false
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            LOG.i("onScaleEnd:",
                    "mInitialAbsFocusPoint.x:", mInitialAbsFocusPoint.x,
                    "mInitialAbsFocusPoint.y:", mInitialAbsFocusPoint.y,
                    "mOverPinchable;", mOverPinchable)

            try {
                if (mOverPinchable || mOverScrollVertical || mOverScrollHorizontal) {
                    // We might have over pinched/scrolled. Animate back to reasonable value.
                    @Zoom val maxZoom = getMaxZoom()
                    @Zoom val minZoom = getMinZoom()

                    // check what zoom needs to be applied
                    // to get into a non-overpinched state
                    @Zoom val newZoom = checkZoomBounds(zoom, allowOverPinch = false)

                    LOG.i("onScaleEnd:",
                            "zoom:", zoom,
                            "newZoom:", newZoom,
                            "max:", maxZoom,
                            "min:", minZoom)

                    // check what pan needs to be applied
                    // to get into a non-overscrolled state
                    val panFix = getPanCorrection().toAbsolute()

                    if (panFix.x == 0F && panFix.y == 0F && newZoom.compareTo(zoom) == 0) {
                        // nothing to correct, we can stop right here
                        setState(NONE)
                        return
                    }

                    // select zoom pivot point based on what edge of the screen is currently overscrolled
                    val zoomTarget = calculateZoomPivotPoint(panFix)

                    // calculate the new pan position
                    val newPan = pan + panFix
                    if (newZoom.compareTo(zoom) != 0) {
                        // we have overpinched. to calculate how much pan needs to be applied
                        // to fix overscrolling we need to simulate the target zoom (when overpinching has been corrected)
                        // to calculate the needed pan correction for that zoom level

                        // remember current pan and zoom value to reset to that state later
                        val oldPan = pan
                        val oldZoom = zoom

                        // apply the target zoom with the currently known pivot point
                        applyZoom(newZoom, true, true, zoomTarget.x, zoomTarget.y, notifyListeners = false)

                        // recalculate pan fix to account for additional borders that might overscroll when zooming out
                        panFix.set(getPanCorrection().toAbsolute())

                        // recalculate new pan location using the simulated target zoom level
                        newPan.set(pan + panFix)

                        // revert simulation
                        applyZoomAndAbsolutePan(oldZoom, oldPan.x, oldPan.y, true, true, notifyListeners = false)
                    }

                    if (panFix.x == 0F && panFix.y == 0F) {
                        // no overscroll to correct
                        // only fix overpinch
                        animateZoom(newZoom, allowOverPinch = true)
                    } else {
                        // fix overscroll (overpinch is also corrected in here if necessary)
                        animateZoomAndAbsolutePan(newZoom,
                                newPan.x, newPan.y,
                                zoomTargetX = zoomTarget.x,
                                zoomTargetY = zoomTarget.y,
                                allowOverScroll = true, allowOverPinch = true)
                    }
                    // return here because new state will be ANIMATING
                    return
                }
                setState(NONE)
            } finally {
                resetPinchListenerState()
            }
        }

        /**
         * Resets the fields of this pinch gesture listener
         * to prepare it for the next pinch gesture detection
         * and remove any remaining data from the previous gesture.
         */
        private fun resetPinchListenerState() {
            mInitialAbsFocusPoint.set(Float.NaN, Float.NaN)
            mCurrentAbsFocusOffset.set(0F, 0F)
        }

        /**
         * Calculate pivot point to use for zoom based on pan fixes to be applied
         *
         * @param fixPan the amount of pan to apply to get into a valid state (no overscroll)
         * @return x-axis and y-axis view coordinates
         */
        private fun calculateZoomPivotPoint(fixPan: AbsolutePoint): PointF {
            if (zoom <= 1F) {
                // The zoom pivot point here should be based on the gravity that is used
                // to initially transform the content.
                // Currently this is always [View.Gravity.CENTER] as indicated by [mTransformationGravity]
                // but this might be changed by the user.
                return AbsolutePoint(-mContentRect.width() / 2F, -mContentRect.height() / 2F).toViewCoordinate()
            }

            val x = when {
                fixPan.x > 0 -> mContainerWidth // content needs to be moved left, use the right border as target
                fixPan.x < 0 -> 0F // content needs to move right, use the left border as target
                else -> mContainerWidth / 2F // axis is not changed, use center as target
            }

            val y = when {
                fixPan.y > 0 -> mContainerHeight // content needs to be moved up, use the bottom border as target
                fixPan.y < 0 -> 0F // content needs to move down, use the top border as target
                else -> mContainerHeight / 2F // axis is not changed, use center as target
            }

            return PointF(x, y)
        }
    }

    /**
     * @return the currently set upper boundary for maximum zoom value
     */
    @Zoom
    private fun getMaxZoom(): Float = mMaxZoom.toZoom(mMaxZoomMode)

    /**
     * @return the currently set lower boundary for minimum zoom value
     */
    @Zoom
    private fun getMinZoom(): Float = mMinZoom.toZoom(mMinZoomMode)

    private inner class FlingScrollListener : GestureDetector.SimpleOnGestureListener() {

        override fun onDown(e: MotionEvent): Boolean {
            // cancel any animation that might still be running
            setState(NONE)
            return true // We are interested in the gesture.
        }

        override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (!mFlingEnabled) {
                // fling is disabled, so we just ignore the event
                return false
            }

            val vX = (if (mHorizontalPanEnabled) velocityX else 0F).toInt()
            val vY = (if (mVerticalPanEnabled) velocityY else 0F).toInt()
            return startFling(vX, vY)
        }


        /**
         * Scroll event detected.
         *
         * We assume overScroll is true. If this is the case, it will be reset in [onScrollEnd].
         * If not, the [applyScaledPan] function will ignore our delta.
         *
         * TODO this this not true! ^
         */
        override fun onScroll(e1: MotionEvent, e2: MotionEvent,
                              @AbsolutePan distanceX: Float, @AbsolutePan distanceY: Float): Boolean {
            var delta = AbsolutePoint(distanceX, distanceY)
            if (setState(SCROLLING)) {
                // Change sign, since we work with opposite values.
                delta = -delta

                // See if we are overscrolling.
                val panFix = getPanCorrection()

                // If we are overscrolling AND scrolling towards the overscroll direction...
                if (panFix.x < 0 && delta.x > 0 || panFix.x > 0 && delta.x < 0) {
                    // Compute friction: a factor for distances. Must be 1 if we are not overscrolling,
                    // and 0 if we are at the end of the available overscroll. This works:
                    val overScrollX = Math.abs(panFix.x) / maxOverScroll // 0 ... 1
                    val frictionX = 0.6f * (1f - Math.pow(overScrollX.toDouble(), 0.4).toFloat()) // 0 ... 0.6
                    LOG.i("onScroll", "applying friction X:", frictionX)
                    delta.x *= frictionX
                }
                if (panFix.y < 0 && delta.y > 0 || panFix.y > 0 && delta.y < 0) {
                    val overScrollY = Math.abs(panFix.y) / maxOverScroll // 0 ... 1
                    val frictionY = 0.6f * (1f - Math.pow(overScrollY.toDouble(), 0.4).toFloat()) // 0 ... 10.6
                    LOG.i("onScroll", "applying friction Y:", frictionY)
                    delta.y *= frictionY
                }

                // If disabled, reset to 0.
                if (!mHorizontalPanEnabled) delta.x = 0f
                if (!mVerticalPanEnabled) delta.y = 0f

                if (delta.x != 0f || delta.y != 0f) {
                    applyScaledPan(delta.x, delta.y, true)
                }
                return true
            }
            return false
        }
    }

    private fun onScrollEnd() {
        if (mOverScrollHorizontal || mOverScrollVertical) {
            // We might have over scrolled. Animate back to reasonable value.
            val panFix = getPanCorrection()
            if (panFix.x != 0f || panFix.y != 0f) {
                animateScaledPan(panFix.x, panFix.y, true)
                return
            }
        }
        setState(NONE)
    }

    //endregion

    //region Position APIs

    /**
     * A low level API that can animate both zoom and pan at the same time.
     * Zoom might not be the actual matrix scale, see [ZoomApi.zoom] and [ZoomApi.realZoom].
     * The coordinates are referred to the content size passed in [setContentSize]
     * so they do not depend on current zoom.
     *
     * @param zoom    the desired zoom value
     * @param x       the desired left coordinate
     * @param y       the desired top coordinate
     * @param animate whether to animate the transition
     */
    override fun moveTo(@Zoom zoom: Float, @AbsolutePan x: Float, @AbsolutePan y: Float, animate: Boolean) {
        if (!mInitialized) return
        if (animate) {
            animateZoomAndAbsolutePan(zoom, x, y, allowOverScroll = false)
        } else {
            applyZoomAndAbsolutePan(zoom, x, y, allowOverScroll = false)
        }
    }

    /**
     * Pans the content until the top-left coordinates match the given x-y
     * values. These are referred to the content size passed in [setContentSize],
     * so they do not depend on current zoom.
     *
     * @param x       the desired left coordinate
     * @param y       the desired top coordinate
     * @param animate whether to animate the transition
     */
    override fun panTo(@AbsolutePan x: Float, @AbsolutePan y: Float, animate: Boolean) {
        panBy(x - panX, y - panY, animate)
    }

    /**
     * Pans the content by the given quantity in dx-dy values.
     * These are referred to the content size passed in [setContentSize],
     * so they do not depend on current zoom.
     *
     *
     * In other words, asking to pan by 1 pixel might result in a bigger pan, if the content
     * was zoomed in.
     *
     * @param dx      the desired delta x
     * @param dy      the desired delta y
     * @param animate whether to animate the transition
     */
    override fun panBy(@AbsolutePan dx: Float, @AbsolutePan dy: Float, animate: Boolean) {
        if (!mInitialized) return
        if (animate) {
            animateZoomAndAbsolutePan(zoom, panX + dx, panY + dy, allowOverScroll = false)
        } else {
            applyZoomAndAbsolutePan(zoom, panX + dx, panY + dy, allowOverScroll = false)
        }
    }

    /**
     * Zooms to the given scale. This might not be the actual matrix zoom,
     * see [ZoomApi.zoom] and [ZoomApi.realZoom].
     *
     * @param zoom    the new scale value
     * @param animate whether to animate the transition
     */
    override fun zoomTo(@Zoom zoom: Float, animate: Boolean) {
        if (!mInitialized) return
        if (animate) {
            animateZoom(zoom, allowOverPinch = false)
        } else {
            applyZoom(zoom, allowOverPinch = false)
        }
    }

    /**
     * Applies the given factor to the current zoom.
     *
     * @param zoomFactor a multiplicative factor
     * @param animate    whether to animate the transition
     */
    override fun zoomBy(zoomFactor: Float, animate: Boolean) {
        @Zoom val newZoom = zoom * zoomFactor
        zoomTo(newZoom, animate)
    }


    /**
     * Applies a small, animated zoom-in.
     * Shorthand for [zoomBy] with factor 1.3.
     */
    override fun zoomIn() {
        zoomBy(1.3f, animate = true)
    }

    /**
     * Applies a small, animated zoom-out.
     * Shorthand for [zoomBy] with factor 0.7.
     */
    override fun zoomOut() {
        zoomBy(0.7f, animate = true)
    }

    /**
     * Animates the actual matrix zoom to the given value.
     *
     * @param realZoom the new real zoom value
     * @param animate  whether to animate the transition
     */
    override fun realZoomTo(@RealZoom realZoom: Float, animate: Boolean) {
        val zoom = realZoom.toZoom(ZoomApi.TYPE_REAL_ZOOM)
        zoomTo(zoom, animate)
    }

    /**
     * Which is the max zoom that should be allowed.
     * If [setOverPinchable] is set to true, this can be over-pinched
     * for a brief time.
     *
     * @param maxZoom the max zoom
     * @param type    the constraint mode
     * @see ZoomApi.zoom
     * @see ZoomApi.realZoom
     * @see ZoomApi.TYPE_ZOOM
     *
     * @see ZoomApi.TYPE_REAL_ZOOM
     */
    override fun setMaxZoom(maxZoom: Float, @ZoomType type: Int) {
        if (maxZoom < 0) {
            throw IllegalArgumentException("Max zoom should be >= 0.")
        }
        mMaxZoom = maxZoom
        mMaxZoomMode = type

        // check if current zoomlevel is within bounds
        if (zoom > getMaxZoom()) {
            // correct to the exact new boundary if necessary
            zoomTo(getMaxZoom(), animate = true)
        }
    }

    /**
     * Which is the min zoom that should be allowed.
     * If [setOverPinchable] is set to true, this can be over-pinched
     * for a brief time.
     *
     * @param minZoom the min zoom
     * @param type    the constraint mode
     * @see ZoomApi.zoom
     * @see ZoomApi.realZoom
     */
    override fun setMinZoom(minZoom: Float, @ZoomType type: Int) {
        if (minZoom < 0) {
            throw IllegalArgumentException("Min zoom should be >= 0")
        }
        mMinZoom = minZoom
        mMinZoomMode = type
        if (zoom <= getMinZoom()) {
            zoomTo(getMinZoom(), animate = true)
        }
    }

    //endregion

    //region Apply values

    private val mCancelAnimationListener = object : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator?) {
        }

        override fun onAnimationEnd(animation: Animator?) {
            setState(NONE)
        }

        override fun onAnimationCancel(animation: Animator?) {
        }

        override fun onAnimationRepeat(animation: Animator?) {
        }
    }

    /**
     * Prepares a [ValueAnimator] for the first run
     */
    private fun ValueAnimator.prepare() {
        this.duration = mAnimationDuration
        this.addListener(mCancelAnimationListener)
        this.interpolator = ANIMATION_INTERPOLATOR
    }

    /**
     * Calls [applyZoom] repeatedly
     * until the final zoom is reached, interpolating.
     *
     * @param zoom        the new zoom
     * @param allowOverPinch whether overpinching is allowed
     */
    private fun animateZoom(@Zoom zoom: Float, allowOverPinch: Boolean) {
        if (setState(ANIMATING)) {
            mClearAnimation = false
            @Zoom val startZoom = this.zoom
            @Zoom val endZoom = checkZoomBounds(zoom, allowOverPinch)
            val zoomAnimator = ValueAnimator.ofFloat(startZoom, endZoom)
            zoomAnimator.prepare()
            zoomAnimator.addUpdateListener {
                LOG.v("animateZoom:", "animationStep:", it.animatedFraction)
                if (mClearAnimation) {
                    it.cancel()
                }
                mContainer.postOnAnimation {
                    applyZoom(it.animatedValue as Float, allowOverPinch)
                }
            }

            zoomAnimator.start()
        }
    }

    /**
     * Calls [applyZoomAndAbsolutePan] repeatedly
     * until the final position is reached, interpolating.
     *
     * @param zoom         new zoom
     * @param x               final abs pan
     * @param y               final abs pan
     * @param allowOverScroll true if overscroll is allowed, false otherwise
     * @param allowOverPinch  true if overpinch is allowed, false otherwise
     * @param zoomTargetX     the x-axis zoom target
     * @param zoomTargetY     the y-axis zoom target
     */
    private fun animateZoomAndAbsolutePan(@Zoom zoom: Float,
                                          @AbsolutePan x: Float, @AbsolutePan y: Float,
                                          allowOverScroll: Boolean,
                                          allowOverPinch: Boolean = false,
                                          zoomTargetX: Float? = null,
                                          zoomTargetY: Float? = null) {
        if (setState(ANIMATING)) {
            mClearAnimation = false
            @Zoom val startZoom = this.zoom
            @Zoom val endZoom = checkZoomBounds(zoom, allowOverScroll)
            val startPan = pan
            val targetPan = AbsolutePoint(x, y)
            LOG.i("animateZoomAndAbsolutePan:", "starting.", "startX:", startPan.x, "endX:", x, "startY:", startPan.y, "endY:", y)
            LOG.i("animateZoomAndAbsolutePan:", "starting.", "startZoom:", startZoom, "endZoom:", endZoom)

            @SuppressLint("ObjectAnimatorBinding")
            val animator = ObjectAnimator.ofPropertyValuesHolder(mContainer,
                    PropertyValuesHolder.ofObject(
                            "pan",
                            TypeEvaluator { fraction: Float, startValue: AbsolutePoint, endValue: AbsolutePoint ->
                                startValue + (endValue - startValue) * fraction
                            }, startPan, targetPan),

                    PropertyValuesHolder.ofFloat(
                            "zoom",
                            startZoom, endZoom)
            )
            animator.prepare()
            animator.addUpdateListener {
                if (mClearAnimation) {
                    it.cancel()
                }
                mContainer.postOnAnimation {
                    val newZoom = it.getAnimatedValue("zoom") as Float
                    val currentPan = it.getAnimatedValue("pan") as AbsolutePoint
                    applyZoomAndAbsolutePan(newZoom, currentPan.x, currentPan.y, allowOverScroll, allowOverPinch, zoomTargetX, zoomTargetY)
                }
            }
            animator.start()
        }
    }

    /**
     * Calls [applyScaledPan] repeatedly
     * until the final delta is applied, interpolating.
     *
     * @param deltaX          a scaled delta
     * @param deltaY          a scaled delta
     * @param allowOverScroll whether to overscroll
     */
    private fun animateScaledPan(@AbsolutePan deltaX: Float, @ScaledPan deltaY: Float,
                                 allowOverScroll: Boolean) {
        if (setState(ANIMATING)) {
            mClearAnimation = false
            val startPan = scaledPan
            val endPan = startPan + ScaledPoint(deltaX, deltaY)

            val panAnimator = ValueAnimator.ofObject(TypeEvaluator { fraction, startValue: ScaledPoint, endValue: ScaledPoint ->
                startValue + (endValue - startValue) * fraction - scaledPan
            }, startPan, endPan)
            panAnimator.prepare()
            panAnimator.addUpdateListener {
                LOG.v("animateScaledPan:", "animationStep:", it.animatedFraction)
                if (mClearAnimation) {
                    it.cancel()
                }
                mContainer.postOnAnimation {
                    val currentPan = it.animatedValue as ScaledPoint
                    applyScaledPan(currentPan.x, currentPan.y, allowOverScroll)
                }
            }

            panAnimator.start()
        }
    }

    override fun setAnimationDuration(duration: Long) {
        mAnimationDuration = duration
    }

    /**
     * Applies the given zoom value, meant as a [Zoom] value
     * (so not a [RealZoom]).
     * The zoom is applied so that the center point is kept in its place
     *
     * @param zoom        the new zoom value
     * @param allowOverPinch whether to overpinch
     * @param zoomTargetX the x-axis zoom target
     * @param zoomTargetY the y-axis zoom target
     */
    private fun applyZoom(@Zoom zoom: Float,
                          allowOverPinch: Boolean,
                          allowOverScroll: Boolean = false,
                          zoomTargetX: Float? = null,
                          zoomTargetY: Float? = null,
                          notifyListeners: Boolean = true) {
        val newZoom = checkZoomBounds(zoom, allowOverPinch)
        val scaleFactor = newZoom / this.zoom

        mMatrix.postScale(scaleFactor, scaleFactor,
                zoomTargetX ?: mContainerWidth / 2f, zoomTargetY ?: mContainerHeight / 2f)
        mMatrix.mapRect(mTransformedRect, mContentRect)
        this.zoom = newZoom
        ensurePanBounds(allowOverScroll)
        if (notifyListeners) {
            dispatchOnMatrix()
        }
    }

    /**
     * Applies both zoom and absolute pan. This is like specifying a position.
     * The semantics of this are that after the position is applied, the zoom corresponds
     * to the given value, [ZoomApi.panX] returns x, [ZoomApi.panY] returns y.
     *
     *
     * Absolute panning is achieved through [Matrix.preTranslate],
     * which works in the original coordinate system.
     *
     * @param zoom            the new zoom value
     * @param x               the final left absolute pan
     * @param y               the final top absolute pan
     * @param allowOverScroll true if overscroll is allowed, false otherwise
     * @param allowOverPinch  true if overpinch is allowed, false otherwise
     * @param zoomTargetX     the x-axis zoom target
     * @param zoomTargetY     the y-axis zoom target
     * @param notifyListeners when true listeners are informed about this zoom/pan, otherwise they wont
     */
    private fun applyZoomAndAbsolutePan(@Zoom zoom: Float,
                                        @AbsolutePan x: Float, @AbsolutePan y: Float,
                                        allowOverScroll: Boolean,
                                        allowOverPinch: Boolean = false,
                                        zoomTargetX: Float? = null,
                                        zoomTargetY: Float? = null,
                                        notifyListeners: Boolean = true) {
        // Translation
        val delta = AbsolutePoint(x, y) - pan
        mMatrix.preTranslate(delta.x, delta.y)
        mMatrix.mapRect(mTransformedRect, mContentRect)

        // Scale
        val newZoom = checkZoomBounds(zoom, allowOverPinch)
        val scaleFactor = newZoom / this.zoom
        // TODO: This used to work but I am not sure about it.
        // mMatrix.postScale(scaleFactor, scaleFactor, getScaledPanX(), getScaledPanY());
        // It keeps the pivot point at the scaled values 0, 0 (see applyPinch).
        // I think we should keep the current top, left.. Let's try:

        val pivotX = zoomTargetX ?: 0F
        val pivotY = zoomTargetY ?: 0F

        mMatrix.postScale(scaleFactor, scaleFactor, pivotX, pivotY)
        mMatrix.mapRect(mTransformedRect, mContentRect)
        this.zoom = newZoom

        ensurePanBounds(allowOverScroll)
        if (notifyListeners) {
            dispatchOnMatrix()
        }
    }

    /**
     * Applies the given scaled translation.
     *
     *
     * Scaled translation are applied through [Matrix.postTranslate],
     * which acts on the actual dimension of the rect.
     *
     * @param deltaX          the x translation
     * @param deltaY          the y translation
     * @param allowOverScroll whether to overScroll
     */
    private fun applyScaledPan(@ScaledPan deltaX: Float, @ScaledPan deltaY: Float, allowOverScroll: Boolean) {
        mMatrix.postTranslate(deltaX, deltaY)
        mMatrix.mapRect(mTransformedRect, mContentRect)
        ensurePanBounds(allowOverScroll)
        dispatchOnMatrix()
    }

    /**
     * Converts a [AbsolutePan] value to an [ScaledPan] value
     * @return the [ScaledPan] value
     */
    @ScaledPan
    private fun Float.toScaled(): Float {
        return this * realZoom
    }

    /**
     * Converts a [ScaledPan] value to an [AbsolutePan] value
     * @return the [AbsolutePan] value
     */
    @AbsolutePan
    private fun Float.toAbsolute(): Float {
        return this / realZoom
    }

    /**
     * Converts an [AbsolutePoint] to a [ScaledPoint]
     */
    private fun AbsolutePoint.toScaled(): ScaledPoint {
        return ScaledPoint(this.x.toScaled(), this.y.toScaled())
    }

    /**
     * Converts a [ScaledPoint] to an [AbsolutePoint]
     */
    private fun ScaledPoint.toAbsolute(): AbsolutePoint {
        return AbsolutePoint(this.x.toAbsolute(), this.y.toAbsolute())
    }

    /**
     * Calculates the [AbsolutePoint] value for a view coordinate
     * This is the reverse operation to [AbsolutePoint.toViewCoordinate].
     *
     * Example:
     * When the viewport is 1000x1000 and the [ZoomLayout] content is 3000x3000 and exactly centered
     * and you call [viewCoordinateToAbsolutePoint(500,500)] the result will be -1500x-1500
     *
     * @param x x-axis screen value
     * @param y y-axis screen value
     * @return [AbsolutePoint]
     */
    private fun viewCoordinateToAbsolutePoint(x: Float, y: Float): AbsolutePoint {
        var scaledPoint = ScaledPoint(-x, -y)
        // Account for current pan.
        scaledPoint += scaledPan
        // Transform to an absolute, scale-independent value.
        return scaledPoint.toAbsolute()
    }

    /**
     * Calculates the view coordinate from an [AbsolutePoint]
     * This is the reverse operation to [viewCoordinateToAbsolutePoint].
     *
     * @return view coordinate
     */
    private fun AbsolutePoint.toViewCoordinate(): PointF {
        val scaledPoint = this.toScaled()
        return PointF(scaledPanX - scaledPoint.x, scaledPanY - scaledPoint.y)
    }

    /**
     * Helper for pinch gestures. In these cases what we know is the detector focus,
     * and we can use it in [Matrix.postScale] to avoid
     * buggy translations.
     *
     * @param zoom        the new zoom
     * @param targetX        the target X in abs value
     * @param targetY        the target Y in abs value
     * @param allowOverPinch whether to overPinch
     */
    private fun applyPinch(@Zoom zoom: Float, @AbsolutePan targetX: Float, @AbsolutePan targetY: Float,
                           allowOverPinch: Boolean) {
        // TODO: this method is currently unused, should it be removed? The doc mentions buggy translations :S

        // The pivotX and pivotY options of postScale refer (obviously!) to the visible
        // portion of the screen, since the (0,0) point is remapped to be in top-left of the view.
        // The right coordinates to use are the view coordinates.
        // This means we should use scaled coordinates, but remove the current pan.

        val targetPan = scaledPan - AbsolutePoint(targetX, targetY).toScaled()
        val newZoom = checkZoomBounds(zoom, allowOverPinch)
        val scaleFactor = newZoom / this.zoom
        mMatrix.postScale(scaleFactor, scaleFactor,
                targetPan.x, targetPan.y)

        mMatrix.mapRect(mTransformedRect, mContentRect)
        this.zoom = newZoom
        ensurePanBounds(allowOverScroll = false)
        dispatchOnMatrix()
    }

    //endregion

    //region Fling

    // Puts min, start and max values in the mTemp array.
    // Since axes are shifted (pans are negative), min values are related to bottom-right,
    // while max values are related to top-left.
    private fun computeScrollerValues(horizontal: Boolean, output: ScrollerValues) {
        @ScaledPan val currentPan = (if (horizontal) scaledPanX else scaledPanY).toInt()
        val viewDim = (if (horizontal) mContainerWidth else mContainerHeight).toInt()
        @ScaledPan val contentDim = (if (horizontal) mTransformedRect.width() else mTransformedRect.height()).toInt()
        val fix = checkPanBounds(horizontal, false).toInt()
        if (viewDim >= contentDim) {
            // Content is smaller, we are showing some boundary.
            // We can't move in any direction (but we can overScroll).
            output.minValue = currentPan + fix
            output.startValue = currentPan
            output.maxValue = currentPan + fix
        } else {
            // Content is bigger, we can move.
            // in this case minPan + viewDim = contentDim
            output.minValue = -(contentDim - viewDim)
            output.startValue = currentPan
            output.maxValue = 0
        }
        output.isInOverScroll = fix != 0
    }

    private class ScrollerValues {
        @ScaledPan
        internal var minValue: Int = 0
        @ScaledPan
        internal var startValue: Int = 0
        @ScaledPan
        internal var maxValue: Int = 0
        internal var isInOverScroll: Boolean = false
    }

    private fun startFling(@ScaledPan velocityX: Int, @ScaledPan velocityY: Int): Boolean {
        // Using actual pan values for the scroller.
        // Note: these won't make sense if zoom changes.
        computeScrollerValues(true, mScrollerValuesX)
        computeScrollerValues(false, mScrollerValuesY)
        @ScaledPan val minX = mScrollerValuesX.minValue
        @ScaledPan val startX = mScrollerValuesX.startValue
        @ScaledPan val maxX = mScrollerValuesX.maxValue
        @ScaledPan val minY = mScrollerValuesY.minValue
        @ScaledPan val startY = mScrollerValuesY.startValue
        @ScaledPan val maxY = mScrollerValuesY.maxValue
        if (!mAllowFlingInOverscroll && (mScrollerValuesX.isInOverScroll || mScrollerValuesY.isInOverScroll)) {
            // Only allow new flings while overscrolled if explicitly enabled as this might causes artifacts.
            return false
        }
        if (minX >= maxX && minY >= maxY && !mOverScrollVertical && !mOverScrollHorizontal) {
            return false
        }
        // Must be after the other conditions.
        if (!setState(FLINGING)) return false

        @ScaledPan val overScrollX = if (mOverScrollHorizontal) maxOverScroll else 0
        @ScaledPan val overScrollY = if (mOverScrollVertical) maxOverScroll else 0
        LOG.i("startFling", "velocityX:", velocityX, "velocityY:", velocityY)
        LOG.i("startFling", "flingX:", "min:", minX, "max:", maxX, "start:", startX, "overScroll:", overScrollY)
        LOG.i("startFling", "flingY:", "min:", minY, "max:", maxY, "start:", startY, "overScroll:", overScrollX)
        mFlingScroller.fling(startX, startY,
                velocityX, velocityY,
                minX, maxX, minY, maxY,
                overScrollX, overScrollY)

        mContainer.post(object : Runnable {
            override fun run() {
                if (mFlingScroller.isFinished) {
                    setState(NONE)
                } else if (mFlingScroller.computeScrollOffset()) {
                    @ScaledPan val newPanX = mFlingScroller.currX
                    @ScaledPan val newPanY = mFlingScroller.currY
                    // OverScroller will eventually go back to our bounds.
                    applyScaledPan(newPanX - scaledPanX, newPanY - scaledPanY, true)
                    mContainer.postOnAnimation(this)
                }
            }
        })
        return true
    }

    //endregion

    //region scrollbars helpers

    /**
     * Helper for implementing [View.computeHorizontalScrollOffset]
     * in custom views.
     *
     * @return the horizontal scroll offset.
     */
    fun computeHorizontalScrollOffset(): Int {
        return (-scaledPanX).toInt()
    }

    /**
     * Helper for implementing [View.computeHorizontalScrollRange]
     * in custom views.
     *
     * @return the horizontal scroll range.
     */
    fun computeHorizontalScrollRange(): Int {
        return mTransformedRect.width().toInt()
    }

    /**
     * Helper for implementing [View.computeVerticalScrollOffset]
     * in custom views.
     *
     * @return the vertical scroll offset.
     */
    fun computeVerticalScrollOffset(): Int {
        return (-scaledPanY).toInt()
    }

    /**
     * Helper for implementing [View.computeVerticalScrollRange]
     * in custom views.
     *
     * @return the vertical scroll range.
     */
    fun computeVerticalScrollRange(): Int {
        return mTransformedRect.height().toInt()
    }

    companion object {

        // TODO add OverScrollCallback and OverPinchCallback.
        // Should notify the user when the boundaries are reached.

        // TODO expose friction parameters, use an interpolator.

        // TODO Make public, add API.
        /**
         * The default overscrolling factor
         */
        private val DEFAULT_OVERSCROLL_FACTOR = 0.10f

        // TODO Make public, add API. Use androidx.Interpolator?
        private val ANIMATION_INTERPOLATOR = AccelerateDecelerateInterpolator()

        /**
         * The default animation duration
         */
        const val DEFAULT_ANIMATION_DURATION: Long = 280

        private val TAG = ZoomEngine::class.java.simpleName
        private val LOG = ZoomLogger.create(TAG)

        private const val NONE = 0
        private const val SCROLLING = 1
        private const val PINCHING = 2
        private const val ANIMATING = 3
        private const val FLINGING = 4

        private fun getStateName(@State state: Int): String {
            when (state) {
                NONE -> return "NONE"
                FLINGING -> return "FLINGING"
                SCROLLING -> return "SCROLLING"
                PINCHING -> return "PINCHING"
                ANIMATING -> return "ANIMATING"
            }
            return ""
        }

        //endregion

        //region Touch events and Gesture Listeners

        // Might make these public some day?
        private const val TOUCH_NO = 0
        private const val TOUCH_LISTEN = 1
        private const val TOUCH_STEAL = 2
    }

    //endregion
}
