package com.otaliastudios.zoom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import com.otaliastudios.zoom.ZoomApi.ZoomType


/**
 * Uses [ZoomEngine] to allow zooming and pan events onto a view hierarchy.
 * The hierarchy must be contained in a single view, added to this layout
 * (like what you do with a ScrollView).
 *
 *
 * If the hierarchy has clickable children that should react to touch events, you are
 * required to call [setHasClickableChildren] or use the attribute.
 * This is off by default because it is more expensive in terms of performance.
 *
 *
 * Currently padding to this view / margins to the child view are NOT supported.
 *
 *
 * TODO: support padding (from inside ZoomEngine that gets the view)
 * TODO: support layout_margin (here)
 */
open class ZoomLayout
private constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int, val engine: ZoomEngine = ZoomEngine(context))
    : FrameLayout(context, attrs, defStyleAttr), ZoomEngine.Listener, ZoomApi by engine {

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null, @AttrRes defStyleAttr: Int = 0)
            : this(context, attrs, defStyleAttr, ZoomEngine(context))

    private val mMatrix = Matrix()
    private val mMatrixValues = FloatArray(9)
    private var mHasClickableChildren: Boolean = false

    init {
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.ZoomEngine, defStyleAttr, 0)
        val overScrollHorizontal = a.getBoolean(R.styleable.ZoomEngine_overScrollHorizontal, true)
        val overScrollVertical = a.getBoolean(R.styleable.ZoomEngine_overScrollVertical, true)
        val horizontalPanEnabled = a.getBoolean(R.styleable.ZoomEngine_horizontalPanEnabled, true)
        val verticalPanEnabled = a.getBoolean(R.styleable.ZoomEngine_verticalPanEnabled, true)
        val overPinchable = a.getBoolean(R.styleable.ZoomEngine_overPinchable, true)
        val zoomEnabled = a.getBoolean(R.styleable.ZoomEngine_zoomEnabled, true)
        val flingEnabled = a.getBoolean(R.styleable.ZoomEngine_flingEnabled, true)
        val allowFlingInOverscroll = a.getBoolean(R.styleable.ZoomEngine_allowFlingInOverscroll, true)
        val hasChildren = a.getBoolean(R.styleable.ZoomEngine_hasClickableChildren, false)
        val minZoom = a.getFloat(R.styleable.ZoomEngine_minZoom, ZoomApi.MIN_ZOOM_DEFAULT)
        val maxZoom = a.getFloat(R.styleable.ZoomEngine_maxZoom, ZoomApi.MAX_ZOOM_DEFAULT)
        @ZoomType val minZoomMode = a.getInteger(R.styleable.ZoomEngine_minZoomType, ZoomApi.MIN_ZOOM_DEFAULT_TYPE)
        @ZoomType val maxZoomMode = a.getInteger(R.styleable.ZoomEngine_maxZoomType, ZoomApi.MAX_ZOOM_DEFAULT_TYPE)
        val transformation = a.getInteger(R.styleable.ZoomEngine_transformation, ZoomApi.TRANSFORMATION_CENTER_INSIDE)
        val transformationGravity = a.getInt(R.styleable.ZoomEngine_transformationGravity, ZoomApi.TRANSFORMATION_GRAVITY_AUTO)
        val alignment = a.getInt(R.styleable.ZoomEngine_alignment, ZoomApi.ALIGNMENT_DEFAULT)
        val animationDuration = a.getInt(R.styleable.ZoomEngine_animationDuration, ZoomEngine.DEFAULT_ANIMATION_DURATION.toInt()).toLong()
        a.recycle()

        engine.setContainer(this)
        engine.addListener(this)
        setTransformation(transformation, transformationGravity)
        setAlignment(alignment)
        setOverScrollHorizontal(overScrollHorizontal)
        setOverScrollVertical(overScrollVertical)
        setHorizontalPanEnabled(horizontalPanEnabled)
        setVerticalPanEnabled(verticalPanEnabled)
        setOverPinchable(overPinchable)
        setZoomEnabled(zoomEnabled)
        setFlingEnabled(flingEnabled)
        setAllowFlingInOverscroll(allowFlingInOverscroll)
        setAnimationDuration(animationDuration)
        setMinZoom(minZoom, minZoomMode)
        setMaxZoom(maxZoom, maxZoomMode)
        setHasClickableChildren(hasChildren)

        setWillNotDraw(false)
    }

    //region Internal

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {

        // Measure ourselves as MATCH_PARENT
        val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)
        if (widthMode == View.MeasureSpec.UNSPECIFIED || heightMode == View.MeasureSpec.UNSPECIFIED) {
            throw RuntimeException("$TAG must be used with fixed dimensions (e.g. match_parent)")
        }
        val widthSize = View.MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = View.MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(widthSize, heightSize)

        // Measure our child as unspecified.
        val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        measureChildren(spec, spec)
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        if (childCount == 0) {
            child.viewTreeObserver.addOnGlobalLayoutListener { engine.setContentSize(child.width.toFloat(), child.height.toFloat()) }
            super.addView(child, index, params)
        } else {
            throw RuntimeException("$TAG accepts only a single child.")
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return engine.onInterceptTouchEvent(ev) || mHasClickableChildren && super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return engine.onTouchEvent(ev) || mHasClickableChildren && super.onTouchEvent(ev)
    }

    override fun onUpdate(engine: ZoomEngine, matrix: Matrix) {
        mMatrix.set(matrix)
        if (mHasClickableChildren) {
            if (childCount > 0) {
                val child = getChildAt(0)
                mMatrix.getValues(mMatrixValues)
                child.pivotX = 0f
                child.pivotY = 0f
                child.translationX = mMatrixValues[Matrix.MTRANS_X]
                child.translationY = mMatrixValues[Matrix.MTRANS_Y]
                child.scaleX = mMatrixValues[Matrix.MSCALE_X]
                child.scaleY = mMatrixValues[Matrix.MSCALE_Y]
            }
        } else {
            invalidate()
        }

        if ((isHorizontalScrollBarEnabled || isVerticalScrollBarEnabled) && !awakenScrollBars()) {
            invalidate()
        }
    }

    override fun onIdle(engine: ZoomEngine) {}

    override fun computeHorizontalScrollOffset(): Int = engine.computeHorizontalScrollOffset()

    override fun computeHorizontalScrollRange(): Int = engine.computeHorizontalScrollRange()

    override fun computeVerticalScrollOffset(): Int = engine.computeVerticalScrollOffset()

    override fun computeVerticalScrollRange(): Int = engine.computeVerticalScrollRange()

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        val result: Boolean

        if (!mHasClickableChildren) {
            val save = canvas.save()
            canvas.concat(mMatrix)
            result = super.drawChild(canvas, child, drawingTime)
            canvas.restoreToCount(save)
        } else {
            result = super.drawChild(canvas, child, drawingTime)
        }

        return result
    }

    //endregion

    //region APIs


    /**
     * Whether the view hierarchy inside has (or will have) clickable children.
     * This is false by default.
     *
     * @param hasClickableChildren whether we have clickable children
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun setHasClickableChildren(hasClickableChildren: Boolean) {
        LOG.i("setHasClickableChildren:", "old:", mHasClickableChildren, "new:", hasClickableChildren)
        if (mHasClickableChildren && !hasClickableChildren) {
            // Revert any transformation that was applied to our child.
            if (childCount > 0) {
                val child = getChildAt(0)
                child.scaleX = 1f
                child.scaleY = 1f
                child.translationX = 0f
                child.translationY = 0f
            }
        }
        mHasClickableChildren = hasClickableChildren

        // Update if we were laid out already.
        if (width > 0 && height > 0) {
            if (mHasClickableChildren) {
                onUpdate(engine, mMatrix)
            } else {
                invalidate()
            }
        }
    }

    //endregion

    companion object {
        private val TAG = ZoomLayout::class.java.simpleName
        private val LOG = ZoomLogger.create(TAG)
    }

}