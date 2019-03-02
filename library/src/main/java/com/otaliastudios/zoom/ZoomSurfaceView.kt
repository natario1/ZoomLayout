package com.otaliastudios.zoom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.*
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import com.otaliastudios.zoom.ZoomApi.ZoomType
import com.otaliastudios.zoom.opengl.program.EglRectTextureProgram
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10


/**
 * Uses [ZoomEngine] to allow zooming and pan events onto a GL rendered surface.
 */
open class ZoomSurfaceView
private constructor(
        context: Context,
        attrs: AttributeSet?,
        val engine: ZoomEngine = ZoomEngine(context)
) : GLSurfaceView(context, attrs), ZoomEngine.Listener, ZoomApi by engine, GLSurfaceView.Renderer {

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null)
            : this(context, attrs, ZoomEngine(context))

    private val callbacks = mutableListOf<Callback>()
    private var surfaceTexture: SurfaceTexture? = null
    private val surfaceTextureTransformMatrix = FloatArray(16)
    private var program: EglRectTextureProgram? = null
    private var textureId = 0

    /**
     * A Callback to be notified of the surface lifecycle.
     * All methods are invoked on the ui thread for convenience.
     */
    interface Callback {

        /**
         * The underlying surface was just created. At this point you
         * can call [createSurface] to obtain a consumable surface.
         */
        @UiThread
        fun onZoomSurfaceCreated(view: ZoomSurfaceView)

        /**
         * The underlying surface has just changed dimensions. This is also
         * called after creation to notify of initial dimensions.
         */
        @UiThread
        fun onZoomSurfaceChanged(view: ZoomSurfaceView, width: Int, height: Int)

        /**
         * The underlying surface has just been destroyed. At this point
         * surfaces created with [createSurface] should not be used anymore.
         */
        @UiThread
        fun onZoomSurfaceDestroyed(view: ZoomSurfaceView)
    }

    /**
     * Adds a new [Callback] to be notified of the surface state.
     * Should be called as soon as possible.
     */
    fun addCallback(callback: Callback) {
        callbacks.add(callback)
    }

    /**
     * Removes a [Callback] previously added with [addCallback].
     */
    fun removeCallback(callback: Callback) {
        callbacks.remove(callback)
    }

    init {
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.ZoomEngine, 0, 0)
        val horizontalPanEnabled = a.getBoolean(R.styleable.ZoomEngine_horizontalPanEnabled, true)
        val verticalPanEnabled = a.getBoolean(R.styleable.ZoomEngine_verticalPanEnabled, true)
        val zoomEnabled = a.getBoolean(R.styleable.ZoomEngine_zoomEnabled, true)
        val flingEnabled = a.getBoolean(R.styleable.ZoomEngine_flingEnabled, true)
        val allowFlingInOverscroll = a.getBoolean(R.styleable.ZoomEngine_allowFlingInOverscroll, true)
        val maxZoom = a.getFloat(R.styleable.ZoomEngine_maxZoom, ZoomApi.MAX_ZOOM_DEFAULT)
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
        setOverScrollHorizontal(false)
        setOverScrollVertical(false)
        setHorizontalPanEnabled(horizontalPanEnabled)
        setVerticalPanEnabled(verticalPanEnabled)
        setOverPinchable(false)
        setZoomEnabled(zoomEnabled)
        setFlingEnabled(flingEnabled)
        setAllowFlingInOverscroll(allowFlingInOverscroll)
        setAnimationDuration(animationDuration)
        setMinZoom(1F, ZoomApi.TYPE_REAL_ZOOM)
        setMaxZoom(maxZoom, maxZoomMode)

        setEGLContextClientVersion(2)
        setRenderer(this)
        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        holder.addCallback(object: SurfaceHolder.Callback {

            // Surface has changed. Disptach to callbacks.
            override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
                post { callbacks.forEach { it.onZoomSurfaceChanged(this@ZoomSurfaceView, width, height) } }
            }

            // Surface was created. Do nothing as we use the Renderer callback.
            override fun surfaceCreated(holder: SurfaceHolder?) {}

            // Surface was destroyed. Release stuff.
            override fun surfaceDestroyed(holder: SurfaceHolder?) { onSurfaceDestroyed() }
        })
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        val engineResult = event?.let { engine.onTouchEvent(it) } ?: false
        return engineResult || super.dispatchTouchEvent(event)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        engine.setContentSize(measuredWidth.toFloat(), measuredHeight.toFloat())
    }

    /**
     * Returns a [Surface] that can be consumed by some buffer provider.
     * This method should be called
     */
    fun createSurface(): Surface {
        if (surfaceTexture == null) {
            throw IllegalStateException("createSurface() must be called after ZoomSurfaceView.Callback.onZoomSurfaceCreated()")
        }
        return Surface(surfaceTexture!!)
    }

    @WorkerThread
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = EglRectTextureProgram()
        textureId = program!!.createTexture()
        surfaceTexture = SurfaceTexture(textureId).also {
            // Since we are using RENDERMODE_WHEN_DIRTY, we must notify the SurfaceView
            // of dirtyness, so that it draws again. This is how it's done. requestRender is thread-safe.
            it.setOnFrameAvailableListener { requestRender() }
        }

        post {
            callbacks.forEach { it.onZoomSurfaceCreated(this) }
        }
    }

    @WorkerThread
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {}

    private fun onSurfaceDestroyed() {
        surfaceTexture?.release()
        surfaceTexture = null
        program?.release()
        program = null
        callbacks.forEach { it.onZoomSurfaceDestroyed(this) }
    }

    /**
     * When we have a zoom update, just request a new rendered frame.
     * This will post the request on the renderer thread and finally invoke
     * [onDrawFrame].
     */
    override fun onUpdate(engine: ZoomEngine, matrix: Matrix) {
        requestRender()
    }

    @WorkerThread
    override fun onDrawFrame(gl: GL10?) {
        val texture = surfaceTexture ?: return
        val program = program ?: return
        val translX = -panX / measuredWidth
        val translY = panY / measuredHeight
        val scale = 1F / realZoom
        LOG.i("performing a  draw operation. translX: $translX translY: $translY scale: $scale")

        // Latch the latest frame.  If there isn't anything new,
        // we'll just re-use whatever was there before.
        texture.updateTexImage()
        texture.getTransformMatrix(surfaceTextureTransformMatrix)
        android.opengl.Matrix.translateM(surfaceTextureTransformMatrix, 0, translX, translY, 0F)
        android.opengl.Matrix.scaleM(surfaceTextureTransformMatrix, 0, scale, scale, 1F)
        program.drawRect(textureId, surfaceTextureTransformMatrix)
    }

    override fun onIdle(engine: ZoomEngine) {}

    companion object {
        private val TAG = ZoomSurfaceView::class.java.simpleName
        private val LOG = ZoomLogger.create(TAG)
    }

}