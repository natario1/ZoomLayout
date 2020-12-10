package com.otaliastudios.zoom

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import com.otaliastudios.zoom.ZoomApi.*
import com.otaliastudios.zoom.internal.StateController
import com.otaliastudios.zoom.internal.UpdatesDispatcher
import com.otaliastudios.zoom.internal.gestures.PinchDetector
import com.otaliastudios.zoom.internal.gestures.ScrollFlingDetector
import com.otaliastudios.zoom.internal.matrix.MatrixController
import com.otaliastudios.zoom.internal.matrix.MatrixUpdate
import com.otaliastudios.zoom.internal.movement.PanManager
import com.otaliastudios.zoom.internal.movement.ZoomManager
import kotlin.math.max
import kotlin.math.min


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
 * The creator has to ensure that [setContainer] is called before any other operation is performed.
 *
 * @param context a valid context
 */
constructor(context: Context) : ZoomApi {

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
        // TODO (v2) remove this
        addListener(listener)
    }

    /**
     * Used for various components callbacks. We don't want to implement this in ZoomEngine
     * itself or these APIs will be publicly exposed as interface methods are public.
     */
    private inner class Callbacks : ViewTreeObserver.OnGlobalLayoutListener,
            StateController.Callback,
            MatrixController.Callback {

        override fun onGlobalLayout() {
            setContainerSize(container.width.toFloat(), container.height.toFloat())
        }

        // Post utilities

        override fun post(action: Runnable): Boolean = container.post(action)

        override fun postOnAnimation(action: Runnable) = container.postOnAnimation(action)

        // Matrix callbacks

        override fun onMatrixUpdate() {
            dispatcher.dispatchOnMatrix()
        }

        /**
         * If we need to apply the transformation ([firstTime] is true), we do so.
         * If we don't, we still do some computations to keep the appearance unchanged.
         */
        override fun onMatrixSizeChanged(oldZoom: Float, firstTime: Boolean) {
            LOG.w("onMatrixSizeChanged: firstTime:", firstTime, "oldZoom:", oldZoom,
                    "transformation:", transformationType,
                    "transformationZoom:", zoomManager.transformationZoom)
            stateController.makeIdle()
            if (firstTime) {
                // Compute the transformation zoom for the first time, which means applying the transformation.
                // Then zoom to this value so we are left with @Zoom=1 and can apply the zoom boundaries.
                zoomManager.transformationZoom = computeTransformationZoom()
                matrixController.applyUpdate {
                    zoomTo(zoomManager.transformationZoom, false)
                    notify = false
                }
                // Apply the transformation pan through the transformation gravity.
                val newPan = computeTransformationPan()
                matrixController.applyUpdate { panTo(newPan, false) }
            } else {
                // We were initialized, but some size changed. We will:
                // - Recompute the transformationZoom: since size changed, the old makes no sense
                // - Reapply the old zoom so it is kept unchanged and bounds are applied
                zoomManager.transformationZoom = computeTransformationZoom()
                matrixController.applyUpdate { zoomTo(realZoom, false) }
            }
            LOG.i("onMatrixSizeChanged: newTransformationZoom:", zoomManager.transformationZoom,
                    "newRealZoom:", realZoom, "newZoom:", zoom)
        }

        // State callbacks

        override fun isStateAllowed(newState: Int): Boolean {
            return matrixController.isInitialized
        }

        override fun onStateIdle() {
            dispatcher.dispatchOnIdle()
        }

        override fun cleanupState(@StateController.State oldState: Int) {
            when (oldState) {
                StateController.ANIMATING -> matrixController.cancelAnimations()
                StateController.FLINGING -> scrollFlingDetector.cancelFling()
            }
        }

        // Gesture callbacks

        override fun endScrollGesture() {
            scrollFlingDetector.cancelScroll()
        }

        override fun maybeStartPinchGesture(event: MotionEvent): Boolean {
            return pinchDetector.maybeStart(event)
        }

        override fun maybeStartScrollFlingGesture(event: MotionEvent): Boolean {
            return scrollFlingDetector.maybeStart(event)
        }
    }

    // Options & state
    private var transformationType = ZoomApi.TRANSFORMATION_CENTER_INSIDE
    private var transformationGravity = ZoomApi.TRANSFORMATION_GRAVITY_AUTO

    // Internal
    private lateinit var container: View
    private val callbacks = Callbacks()

    @Suppress("LeakingThis")
    private val dispatcher = UpdatesDispatcher(this)
    private val stateController = StateController(callbacks)

    @Suppress("LeakingThis")
    private val panManager = PanManager(this) { matrixController }

    @Suppress("LeakingThis")
    internal val zoomManager = ZoomManager(this) { matrixController }
    private val matrixController: MatrixController = MatrixController(zoomManager, panManager, stateController, callbacks)

    // Gestures
    private val scrollFlingDetector = ScrollFlingDetector(context, panManager, stateController, matrixController)
    private val pinchDetector = PinchDetector(context, zoomManager, panManager, stateController, matrixController)

    //region MatrixController delegates

    /**
     * Gets the current zoom value, including the base zoom that was eventually applied during
     * the starting transformation, see [setTransformation].
     * This value will match the scaleX - scaleY values you get into the [Matrix],
     * and is the actual scale value of the content from its original size.
     *
     * @return the real zoom
     */
    @RealZoom
    override val realZoom get() = matrixController.zoom

    /**
     * The current pan as an [AbsolutePoint].
     */
    // Make a copy so it is not changed from outside.
    override val pan get() = matrixController.pan.copy()

    /**
     * Returns the current horizontal pan value, in content absolute coordinates
     * (that is, as if there was no zoom at all).
     *
     * @return the current horizontal pan
     */
    @AbsolutePan
    override val panX get() = matrixController.panX

    /**
     * Returns the current vertical pan value, in content absolute coordinates
     * (that is, as if there was no zoom at all).
     *
     * @return the current vertical pan
     */
    @AbsolutePan
    override val panY get() = matrixController.panY

    /**
     * The current pan as a [ScaledPoint].
     */
    // Make a copy so it is not changed from outside.
    override val scaledPan get() = matrixController.scaledPan.copy()

    /**
     * Returns the current horizontal pan value, in content scaled coordinates
     * (that is, including the current zoom).
     *
     * @return the current horizontal pan
     */
    override val scaledPanX get() = matrixController.scaledPanX

    /**
     * Returns the current vertical pan value, in content scaled coordinates
     * (that is, including the current zoom).
     *
     * @return the current vertical pan
     */
    override val scaledPanY get() = matrixController.scaledPanY

    /**
     * Returns the current matrix. This can be changed from the outside, but is not
     * guaranteed to remain stable.
     *
     * @return the current matrix.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    val matrix get() = matrixController.matrix

    /**
     * Returns the content width as passed to [setContentSize].
     * @return the current width
     */
    @AbsolutePan
    val contentWidth get() = matrixController.contentWidth

    /**
     * Returns the content height as passed to [setContentSize].
     * @return the current height
     */
    @AbsolutePan
    val contentHeight get() = matrixController.contentHeight

    /**
     * Returns the container width as passed to [setContainerSize].
     * @return the current width
     */
    val containerWidth get() = matrixController.containerWidth

    /**
     * Returns the container height as passed to [setContainerSize].
     * @return the current height
     */
    val containerHeight get() = matrixController.containerHeight

    //endregion

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
    override val zoom get() = zoomManager.realZoomToZoom(realZoom)

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
        // TODO (v2) remove matrix, users can use engine.matrix / engine.pan / engine.zoom whatever

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
    // TODO (v2) remove this class, users can use engine.matrix / engine.pan / engine.zoom whatever
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
        // fail fast if the engine is not initialized properly
        if (!::container.isInitialized) {
            error("container is not initialized.")
        }
        dispatcher.addListener(listener)
    }

    /**
     * Removes a previously registered listener.
     * @param listener the listener to be removed
     */
    @Suppress("unused")
    fun removeListener(listener: Listener) {
        dispatcher.removeListener(listener)
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
     * Set the [OverPanRangeProvider] that specifies the amount of
     * overpan to allow.
     */
    override fun setOverPanRange(provider: OverPanRangeProvider) {
        panManager.overPanRangeProvider = provider
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
        zoomManager.isOverEnabled = overPinchable
    }

    /**
     * Set the [OverZoomRangeProvider] that specifies the amount of
     * overzoom to allow.
     */
    override fun setOverZoomRange(provider: OverZoomRangeProvider) {
        zoomManager.overZoomRangeProvider = provider
    }

    /**
     * Controls whether zoom using pinch gesture is enabled or not.
     *
     * @param enabled true enables zooming, false disables it
     */
    override fun setZoomEnabled(enabled: Boolean) {
        zoomManager.isEnabled = enabled
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
     * Controls whether scroll gesture is enabled or not.
     *
     * @param enabled true enables scroll gesture, false disables it
     */
    override fun setScrollEnabled(enabled: Boolean) {
        scrollFlingDetector.scrollEnabled = enabled
    }

    /**
     * Controls whether one finger scroll gesture is enabled or not.
     *
     * @param enabled true enables one finger scroll gesture, false disables it
     */
    override fun setOneFingerScrollEnabled(enabled: Boolean) {
        scrollFlingDetector.oneFingerScrollEnabled = enabled
    }

    /**
     * Controls whether two fingers scroll gesture is enabled or not.
     *
     * @param enabled true enables two fingers scroll gesture, false disables it
     */
    override fun setTwoFingersScrollEnabled(enabled: Boolean) {
        scrollFlingDetector.twoFingersScrollEnabled = enabled
    }

    /**
     * Controls whether three fingers scroll gesture is enabled or not.
     *
     * @param enabled true enables three fingers scroll gesture, false disables it
     */
    override fun setThreeFingersScrollEnabled(enabled: Boolean) {
        scrollFlingDetector.threeFingersScrollEnabled = enabled
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
        transformationType = transformation
        transformationGravity = gravity
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
        panManager.alignment = alignment
    }

    //endregion

    //region Initialize

    /**
     * Set a container to perform transformations on.
     * This method can only be called once at initialization time. It throws an exception if
     * it is called twice.
     *
     * @param container view
     */
    fun setContainer(container: View) {
        if(this::container.isInitialized) {
            error("container already set")
        }
        this.container = container
        this.container.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                view.viewTreeObserver.addOnGlobalLayoutListener(callbacks)
            }
            override fun onViewDetachedFromWindow(view: View) {
                view.viewTreeObserver.removeOnGlobalLayoutListener(callbacks)
            }
        })
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
        matrixController.setContentSize(width, height, applyTransformation)
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
        matrixController.setContainerSize(width, height, applyTransformation)
    }

    /**
     * Clears the current state, and stops dispatching matrix events
     * until the view is laid out again and [ZoomEngine.setContentSize]
     * is called.
     */
    @Suppress("unused")
    fun clear() {
        zoomManager.clear()
        panManager.clear()
        matrixController.clear()
    }

    /**
     * Computes the starting zoom, which means applying the transformation.
     */
    private fun computeTransformationZoom(): Float {
        when (transformationType) {
            ZoomApi.TRANSFORMATION_CENTER_INSIDE -> {
                val scaleX = containerWidth / contentWidth
                val scaleY = containerHeight / contentHeight
                LOG.v("computeTransformationZoom", "centerInside", "scaleX:", scaleX, "scaleY:", scaleY)
                return min(scaleX, scaleY)
            }
            ZoomApi.TRANSFORMATION_CENTER_CROP -> {
                val scaleX = containerWidth / contentWidth
                val scaleY = containerHeight / contentHeight
                LOG.v("computeTransformationZoom", "centerCrop", "scaleX:", scaleX, "scaleY:", scaleY)
                return max(scaleX, scaleY)
            }
            ZoomApi.TRANSFORMATION_NONE -> return 1f
            else -> return 1f
        }
    }

    /**
     * Computes the starting pan coordinates, given the current content dimensions and container
     * dimensions. This means applying the transformation gravity.
     */
    private fun computeTransformationPan(): ScaledPoint {
        val extraWidth = contentWidth * realZoom - containerWidth
        val extraHeight = contentHeight * realZoom - containerHeight
        val gravity = computeTransformationGravity(transformationGravity)
        val x = -panManager.applyGravity(gravity, extraWidth, true)
        val y = -panManager.applyGravity(gravity, extraHeight, false)
        return ScaledPoint(x, y)
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
                val horizontal = Alignment.toHorizontalGravity(panManager.alignment, Gravity.CENTER_HORIZONTAL)
                val vertical = Alignment.toVerticalGravity(panManager.alignment, Gravity.CENTER_VERTICAL)
                return horizontal or vertical
            }
            else -> input
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
        return stateController.onInterceptTouchEvent(ev)
    }

    /**
     * Process the given touch event.
     * If true is returned, implementors should not pass the call to super.
     *
     * @param ev the motion event
     * @return whether we want to steal the event
     */
    fun onTouchEvent(ev: MotionEvent): Boolean {
        return stateController.onTouchEvent(ev)
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
        val realZoom = zoomManager.zoomToRealZoom(zoom)
        val update = MatrixUpdate.obtain {
            zoomTo(realZoom, false)
            panTo(AbsolutePoint(x, y), false)
        }
        if (animate) {
            matrixController.animateUpdate(update)
        } else {
            cancelAnimations()
            matrixController.applyUpdate(update)
        }
    }

    /**
     * Moves to the center of the content.
     *
     * @param zoom    the desired zoom value
     * @param animate whether to animate the transition
     */
    override fun moveToCenter(@Zoom zoom: Float?, animate: Boolean) {
        val targetZoom = zoom?.coerceIn(
                zoomManager.realZoomToZoom(zoomManager.getMinZoom()),
                zoomManager.realZoomToZoom(zoomManager.getMaxZoom())
        ) ?: this.zoom
        val targetRealZoom = zoomManager.zoomToRealZoom(targetZoom)

        val zoomLayoutCenterX: Float = (containerWidth / targetRealZoom) / 2f
        val zoomLayoutCenterY: Float = (containerHeight / targetRealZoom) / 2f
        val contentCenterX: Float = contentWidth / 2f
        val contentCenterY: Float = contentHeight / 2f
        val diffX = (contentCenterX - zoomLayoutCenterX)
        val diffY = (contentCenterY - zoomLayoutCenterY)

        moveTo(targetZoom, -diffX, -diffY, animate)
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
        val update = MatrixUpdate.obtain { panBy(AbsolutePoint(dx, dy), false) }
        if (animate) {
            matrixController.animateUpdate(update)
        } else {
            cancelAnimations()
            matrixController.applyUpdate(update)
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
        val realZoom = zoomManager.zoomToRealZoom(zoom)
        realZoomTo(realZoom, animate)
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
        val update = MatrixUpdate.obtain { zoomTo(realZoom, false) }
        if (animate) {
            matrixController.animateUpdate(update)
        } else {
            cancelAnimations()
            matrixController.applyUpdate(update)
        }
    }

    /**
     * Get the currently allowed max zoom.
     * If [ZoomApi.setOverPinchable] is set to true, this can be over-pinched
     * for a brief time.
     *
     * @see zoom
     * @see realZoom
     */
    override fun getMaxZoom(): Float  = zoomManager.maxZoom

    /**
     * Get the currently set max zoom type.
     *
     * @see getMaxZoom
     */
    @ZoomType
    override fun getMaxZoomType(): Int = zoomManager.maxZoomMode

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
            realZoomTo(zoomManager.getMaxZoom(), animate = true)
        }
    }

    /**
     * Get the currently allowed min zoom.
     * If [ZoomApi.setOverPinchable] is set to true, this can be over-pinched
     * for a brief time.
     *
     * @see zoom
     * @see realZoom
     */
    override fun getMinZoom(): Float  = zoomManager.minZoom

    /**
     * Get the currently set min zoom type.
     *
     * @see getMinZoom
     */
    @ZoomType
    override fun getMinZoomType(): Int = zoomManager.minZoomMode

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
        if (realZoom <= zoomManager.getMinZoom()) {
            realZoomTo(zoomManager.getMinZoom(), animate = true)
        }
    }

    //endregion

    //region Apply values

    /**
     * Sets the duration of animations triggered by zoom and pan APIs.
     * Defaults to [ZoomEngine.DEFAULT_ANIMATION_DURATION].
     *
     * @param duration new animation duration
     */
    override fun setAnimationDuration(duration: Long) {
        matrixController.animationDuration = duration
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
        if (stateController.isFlinging()) {
            scrollFlingDetector.cancelFling()
            return true
        } else if (stateController.isAnimating()) {
            stateController.makeIdle()
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
        return (-matrixController.scaledPanX).toInt()
    }

    /**
     * Helper for implementing [View.computeHorizontalScrollRange]
     * in custom views.
     *
     * @return the horizontal scroll range.
     */
    fun computeHorizontalScrollRange(): Int {
        return matrixController.contentScaledWidth.toInt()
    }

    /**
     * Helper for implementing [View.computeVerticalScrollOffset]
     * in custom views.
     *
     * @return the vertical scroll offset.
     */
    fun computeVerticalScrollOffset(): Int {
        return (-matrixController.scaledPanY).toInt()
    }

    /**
     * Helper for implementing [View.computeVerticalScrollRange]
     * in custom views.
     *
     * @return the vertical scroll range.
     */
    fun computeVerticalScrollRange(): Int {
        return matrixController.contentScaledHeight.toInt()
    }

    //endregion

    companion object {

        /**
         * The default animation duration
         */
        const val DEFAULT_ANIMATION_DURATION: Long = 280

        private val TAG = ZoomEngine::class.java.simpleName
        private val LOG = ZoomLogger.create(TAG)


    }
}
