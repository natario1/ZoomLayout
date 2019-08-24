package com.otaliastudios.zoom

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.opengl.GLSurfaceView
import android.os.Build
import android.util.AttributeSet
import android.view.*
import androidx.annotation.RequiresApi
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import com.otaliastudios.zoom.ZoomApi.ZoomType
import com.otaliastudios.opengl.core.EglConfigChooser
import com.otaliastudios.opengl.core.EglContextFactory
import com.otaliastudios.opengl.draw.GlRect
import com.otaliastudios.opengl.extensions.clear
import com.otaliastudios.opengl.extensions.scale
import com.otaliastudios.opengl.extensions.translate
import com.otaliastudios.opengl.program.GlFlatProgram
import com.otaliastudios.opengl.program.GlTextureProgram
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10


/**
 * Uses [ZoomEngine] to allow zooming and pan events onto a GL rendered surface.
 */
@Suppress("LeakingThis")
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
open class ZoomSurfaceView private constructor(
        context: Context,
        attrs: AttributeSet?,
        @Suppress("MemberVisibilityCanBePrivate") val engine: ZoomEngine = ZoomEngine(context)
) : GLSurfaceView(context, attrs),
        ZoomApi by engine,
        GLSurfaceView.Renderer {

    init {
        // See if the OpenGL dependency was added.
        val hasEgloo = runCatching { GlRect() }
        if (hasEgloo.isFailure) {
            val hasEglCore = runCatching {
                Class.forName("com.otaliastudios.opengl.draw.EglRect").newInstance()
            }
            if (hasEglCore.isSuccess) {
                throw RuntimeException("Starting from ZoomLayout v1.7.0, you should replace " +
                        "com.otaliastudios.opengl:egl-core with com.otaliastudios.opengl:egloo. " +
                        "Check documentation for version.")
            } else {
                throw RuntimeException("To use ZoomSurfaceView, you have to add " +
                        "com.otaliastudios.opengl:egloo to your dependencies. " +
                        "Check documentation for version.")
            }
        }
    }

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null)
            : this(context, attrs, ZoomEngine(context))

    private val callbacks = mutableListOf<Callback>()

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
    @Suppress("MemberVisibilityCanBePrivate")
    var surfaceTexture: SurfaceTexture? = null
        private set

    private val glTextureRect = GlRect()
    private val glFlatRect = GlRect()
    private var glTextureProgram: GlTextureProgram? = null
    private var glFlatProgram: GlFlatProgram? = null

    private var backgroundColor: Int = Color.rgb(25, 25, 25)
    private var drawsBackgroundColor = false
    private var hasExplicitContentSize = false

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
    @Suppress("unused")
    fun removeCallback(callback: Callback) {
        callbacks.remove(callback)
    }

    /**
     * If non transparent, we will draw this background color using GL before drawing
     * the actual texture.
     */
    override fun setBackgroundColor(color: Int) {
        drawsBackgroundColor = Color.alpha(color) > 0
        backgroundColor = color
        if (glFlatProgram != null) {
            glFlatProgram!!.setColor(color)
        }
    }

    /**
     * Sets the size of the content. If this is not called, the size will be assumed to be
     * that of the [ZoomSurfaceView] itself. In this case, make sure to measure [ZoomSurfaceView]
     * correctly or the texture will be distorted.
     */
    fun setContentSize(width: Float, height: Float) {
        hasExplicitContentSize = true
        if (engine.contentWidth != width || engine.contentHeight != height) {
            engine.setContentSize(width, height, true)
            onContentOrContainerSizeChanged()
        }
    }

    init {
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.ZoomEngine, 0, 0)
        val overScrollHorizontal = a.getBoolean(R.styleable.ZoomEngine_overScrollHorizontal, false)
        val overScrollVertical = a.getBoolean(R.styleable.ZoomEngine_overScrollVertical, false)
        val horizontalPanEnabled = a.getBoolean(R.styleable.ZoomEngine_horizontalPanEnabled, true)
        val verticalPanEnabled = a.getBoolean(R.styleable.ZoomEngine_verticalPanEnabled, true)
        val overPinchable = a.getBoolean(R.styleable.ZoomEngine_overPinchable, false)
        val zoomEnabled = a.getBoolean(R.styleable.ZoomEngine_zoomEnabled, true)
        val flingEnabled = a.getBoolean(R.styleable.ZoomEngine_flingEnabled, true)
        val scrollEnabled = a.getBoolean(R.styleable.ZoomEngine_scrollEnabled, true)
        val oneFingerScrollEnabled = a.getBoolean(R.styleable.ZoomEngine_oneFingerScrollEnabled, true)
        val twoFingersScrollEnabled = a.getBoolean(R.styleable.ZoomEngine_twoFingersScrollEnabled, true)
        val threeFingersScrollEnabled = a.getBoolean(R.styleable.ZoomEngine_threeFingersScrollEnabled, true)
        val allowFlingInOverscroll = a.getBoolean(R.styleable.ZoomEngine_allowFlingInOverscroll, true)
        val minZoom = a.getFloat(R.styleable.ZoomEngine_minZoom, 1F)
        val maxZoom = a.getFloat(R.styleable.ZoomEngine_maxZoom, ZoomApi.MAX_ZOOM_DEFAULT)
        @ZoomType val minZoomMode = a.getInteger(R.styleable.ZoomEngine_minZoomType, ZoomApi.TYPE_ZOOM)
        @ZoomType val maxZoomMode = a.getInteger(R.styleable.ZoomEngine_maxZoomType, ZoomApi.MAX_ZOOM_DEFAULT_TYPE)
        val transformation = a.getInteger(R.styleable.ZoomEngine_transformation, ZoomApi.TRANSFORMATION_CENTER_INSIDE)
        val transformationGravity = a.getInt(R.styleable.ZoomEngine_transformationGravity, ZoomApi.TRANSFORMATION_GRAVITY_AUTO)
        val alignment = a.getInt(R.styleable.ZoomEngine_alignment, ZoomApi.ALIGNMENT_DEFAULT)
        val animationDuration = a.getInt(R.styleable.ZoomEngine_animationDuration, ZoomEngine.DEFAULT_ANIMATION_DURATION.toInt()).toLong()
        a.recycle()

        engine.setContainer(this)
        engine.addListener(object: ZoomEngine.Listener {
            // When we have a zoom update, just request a new rendered frame.
            // This will post the request on the renderer thread and finally invoke [onDrawFrame].
            override fun onUpdate(engine: ZoomEngine, matrix: Matrix) { requestRender() }
            override fun onIdle(engine: ZoomEngine) {}
        })

        setOverScrollHorizontal(overScrollHorizontal)
        setOverScrollVertical(overScrollVertical)
        setTransformation(transformation, transformationGravity)
        setAlignment(alignment)
        setHorizontalPanEnabled(horizontalPanEnabled)
        setVerticalPanEnabled(verticalPanEnabled)
        setOverPinchable(overPinchable)
        setZoomEnabled(zoomEnabled)
        setFlingEnabled(flingEnabled)
        setScrollEnabled(scrollEnabled)
        setOneFingerScrollEnabled(oneFingerScrollEnabled)
        setTwoFingersScrollEnabled(twoFingersScrollEnabled)
        setThreeFingersScrollEnabled(threeFingersScrollEnabled)
        setAllowFlingInOverscroll(allowFlingInOverscroll)
        setAnimationDuration(animationDuration)
        setMinZoom(minZoom, minZoomMode)
        setMaxZoom(maxZoom, maxZoomMode)

        setEGLContextFactory(EglContextFactory.GLES2)
        setEGLConfigChooser(EglConfigChooser.GLES2)
        setRenderer(this)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // Using | so click listeners work.
        return engine.onTouchEvent(ev) or super.onTouchEvent(ev)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // Strictly speaking setContainerSize is not needed, because the engine gets that onGlobalLayout (after),
        // however it's better to do this again so we can use applyTransformation=true and make sure that we
        // react well to container size changes. We also don't want to stay in any weird state for some frames
        // before the engine gets that GlobalLayout call (possible since rendering happens on the Renderer thread).
        val width = measuredWidth.toFloat()
        val height = measuredHeight.toFloat()
        val changed = engine.containerWidth != width || engine.containerHeight != height
        if (changed) engine.setContainerSize(width, height, true)
        if (!hasExplicitContentSize) {
            val contentChanged = engine.contentWidth != width || engine.contentHeight != height
            if (contentChanged) engine.setContentSize(width, height, true)
        }
        if (changed) {
            onContentOrContainerSizeChanged()
        }
    }

    /**
     * This is very important.
     * Basically, in this class we call engine.setContentSize() with a user defined size.
     * But for the engine updates to make sense, we must UNDO here two things that SurfaceView does by default:
     * - scaling the content so that it center-fits the container
     * - drawing with respect to the center
     * They are very useful usually, but for us, this means applying some modifications BEFORE the
     * engine and this invalidates the meaning of the engine updates.
     */
    private fun onContentOrContainerSizeChanged() {
        val fullSize = EGLOO_DRAWABLE_FULL_SIZE // Full size of GlRect()
        val width = fullSize * engine.contentWidth / engine.containerWidth
        val height = fullSize * engine.contentHeight / engine.containerHeight
        val rect = RectF(
                EGLOO_DRAWABLE_TOPLEFT_X,
                EGLOO_DRAWABLE_TOPLEFT_Y,
                EGLOO_DRAWABLE_TOPLEFT_X + width,
                EGLOO_DRAWABLE_TOPLEFT_Y - height // y is opposite in GL
        )
        glTextureRect.setVertexArray(rect)
    }

    @SuppressLint("Recycle")
    @WorkerThread
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        LOG.i("onSurfaceCreated")
        glFlatProgram = GlFlatProgram()
        glFlatProgram!!.setColor(backgroundColor)
        glTextureProgram = GlTextureProgram()
        surfaceTexture = SurfaceTexture(glTextureProgram!!.textureId).also {
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
        glTextureProgram?.release()
        glFlatProgram?.release()
        surface?.release()
        if (hasSurface) {
            callbacks.forEach {
                it.onZoomSurfaceDestroyed(this)
            }
        }
        surfaceTexture = null // Keep these non-null in the callback
        glTextureProgram = null
        glFlatProgram = null
        surface = null

    }

    /**
     * Performs the texture and background drawing.
     *
     * An old version of this acted on surfaceTextureTransformMatrix instead of the rect modelMatrix.
     * This has the drawback that any "background" (overscrolls, zoom < 1, ...) will draw the CLAMP_TO_BORDER
     * texture mess and there's no way around that.
     *
     * The current version acts on textureRect.modelMatrix instead.
     * In both cases there are some issues here.
     *
     * 1. We passed the video size to the engine. So we must UNDO some default behavior of SurfaceView.
     *   This is done and documented in [onContentOrContainerSizeChanged].
     *
     * 2. OpenGL will apply translation first then scale, which is typically not what we want.
     *
     * 3. OpenGL will scale with respect to some fixed point, which is typically not the pan origin.
     *    (if we work on modelMatrix, this point is the translated viewport center)
     *
     * 4. OpenGL Y coordinates are reversed.
     */
    @SuppressLint("WrongCall")
    @WorkerThread
    override fun onDrawFrame(gl: GL10) {
        val texture = surfaceTexture ?: return
        val textureProgram = glTextureProgram ?: return
        val flatProgram = glFlatProgram ?: return

        // Latch the latest frame. If there isn't anything new, we'll just re-use whatever was there before.
        texture.updateTexImage()
        texture.getTransformMatrix(textureProgram.textureTransform)
        LOG.i("onDrawFrame: zoom:${engine.realZoom} panX:${engine.panX} panY:${engine.panY}")

        // The textureRect has size 2x2 (goes from -1 to 1 in both dimensions).
        // It applies translation first then scale, scaling WRT the translated container center.
        val textureWidth = EGLOO_DRAWABLE_FULL_SIZE * engine.contentWidth / engine.containerWidth
        val textureHeight = EGLOO_DRAWABLE_FULL_SIZE * engine.contentHeight / engine.containerHeight
        val translX = textureWidth * (panX / engine.contentWidth)
        val translY = -textureHeight * (panY / engine.contentHeight)
        val scaleX = realZoom
        val scaleY = realZoom
        LOG.i("onDrawFrame: translX:$translX translY:$translY scaleX:$scaleX scaleY:$scaleY")

        // Apply the transformations
        glTextureRect.modelMatrix.apply {
            clear()
            // 1. translate with our pan values.
            translate(x = translX, y = translY)
            // 2. Scale, but with respect to the top-left point (0,1). This is achieved by translating then translating back.
            // In this case we also have to account for translation - we want to scale WRT to the TEXTURE point, not the container point.
            translate(x = EGLOO_DRAWABLE_TOPLEFT_X - translX, y = EGLOO_DRAWABLE_TOPLEFT_Y - translY)
            scale(x = scaleX, y = scaleY)
            translate(x = -EGLOO_DRAWABLE_TOPLEFT_X + translX, y = -EGLOO_DRAWABLE_TOPLEFT_Y + translY)
        }

        // Perform actual drawing. If set, draw a full screen color as background to avoid the SV full black.
        onDraw(glTextureRect.modelMatrix, textureProgram.textureTransform)
        if (drawsBackgroundColor) {
            flatProgram.draw(glFlatRect)
        } else {
            // Need to clear the buffer in case we were underpinching or overscrolling or any other
            // condition that shows some black background.
            gl.glClear(GL10.GL_COLOR_BUFFER_BIT)
        }
        textureProgram.draw(glTextureRect)
    }

    /**
     * Called on the renderer thread when the texture is being drawn.
     * You can, if needed, perform extra transformation by editing the [textureTransformMatrix]
     * or the [modelMatrix] using the [android.opengl.Matrix] utilities.
     */
    @WorkerThread
    protected open fun onDraw(modelMatrix: FloatArray, textureTransformMatrix: FloatArray) {}

    companion object {
        private val TAG = ZoomSurfaceView::class.java.simpleName
        private val LOG = ZoomLogger.create(TAG)

        private const val EGLOO_DRAWABLE_FULL_SIZE = 2
        private const val EGLOO_DRAWABLE_TOPLEFT_X = -1F
        private const val EGLOO_DRAWABLE_TOPLEFT_Y = 1F
    }

}