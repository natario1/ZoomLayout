package com.otaliastudios.zoom

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.GLSurfaceView
import android.os.Build
import android.util.AttributeSet
import android.view.*
import androidx.annotation.RequiresApi
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import com.otaliastudios.zoom.ZoomApi.ZoomType
import com.otaliastudios.zoom.opengl.core.Egl
import com.otaliastudios.zoom.opengl.core.EglConfigChooser
import com.otaliastudios.zoom.opengl.core.EglContextFactory
import com.otaliastudios.zoom.opengl.draw.EglRect
import com.otaliastudios.zoom.opengl.program.EglFlatProgram
import com.otaliastudios.zoom.opengl.program.EglRectFlatProgram
import com.otaliastudios.zoom.opengl.program.EglRectTextureProgram
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10


/**
 * Uses [ZoomEngine] to allow zooming and pan events onto a GL rendered surface.
 *
 * This class does not allow overscrolling nor overpincinhg. This means that these XML attributes are
 * ignored and [setOverScrollHorizontal], [setOverScrollVertical], [setOverPinchable] do nothing.
 * You can still call these methods on the underlying engine, but this will create visible artifacts.
 *
 * The same goes for [setMinZoom]. This is forced to be 1 so that you can never zoom out too much.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
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
    private val surfaceTextureTransformMatrix = FloatArray(16)

    /**
     * A [Surface] that can be consumed by some buffer provider.
     * This will be non-null after [Callback.onZoomSurfaceCreated]
     * and null again after [Callback.onZoomSurfaceDestroyed].
     *
     * This class cares about releasing this object when done.
     */
    var surface: Surface? = null
        private set

    /**
     * A [SurfaceTexture] that can be consumed by some buffer provider.
     * This will be non-null after [Callback.onZoomSurfaceCreated]
     * and null again after [Callback.onZoomSurfaceDestroyed].
     *
     * This class cares about releasing this object when done.
     */
    private var surfaceTexture: SurfaceTexture? = null
        private set


    private var program: EglRectTextureProgram? = null
    private var textureId = 0

    /**
     * A Callback to be notified of the surface lifecycle.
     * All methods are invoked on the ui thread for convenience.
     */
    interface Callback {

        /**
         * The underlying surface was just created. At this point you
         * can call [surface] to obtain a consumable surface.
         */
        @UiThread
        fun onZoomSurfaceCreated(view: ZoomSurfaceView)

        /**
         * The underlying surface has just been destroyed. At this point
         * the surface obtained with [surface] is about to be released.
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
        engine.setOverScrollHorizontal(false)
        engine.setOverScrollVertical(false)
        engine.setOverPinchable(false)
        engine.setMinZoom(1F, ZoomApi.TYPE_REAL_ZOOM)
        setTransformation(transformation, transformationGravity)
        setAlignment(alignment)
        setHorizontalPanEnabled(horizontalPanEnabled)
        setVerticalPanEnabled(verticalPanEnabled)
        setZoomEnabled(zoomEnabled)
        setFlingEnabled(flingEnabled)
        setAllowFlingInOverscroll(allowFlingInOverscroll)
        setAnimationDuration(animationDuration)
        setMaxZoom(maxZoom, maxZoomMode)

        setEGLContextFactory(EglContextFactory.GLES2)
        setEGLConfigChooser(EglConfigChooser.GLES2)
        setRenderer(this)
        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    // This is not allowed in this class, because I can't get CLAMP_TO_BORDER to work.
    final override fun setOverScrollHorizontal(overScroll: Boolean) {}

    // This is not allowed in this class, because I can't get CLAMP_TO_BORDER to work.
    final override fun setOverScrollVertical(overScroll: Boolean) {}

    // This is not allowed in this class, because I can't get CLAMP_TO_BORDER to work.
    final override fun setOverPinchable(overPinchable: Boolean) {}

    // This is not supported - we want to force this to 1.
    final override fun setMinZoom(minZoom: Float, type: Int) {}

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // Using | so click listeners work.
        return engine.onTouchEvent(ev) or super.onTouchEvent(ev)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // Strictly speaking setContainerSize is not needed, because the engine gets that onGlobalLayout (after),
        // however it's better to sync the two calls so that there is no weird state in the middle
        // that can cause distortions in the preview. We also ensure that applyTransformation is true.
        engine.setContainerSize(measuredWidth.toFloat(), measuredHeight.toFloat(), true)
        engine.setContentSize(measuredWidth.toFloat(), measuredHeight.toFloat(), true)
    }

    @WorkerThread
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        LOG.i("onSurfaceCreated")
        program = EglRectTextureProgram()
        textureId = program!!.createTexture()
        surfaceTexture = SurfaceTexture(textureId).also {
            // Since we are using RENDERMODE_WHEN_DIRTY, we must notify the SurfaceView
            // of dirtyness, so that it draws again. This is how it's done. requestRender is thread-safe.
            it.setOnFrameAvailableListener { requestRender() }
        }

        post {
            surface = Surface(surfaceTexture)
            callbacks.forEach { it.onZoomSurfaceCreated(this) }
        }
    }


    @WorkerThread
    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        gl.glViewport(0, 0, width, height)
        // Another option, like CameraView/GlCameraPreview does, is to force
        // recreate the GL thread using onPause() + onResume() [if width or height changed].
        // That relies on the fact that the initial glViewport is that of the surface and was
        // a workaround. It's better to update the viewport than force a recreation.
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow() // This call stops GL thread.
        // Post just for the super-rare case in which we might call onZoomSurfaceCreated
        // after onZoomSurfaceDestroyed (the create call is posted).
        post { releaseCurrentSurface() }
    }

    @UiThread
    private fun releaseCurrentSurface() {
        val hasSurface = surfaceTexture != null
        surfaceTexture?.release()
        program?.release()
        surface?.release()
        if (hasSurface) {
            callbacks.forEach {
                it.onZoomSurfaceDestroyed(this)
            }
        }
        surfaceTexture = null // Keep these non-null in the callback
        program = null
        surface = null

    }

    /**
     * When we have a zoom update, just request a new rendered frame.
     * This will post the request on the renderer thread and finally invoke
     * [onDrawFrame].
     */
    override fun onUpdate(engine: ZoomEngine, matrix: Matrix) {
        requestRender()
    }

    @SuppressLint("WrongCall")
    @WorkerThread
    override fun onDrawFrame(gl: GL10) {
        val texture = surfaceTexture ?: return
        val program = program ?: return
        val translX = -panX / engine.contentWidth
        val translY = panY / engine.contentHeight
        val scale = 1F / realZoom

        // Latch the latest frame. If there isn't anything new,
        // we'll just re-use whatever was there before.
        texture.updateTexImage()
        texture.getTransformMatrix(surfaceTextureTransformMatrix)
        LOG.i("onDrawFrame: scale:$scale translX:$translX translY:$translY")

        // There are some issues here due to the fact that GL will always apply translation first than scale,
        // which is not what we want. It also scales with respect to the (0,0) point (bottom-left) which is also
        // is not what we want. A good option, apparently, is to:
        // 1. translate with our pan values.
        android.opengl.Matrix.translateM(surfaceTextureTransformMatrix, 0, translX, translY, 0f)
        // 2. Scale, but with respect to the top-left point (0,1). This is achieved by translating then translating back.
        android.opengl.Matrix.translateM(surfaceTextureTransformMatrix, 0, 0F, 1f, 0f)
        android.opengl.Matrix.scaleM(surfaceTextureTransformMatrix, 0, scale, scale, 1F)
        android.opengl.Matrix.translateM(surfaceTextureTransformMatrix, 0, 0F, -1f, 0f)
        onDraw(surfaceTextureTransformMatrix)
        program.drawRect(textureId, surfaceTextureTransformMatrix)
    }

    /**
     * Called on the renderer thread when the texture is being drawn.
     * You can, if needed, perform extra transformation by editing the [transformMatrix]
     * matrix using the [android.opengl.Matrix] utilities.
     */
    @WorkerThread
    protected open fun onDraw(transformMatrix: FloatArray) {}

    override fun onIdle(engine: ZoomEngine) {}

    companion object {
        private val TAG = ZoomSurfaceView::class.java.simpleName
        private val LOG = ZoomLogger.create(TAG)
    }

}