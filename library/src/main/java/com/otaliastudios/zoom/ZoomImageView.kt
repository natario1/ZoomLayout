package com.otaliastudios.zoom

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.widget.ImageView
import androidx.annotation.AttrRes
import com.otaliastudios.zoom.ZoomApi.ZoomType


/**
 * Uses [ZoomEngine] to allow zooming and pan events to the inner drawable.
 *
 *
 * TODO: support padding (from inside ZoomEngine that gets the view)
 */
@SuppressLint("AppCompatCustomView")
open class ZoomImageView
private constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int, val engine: ZoomEngine = ZoomEngine(context))
    : ImageView(context, attrs, defStyleAttr), ZoomEngine.Listener, ZoomApi by engine {

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null, @AttrRes defStyleAttr: Int = 0)
            : this(context, attrs, defStyleAttr, ZoomEngine(context))

    //endregion

    //region APIs

    private val mMatrix = Matrix()

    private val isInSharedElementTransition: Boolean
        get() = width != measuredWidth || height != measuredHeight

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

        imageMatrix = mMatrix
        scaleType = ImageView.ScaleType.MATRIX
    }

    //region Internal

    override fun setImageDrawable(drawable: Drawable?) {
        if (drawable != null) {
            engine.setContentSize(drawable.intrinsicWidth.toFloat(),
                    drawable.intrinsicHeight.toFloat())
        }
        super.setImageDrawable(drawable)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // Using | so click listeners work.
        return engine.onTouchEvent(ev) or super.onTouchEvent(ev)
    }

    override fun onUpdate(engine: ZoomEngine, matrix: Matrix) {
        // matrix.getValues(mTemp);
        // Log.e("ZoomEngineDEBUG", "View - Received update, matrix scale = " + mTemp[Matrix.MSCALE_X]);
        mMatrix.set(matrix)
        imageMatrix = mMatrix
        awakenScrollBars()
    }

    override fun onIdle(engine: ZoomEngine) {}

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        /* Log.e("ZoomEngineDEBUG", "View - dispatching container size" +
                " width: " + getWidth() + ", height:" + getHeight() +
                " - different?" + isInSharedElementTransition()); */
        engine.setContainerSize(width.toFloat(), height.toFloat(), true)
    }

    override fun onDraw(canvas: Canvas) {
        if (isInSharedElementTransition) {
            // The framework will often change our matrix between onUpdate and onDraw, leaving us with
            // a bad first frame that makes a noticeable flash. Replace the matrix values with our own.
            imageMatrix = mMatrix
        }
        super.onDraw(canvas)
    }

    override fun computeHorizontalScrollOffset(): Int = engine.computeHorizontalScrollOffset()

    override fun computeHorizontalScrollRange(): Int = engine.computeHorizontalScrollRange()

    override fun computeVerticalScrollOffset(): Int = engine.computeVerticalScrollOffset()

    override fun computeVerticalScrollRange(): Int = engine.computeVerticalScrollRange()

    //endregion

}