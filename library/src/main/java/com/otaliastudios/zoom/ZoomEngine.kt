package com.otaliastudios.zoom

import android.animation.*
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import com.otaliastudios.zoom.ZoomApi.*
import com.otaliastudios.zoom.internal.EventsDispatcher
import com.otaliastudios.zoom.internal.StateManager
import com.otaliastudios.zoom.internal.gestures.PinchDetector
import com.otaliastudios.zoom.internal.gestures.ScrollFlingDetector
import com.otaliastudios.zoom.internal.movement.PanManager
import com.otaliastudios.zoom.internal.movement.ZoomManager


/**
 * A low level class that listens to touch events and posts zoom and pan updates.
 * The most useful output is a [Matrix] that can be used to do pretty much everything,
 * from canvas drawing to View hierarchies translations.
 *
 * Users are required to:
 * - Pass the container view in the constructor
 * - Notify the helper of the content size, using [setContentSize]
 * - Pass touch events to [onInterceptTouchEvent] and [onTouchEvent]
 *
 */
open class ZoomEngine
/**
 * Constructs an helper instance.
 *
 * @param context a valid context
 */
internal constructor(context: Context) :
        ViewTreeObserver.OnGlobalLayoutListener,
        ZoomApi,
        StateManager.Callback {

    /**
     * Constructs an helper instance.
     *
     * @param context a valid context
     * @param container the view hosting the zoomable content
     **/
    constructor(context: Context, container: View) : this(context) {
        setContainer(container)
    }

    /**
     * Constructs an helper instance.
     * Deprecated: use [addListener] to add a listener.
     *
     * @param context a valid context
     * @param container the view hosting the zoomable content
     * @param listener a listener for events
     **/
    @Deprecated("Use [addListener] to add a listener.",
            replaceWith = ReplaceWith("constructor(context, container)"))
    constructor(context: Context, container: View, listener: Listener) : this(context, container) {
        addListener(listener)
    }

    // Options
    private var mTransformation = ZoomApi.TRANSFORMATION_CENTER_INSIDE
    private var mTransformationGravity = ZoomApi.TRANSFORMATION_GRAVITY_AUTO
    internal var mAlignment = ZoomApi.ALIGNMENT_DEFAULT

    // Internal
    private var mMatrix = Matrix()
    internal var mTransformationZoom = 0F // mZoom * mTransformationZoom matches the matrix scale.
    private lateinit var mContainer: View
    private var mInitialized = false
    private var mContentScaledRect = RectF()
    private var mContentRect = RectF()
    private var mAnimationDuration = DEFAULT_ANIMATION_DURATION
    private val eventsDispatcher = EventsDispatcher(this)
    private val stateManager = StateManager(this)
    private val panManager = PanManager(this)
    private val zoomManager = ZoomManager(this)

    // Gestures
    private val scrollFlingDetector = ScrollFlingDetector(context, stateManager, panManager, this)
    private val pinchDetector = PinchDetector(context, stateManager, zoomManager, panManager,this)

    @ScaledPan
    internal val mContentScaledWidth: Float
        get() = mContentScaledRect.width()

    @ScaledPan
    internal val mContentScaledHeight: Float
        get() = mContentScaledRect.height()

    /**
     * Returns the content width as passed to [setContentSize].
     * @return the current width
     */
    @AbsolutePan
    val contentWidth: Float
        get() = mContentRect.width()

    /**
     * Returns the content height as passed to [setContentSize].
     * @return the current height
     */
    @AbsolutePan
    val contentHeight: Float
        get() = mContentRect.height()

    /**
     * Returns the container width as passed to [setContainerSize].
     * @return the current width
     */
    var containerWidth = 0F
        private set

    /**
     * Returns the container height as passed to [setContainerSize].
     * @return the current height
     */
    var containerHeight = 0F
        private set

    /**
     * Gets the current zoom value, which can be used as a reference when calling
     * [zoomTo] or [zoomBy].
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
        internal set

    /**
     * Returns the current matrix. This can be changed from the outside, but is not
     * guaranteed to remain stable.
     *
     * @return the current matrix.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    val matrix: Matrix = Matrix()
        get() {
            field.set(mMatrix)
            return field
        }

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
        get() = zoom * mTransformationZoom

    /**
     * The current pan as an [AbsolutePoint].
     * This field will be updated according to current pan when accessed.
     */
    override val pan = AbsolutePoint()
        get() {
            field.set(panX, panY)
            return field
        }

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
     * This field will be updated according to current scaled pan when accessed.
     *
     * TODO if necessary, expose scaledPan, scaledPanX and scaledPanY through ZoomApi.
     * Right now I'm not sure it makes sense - all input APIs work with absolute values and the
     * "scaled pan" concept is probably not even mentioned in docs.
     */
    internal val scaledPan = ScaledPoint()
        get() {
            field.set(scaledPanX, scaledPanY)
            return field
        }

    /**
     * The current horizontal scaled pan, which is the pan position of the content
     * according to the current zoom value (so it's scaled).
     */
    @ScaledPan
    internal val scaledPanX: Float
        get() = mContentScaledRect.left

    /**
     * The current vertical scaled pan, which is the pan position of the content
     * according to the current zoom value (so it's scaled).
     */
    @ScaledPan
    internal val scaledPanY: Float
        get() = mContentScaledRect.top

    //region Listeners

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

    /**
     * Registers a new [Listener] to be notified of matrix updates.
     * @param listener the new listener
     *
     */
    fun addListener(listener: Listener) {
        eventsDispatcher.addListener(listener)
    }

    /**
     * Removes a previously registered listener.
     * @param listener the listener to be removed
     */
    @Suppress("unused")
    fun removeListener(listener: Listener) {
        eventsDispatcher.removeListener(listener)
    }


    //endregion

    //region post utilities

    internal fun post(action: Runnable) {
        mContainer.post(action)
    }

    internal fun postOnAnimation(action: Runnable) {
        mContainer.postOnAnimation(action)
    }

    //endregion

    //region State callbacks

    override fun isStateAllowed(newState: Int): Boolean {
        return mInitialized
    }

    override fun onStateIdle() {
        eventsDispatcher.dispatchOnIdle()
    }

    override fun cleanupState(@StateManager.State oldState: Int) {
        when (oldState) {
            StateManager.ANIMATING -> {
                mActiveAnimators.forEach { it.cancel() }
                mActiveAnimators.clear()
            }
            StateManager.FLINGING -> scrollFlingDetector.cancelFling()
        }
    }

    //endregion

    //region Gesture callbacks

    override fun endScrollGesture() {
        scrollFlingDetector.cancelScroll()
    }

    override fun maybeStartPinchGesture(event: MotionEvent): Boolean {
        return pinchDetector.maybeStart(event)
    }

    override fun maybeStartScrollFlingGesture(event: MotionEvent): Boolean {
        return scrollFlingDetector.maybeStart(event)
    }

    //endregion

    //region Options

    /**
     * Controls whether the content should be over-scrollable horizontally.
     * If it is, drag and fling horizontal events can scroll the content outside the safe area,
     * then return to safe values.
     *
     * @param overScroll whether to allow horizontal over scrolling
     */
    override fun setOverScrollHorizontal(overScroll: Boolean) {
        panManager.horizontalOverPanEnabled = overScroll
    }

    /**
     * Controls whether the content should be over-scrollable vertically.
     * If it is, drag and fling vertical events can scroll the content outside the safe area,
     * then return to safe values.
     *
     * @param overScroll whether to allow vertical over scrolling
     */
    override fun setOverScrollVertical(overScroll: Boolean) {
        panManager.verticalOverPanEnabled = overScroll
    }

    /**
     * Controls whether horizontal panning using gestures is enabled.
     *
     * @param enabled true enables horizontal panning, false disables it
     */
    override fun setHorizontalPanEnabled(enabled: Boolean) {
        panManager.horizontalPanEnabled = enabled
    }

    /**
     * Controls whether vertical panning using gestures is enabled.
     *
     * @param enabled true enables vertical panning, false disables it
     */
    override fun setVerticalPanEnabled(enabled: Boolean) {
        panManager.verticalPanEnabled = enabled
    }

    /**
     * Controls whether the content should be overPinchable.
     * If it is, pinch events can change the zoom outside the safe bounds,
     * than return to safe values.
     *
     * @param overPinchable whether to allow over pinching
     */
    override fun setOverPinchable(overPinchable: Boolean) {
        zoomManager.overZoomEnabled = overPinchable
    }

    /**
     * Controls whether zoom using pinch gesture is enabled or not.
     *
     * @param enabled true enables zooming, false disables it
     */
    override fun setZoomEnabled(enabled: Boolean) {
        zoomManager.zoomEnabled = enabled
    }

    /**
     * Controls whether fling gesture is enabled or not.
     *
     * @param enabled true enables fling gesture, false disables it
     */
    override fun setFlingEnabled(enabled: Boolean) {
        scrollFlingDetector.flingEnabled = enabled
    }

    /**
     * Controls whether fling events are allowed when the view is in an overscrolled state.
     *
     * @param allow true allows fling in overscroll, false disables it
     */
    override fun setAllowFlingInOverscroll(allow: Boolean) {
        scrollFlingDetector.flingInOverPanEnabled = allow
    }

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

    /**
     * Sets the content alignment. Can be any of the constants defined in [Alignment].
     * The content will be aligned and forced to the specified side of the container.
     * Defaults to [ZoomApi.ALIGNMENT_DEFAULT].
     *
     * Keep in mind that this is disabled when the content is larger than the container,
     * because a forced alignment in this case would result in part of the content being unreachable.
     *
     * @param alignment the new alignment
     */
    override fun setAlignment(@ZoomApi.Alignment alignment: Int) {
        mAlignment = alignment
    }

    //endregion

    //region Initialize

    /**
     * Set a container to perform transformations on.
     * This method should only be called once at initialization time.
     *
     * @param container view
     */
    internal fun setContainer(container: View) {
        mContainer = container
        mContainer.addOnAttachStateChangeListener(object: View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                view.viewTreeObserver.addOnGlobalLayoutListener(this@ZoomEngine)
            }
            override fun onViewDetachedFromWindow(view: View) {
                view.viewTreeObserver.removeOnGlobalLayoutListener(this@ZoomEngine)
            }
        })
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
        if (contentWidth != width || contentHeight != height || applyTransformation) {
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
        if (width != containerWidth || height != containerHeight || applyTransformation) {
            containerWidth = width
            containerHeight = height
            onSizeChanged(applyTransformation)
        }
    }

    private fun onSizeChanged(applyTransformation: Boolean) {
        // We will sync them later using matrix.mapRect.
        mContentScaledRect.set(mContentRect)

        if (contentWidth <= 0
                || contentHeight <= 0
                || containerWidth <= 0
                || containerHeight <= 0)
            return

        LOG.w("onSizeChanged:", "containerWidth:", containerWidth,
                "containerHeight:", containerHeight,
                "contentWidth:", contentWidth,
                "contentHeight:", contentHeight)

        // See if we need to apply the transformation. This is the easier case, because
        // if we don't want to apply it, we must do extra computations to keep the appearance unchanged.
        stateManager.makeIdle()
        val apply = !mInitialized || applyTransformation
        LOG.w("onSizeChanged: will apply?", apply, "transformation?", mTransformation)
        if (apply) {
            // First time. Apply base zoom, dispatch first event and return.
            mTransformationZoom = computeTransformationZoom()
            mMatrix.setScale(mTransformationZoom, mTransformationZoom)
            mMatrix.mapRect(mContentScaledRect, mContentRect)
            zoom = 1f
            LOG.i("onSizeChanged: newTransformationZoom:", mTransformationZoom, "newZoom:", zoom)
            @Zoom val newZoom = zoomManager.checkBounds(zoom, false)
            LOG.i("onSizeChanged: scaleBounds:", "we need a zoom correction of", newZoom - zoom)
            if (newZoom != zoom) applyZoom(newZoom, allowOverPinch = false)

            // pan based on transformation gravity.
            @ScaledPan val newPan = computeTransformationPan()
            @ScaledPan val deltaX = newPan[0] - scaledPanX
            @ScaledPan val deltaY = newPan[1] - scaledPanY
            if (deltaX != 0f || deltaY != 0f) applyScaledPan(deltaX, deltaY, false)

            maybeAdjustPan(allowOverPan = false)
            eventsDispatcher.dispatchOnMatrix()
            if (!mInitialized) {
                mInitialized = true
            }
        } else {
            // We were initialized, but some size changed. Since applyTransformation is false,
            // we must do extra work: recompute the transformationZoom (since size changed, it makes no sense)
            // but also compute a new zoom such that the real zoom is kept unchanged.
            // So, this method triggers no Matrix updates.
            LOG.i("onSizeChanged: Trying to keep real zoom to", realZoom)
            LOG.i("onSizeChanged: oldTransformationZoom:", mTransformationZoom, "oldZoom:$zoom")
            @RealZoom val realZoom = realZoom
            mTransformationZoom = computeTransformationZoom()
            zoom = realZoom / mTransformationZoom
            LOG.i("onSizeChanged: newTransformationZoom:", mTransformationZoom, "newZoom:", zoom)

            // Now sync the content rect with the current matrix since we are trying to keep it.
            // This is to have consistent values for other calls here.
            mMatrix.mapRect(mContentScaledRect, mContentRect)

            // If the new zoom value is invalid, though, we must bring it to the valid place.
            // This is a possible matrix update.
            @Zoom val newZoom = zoomManager.checkBounds(zoom, false)
            LOG.i("onSizeChanged: scaleBounds:", "we need a zoom correction of", newZoom - zoom)
            if (newZoom != zoom) applyZoom(newZoom, allowOverPinch = false)

            // If there was any, pan should be kept. I think there's nothing to do here:
            // If the matrix is kept, and real zoom is kept, then also the real pan is kept.
            // I am not 100% sure of this though, so I prefer to call a useless dispatch.
            maybeAdjustPan(allowOverPan = false)
            eventsDispatcher.dispatchOnMatrix()
        }
    }

    /**
     * Clears the current state, and stops dispatching matrix events
     * until the view is laid out again and [ZoomEngine.setContentSize]
     * is called.
     */
    fun clear() {
        containerHeight = 0f
        containerWidth = 0f
        zoom = 1f
        mTransformationZoom = 0f
        mContentScaledRect = RectF()
        mContentRect = RectF()
        mMatrix = Matrix()
        mInitialized = false
    }

    /**
     * Computes the starting zoom, which means applying the transformation.
     */
    private fun computeTransformationZoom(): Float {
        when (mTransformation) {
            ZoomApi.TRANSFORMATION_CENTER_INSIDE -> {
                val scaleX = containerWidth / mContentScaledWidth
                val scaleY = containerHeight / mContentScaledHeight
                LOG.v("computeTransformationZoom", "centerInside", "scaleX:", scaleX, "scaleY:", scaleY)
                return Math.min(scaleX, scaleY)
            }
            ZoomApi.TRANSFORMATION_CENTER_CROP -> {
                val scaleX = containerWidth / mContentScaledWidth
                val scaleY = containerHeight / mContentScaledHeight
                LOG.v("computeTransformationZoom", "centerCrop", "scaleX:", scaleX, "scaleY:", scaleY)
                return Math.max(scaleX, scaleY)
            }
            ZoomApi.TRANSFORMATION_NONE -> return 1f
            else -> return 1f
        }
    }

    /**
     * Computes the starting pan coordinates, given the current content dimensions and container
     * dimensions. This means applying the transformation gravity.
     */
    @ScaledPan
    private fun computeTransformationPan(): FloatArray {
        val result = floatArrayOf(0f, 0f)
        val extraWidth = mContentScaledWidth - containerWidth
        val extraHeight = mContentScaledHeight - containerHeight
        val gravity = computeTransformationGravity(mTransformationGravity)
        result[0] = -applyGravity(gravity, extraWidth, true)
        result[1] = -applyGravity(gravity, extraHeight, false)
        return result
    }

    /**
     * Computes an actual [Gravity] value from the input gravity,
     * which might also be [ZoomApi.TRANSFORMATION_GRAVITY_AUTO]. In this case we should
     * try to infer a [Gravity] from the alignment, then fallback to center.
     */
    @SuppressLint("RtlHardcoded")
    private fun computeTransformationGravity(input: Int): Int {
        return when (input) {
            ZoomApi.TRANSFORMATION_GRAVITY_AUTO -> {
                val horizontal = Alignment.toHorizontalGravity(mAlignment, Gravity.CENTER_HORIZONTAL)
                val vertical = Alignment.toVerticalGravity(mAlignment, Gravity.CENTER_VERTICAL)
                return horizontal or vertical
            }
            else -> input
        }
    }

    /**
     * Returns 0 for 'start' gravities, [extraSpace] for 'end' gravities, and half of it
     * for 'center' gravities.
     */
    @SuppressLint("RtlHardcoded")
    internal fun applyGravity(gravity: Int, extraSpace: Float, horizontal: Boolean): Float {
        val resolved = if (horizontal) {
            // TODO support START and END correctly.
            gravity and Gravity.HORIZONTAL_GRAVITY_MASK
        } else {
            gravity and Gravity.VERTICAL_GRAVITY_MASK
        }
        return when (resolved) {
            Gravity.TOP, Gravity.LEFT -> 0F
            Gravity.BOTTOM, Gravity.RIGHT -> extraSpace
            Gravity.CENTER_VERTICAL, Gravity.CENTER_HORIZONTAL -> 0.5F * extraSpace
            else -> 0F // Includes Gravity.NO_GRAVITY and unsupported mixes like FILL
        }
    }

    //endregion

    /**
     * This is required when the content is a View that has clickable hierarchies inside.
     * If true is returned, implementors should not pass the call to super.
     *
     * @param ev the motion event
     * @return whether we want to intercept the event
     */
    fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return stateManager.onInterceptTouchEvent(ev)
    }

    /**
     * Process the given touch event.
     * If true is returned, implementors should not pass the call to super.
     *
     * @param ev the motion event
     * @return whether we want to steal the event
     */
    fun onTouchEvent(ev: MotionEvent): Boolean {
        return stateManager.onTouchEvent(ev)
    }

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
            cancelAnimations()
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
            cancelAnimations()
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
            cancelAnimations()
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
    override fun zoomIn() = zoomBy(1.3f, animate = true)

    /**
     * Applies a small, animated zoom-out.
     * Shorthand for [zoomBy] with factor 0.7.
     */
    override fun zoomOut() = zoomBy(0.7f, animate = true)

    /**
     * Animates the actual matrix zoom to the given value.
     *
     * @param realZoom the new real zoom value
     * @param animate  whether to animate the transition
     */
    override fun realZoomTo(@RealZoom realZoom: Float, animate: Boolean) {
        val zoom = realZoom / mTransformationZoom
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
        zoomManager.setMaxZoom(maxZoom, type)
        if (zoom > zoomManager.getMaxZoom()) {
            zoomTo(zoomManager.getMaxZoom(), animate = true)
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
        zoomManager.setMinZoom(minZoom, type)
        if (zoom <= zoomManager.getMinZoom()) {
            zoomTo(zoomManager.getMinZoom(), animate = true)
        }
    }

    //endregion

    //region Apply values

    /**
     * Calls [PanManager.checkBounds] on both directions
     * and applies the correction to the matrix if needed.
     */
    private fun maybeAdjustPan(allowOverPan: Boolean) {
        @ScaledPan val fixX = panManager.checkBounds(true, allowOverPan)
        @ScaledPan val fixY = panManager.checkBounds(false, allowOverPan)
        if (fixX != 0f || fixY != 0f) {
            mMatrix.postTranslate(fixX, fixY)
            mMatrix.mapRect(mContentScaledRect, mContentRect)
        }
    }

    private val mActiveAnimators = mutableSetOf<ValueAnimator>()
    private val mCancelAnimationListener = object : AnimatorListenerAdapter() {

        override fun onAnimationEnd(animator: Animator) {
            cleanup(animator)
        }

        override fun onAnimationCancel(animator: Animator) {
            cleanup(animator)
        }

        private fun cleanup(animator: Animator) {
            animator.removeListener(this)
            mActiveAnimators.remove(animator)
            if (mActiveAnimators.isEmpty()) stateManager.makeIdle()
        }
    }

    /**
     * Prepares a [ValueAnimator] for the first run
     *
     * @return itself (for chaining)
     */
    private fun ValueAnimator.prepare(): ValueAnimator {
        this.duration = mAnimationDuration
        this.addListener(mCancelAnimationListener)
        this.interpolator = ANIMATION_INTERPOLATOR
        return this
    }

    /**
     * Starts a [ValueAnimator] with the given update function
     *
     * @return itself (for chaining)
     */
    private fun ValueAnimator.start(onUpdate: (ValueAnimator) -> Unit): ValueAnimator {
        this.addUpdateListener(onUpdate)
        this.start()
        mActiveAnimators.add(this)
        return this
    }

    /**
     * Calls [applyZoom] repeatedly
     * until the final zoom is reached, interpolating.
     *
     * @param zoom the new zoom
     * @param allowOverPinch whether overpinching is allowed
     */
    internal fun animateZoom(@Zoom zoom: Float, allowOverPinch: Boolean) {
        if (!stateManager.setAnimating()) return
        @Zoom val startZoom = this.zoom
        @Zoom val endZoom = zoomManager.checkBounds(zoom, allowOverPinch)
        ValueAnimator.ofFloat(startZoom, endZoom).prepare().start {
            LOG.v("animateZoom:", "animationStep:", it.animatedFraction)
            applyZoom(it.animatedValue as Float, allowOverPinch)
        }
    }

    /**
     * Calls [applyZoomAndAbsolutePan] repeatedly
     * until the final position is reached, interpolating.
     *
     * @param zoom            new zoom
     * @param x               final abs pan
     * @param y               final abs pan
     * @param allowOverScroll true if overscroll is allowed, false otherwise
     * @param allowOverPinch  true if overpinch is allowed, false otherwise
     * @param zoomTargetX     the x-axis zoom target
     * @param zoomTargetY     the y-axis zoom target
     */
    @SuppressLint("ObjectAnimatorBinding")
    internal fun animateZoomAndAbsolutePan(
            @Zoom zoom: Float,
            @AbsolutePan x: Float,
            @AbsolutePan y: Float,
            allowOverScroll: Boolean,
            allowOverPinch: Boolean = false,
            zoomTargetX: Float? = null,
            zoomTargetY: Float? = null
    ) {
        if (!stateManager.setAnimating()) return
        @Zoom val startZoom = this.zoom
        @Zoom val endZoom = zoomManager.checkBounds(zoom, allowOverScroll)
        val startPan = pan
        val targetPan = AbsolutePoint(x, y)
        LOG.i("animateZoomAndAbsolutePan:", "starting.", "startX:", startPan.x, "endX:", x, "startY:", startPan.y, "endY:", y)
        LOG.i("animateZoomAndAbsolutePan:", "starting.", "startZoom:", startZoom, "endZoom:", endZoom)

        ObjectAnimator.ofPropertyValuesHolder(
                PropertyValuesHolder.ofObject(
                        "pan",
                        TypeEvaluator { fraction: Float, startValue: AbsolutePoint, endValue: AbsolutePoint ->
                            startValue + (endValue - startValue) * fraction
                        }, startPan, targetPan),
                PropertyValuesHolder.ofFloat(
                        "zoom",
                        startZoom, endZoom)
        ).prepare().start {
            val newZoom = it.getAnimatedValue("zoom") as Float
            val currentPan = it.getAnimatedValue("pan") as AbsolutePoint
            applyZoomAndAbsolutePan(newZoom,
                    currentPan.x, currentPan.y,
                    allowOverScroll, allowOverPinch,
                    zoomTargetX, zoomTargetY)
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
    internal fun animateScaledPan(
            @ScaledPan deltaX: Float,
            @ScaledPan deltaY: Float,
            allowOverScroll: Boolean
    ) {
        if (!stateManager.setAnimating()) return
        val startPan = scaledPan
        val endPan = startPan + ScaledPoint(deltaX, deltaY)

        ValueAnimator.ofObject(TypeEvaluator { fraction, startValue: ScaledPoint, endValue: ScaledPoint ->
            startValue + (endValue - startValue) * fraction - scaledPan
        }, startPan, endPan).prepare().start {
            LOG.v("animateScaledPan:", "animationStep:", it.animatedFraction)
            val currentPan = it.animatedValue as ScaledPoint
            applyScaledPan(currentPan.x, currentPan.y, allowOverScroll)
        }
    }

    override fun setAnimationDuration(duration: Long) {
        mAnimationDuration = duration
    }

    /**
     * Applies the given zoom value, meant as a [Zoom] value (so not a [RealZoom]).
     * The zoom is applied so that the center point is kept in its place
     *
     * @param zoom           the new zoom value
     * @param allowOverPinch whether to overpinch
     * @param zoomTargetX    the x-axis zoom target
     * @param zoomTargetY    the y-axis zoom target
     */
    internal fun applyZoom(
            @Zoom zoom: Float,
            allowOverPinch: Boolean,
            allowOverScroll: Boolean = false,
            zoomTargetX: Float = containerWidth / 2f,
            zoomTargetY: Float = containerHeight / 2f,
            notifyListeners: Boolean = true
    ) {
        val newZoom = zoomManager.checkBounds(zoom, allowOverPinch)
        val scaleFactor = newZoom / this.zoom
        mMatrix.postScale(scaleFactor, scaleFactor, zoomTargetX, zoomTargetY)
        mMatrix.mapRect(mContentScaledRect, mContentRect)
        this.zoom = newZoom
        maybeAdjustPan(allowOverScroll)
        if (notifyListeners) {
            eventsDispatcher.dispatchOnMatrix()
        }
    }

    /**
     * Applies both zoom and absolute pan. This is like specifying a position.
     * The semantics of this are that after the position is applied, the zoom corresponds
     * to the given value, [ZoomApi.panX] returns x, [ZoomApi.panY] returns y.
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
    internal fun applyZoomAndAbsolutePan(
            @Zoom zoom: Float,
            @AbsolutePan x: Float,
            @AbsolutePan y: Float,
            allowOverScroll: Boolean,
            allowOverPinch: Boolean = false,
            zoomTargetX: Float? = null,
            zoomTargetY: Float? = null,
            notifyListeners: Boolean = true
    ) {
        // Translation
        val delta = AbsolutePoint(x, y) - pan
        mMatrix.preTranslate(delta.x, delta.y)
        mMatrix.mapRect(mContentScaledRect, mContentRect)

        // Scale
        val newZoom = zoomManager.checkBounds(zoom, allowOverPinch)
        val scaleFactor = newZoom / this.zoom
        // TODO: This used to work but I am not sure about it.
        // mMatrix.postScale(scaleFactor, scaleFactor, getScaledPanX(), getScaledPanY());
        // It keeps the pivot point at the scaled values 0, 0 (see applyPinch).
        // I think we should keep the current top, left.. Let's try:
        val pivotX = zoomTargetX ?: 0F
        val pivotY = zoomTargetY ?: 0F
        mMatrix.postScale(scaleFactor, scaleFactor, pivotX, pivotY)
        mMatrix.mapRect(mContentScaledRect, mContentRect)
        this.zoom = newZoom

        maybeAdjustPan(allowOverScroll)
        if (notifyListeners) {
            eventsDispatcher.dispatchOnMatrix()
        }
    }

    /**
     * Applies the given scaled translation.
     *
     * Scaled translation are applied through [Matrix.postTranslate],
     * which acts on the actual dimension of the rect.
     *
     * @param deltaX          the x translation
     * @param deltaY          the y translation
     * @param allowOverScroll whether to overScroll
     */
    internal fun applyScaledPan(
            @ScaledPan deltaX: Float,
            @ScaledPan deltaY: Float,
            allowOverScroll: Boolean
    ) {
        mMatrix.postTranslate(deltaX, deltaY)
        mMatrix.mapRect(mContentScaledRect, mContentRect)
        maybeAdjustPan(allowOverScroll)
        eventsDispatcher.dispatchOnMatrix()
    }

    //endregion

    //region Fling


    /**
     * Cancels all currently active animations triggered by either API calls with `animate = true`
     * or touch input flings. If no animation is currently active this is a no-op.
     *
     * @return true if anything was cancelled, false otherwise
     */
    override fun cancelAnimations(): Boolean {
        if (stateManager.isFlinging() || stateManager.isAnimating()) {
            stateManager.makeIdle()
            return true
        }
        return false
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
        return mContentScaledWidth.toInt()
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
        return mContentScaledHeight.toInt()
    }

    //endregion

    companion object {
        // TODO Make public, add API. Use androidx.Interpolator?
        private val ANIMATION_INTERPOLATOR = AccelerateDecelerateInterpolator()

        /**
         * The default animation duration
         */
        const val DEFAULT_ANIMATION_DURATION: Long = 280

        private val TAG = ZoomEngine::class.java.simpleName
        private val LOG = ZoomLogger.create(TAG)


    }
}
