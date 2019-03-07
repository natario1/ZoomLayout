package com.otaliastudios.zoom.opengl.core


import android.annotation.TargetApi
import android.graphics.SurfaceTexture
import android.opengl.*
import android.os.Build
import android.util.Log
import android.view.Surface
import androidx.annotation.RequiresApi


/**
 * Core EGL state (display, context, config).
 * The EGLContext must only be attached to one thread at a time.
 * This class is not thread-safe.
 *
 * @param sharedContext The context to share, or null if sharing is not desired.
 * @param flags Configuration bit flags, e.g. FLAG_RECORDABLE.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class EglCore(sharedContext: EGLContext = EGL14.EGL_NO_CONTEXT, flags: Int = 0) {

    private var eglDisplay: EGLDisplay? = EGL14.EGL_NO_DISPLAY
    private var eglContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var glVersion = -1 // 2 or 3

    init {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay === EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("unable to get EGL14 display")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            eglDisplay = null
            throw RuntimeException("unable to initialize EGL14")
        }

        // Try to get a GLES3 context, if requested.
        val recordable = flags and FLAG_RECORDABLE != 0
        val tryGles3 = flags and FLAG_TRY_GLES3 != 0
        if (tryGles3) {
            val config = EglConfigChooser.getConfig(eglDisplay!!, 3, recordable)
            if (config != null) {
                val attributes = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
                val context = EGL14.eglCreateContext(eglDisplay, config, sharedContext, attributes, 0)
                try {
                    checkEglError("eglCreateContext (3)")
                    eglConfig = config
                    eglContext = context
                    glVersion = 3
                } catch (e: Exception) {
                    // Swallow, will try GLES2
                }
            }
        }

        // If GLES3 failed, go with GLES2.
        val tryGles2 = eglContext === EGL14.EGL_NO_CONTEXT
        if (tryGles2) {
            val config = EglConfigChooser.getConfig(eglDisplay!!, 2, recordable)
            if (config != null) {
                val attributes = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
                val context = EGL14.eglCreateContext(eglDisplay, config, sharedContext, attributes, 0)
                checkEglError("eglCreateContext (2)")
                eglConfig = config
                eglContext = context
                glVersion = 2
            } else {
                throw RuntimeException("Unable to find a suitable EGLConfig")
            }
        }
    }


    /**
     * Discards all resources held by this class, notably the EGL context.  This must be
     * called from the thread where the context was created.
     *
     * On completion, no context will be current.
     */
    fun release() {
        if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
            // Android is unusual in that it uses a reference-counted EGLDisplay.  So for
            // every eglInitialize() we need an eglTerminate().
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }

        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglConfig = null
    }

    // Kotlin has no finalize, but simply declaring it works,
    // as stated in official documentation.
    protected fun finalize() {
        if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
            // We're limited here -- finalizers don't run on the thread that holds
            // the EGL state, so if a surface or context is still current on another
            // thread we can't fully release it here.  Exceptions thrown from here
            // are quietly discarded.  Complain in the log file.
            Log.e(TAG, "WARNING: EglCore was not explicitly released - state may be leaked")
            release()
        }
    }

    /**
     * Destroys the specified surface.  Note the EGLSurface won't actually be destroyed if it's
     * still current in a context.
     */
    fun releaseSurface(eglSurface: EGLSurface) {
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
    }

    /**
     * Creates an EGL surface associated with a Surface.
     * If this is destined for MediaCodec, the EGLConfig should have the "recordable" attribute.
     */
    fun createWindowSurface(surface: Any): EGLSurface {
        if (surface !is Surface && surface !is SurfaceTexture) {
            throw RuntimeException("invalid surface: $surface")
        }

        // Create a window surface, and attach it to the Surface we received.
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface,
                surfaceAttribs, 0)
        checkEglError("eglCreateWindowSurface")
        if (eglSurface == null) throw RuntimeException("surface was null")
        return eglSurface
    }

    /**
     * Creates an EGL surface associated with an offscreen buffer.
     */
    fun createOffscreenSurface(width: Int, height: Int): EGLSurface {
        val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig,
                surfaceAttribs, 0)
        checkEglError("eglCreatePbufferSurface")
        if (eglSurface == null) {
            throw RuntimeException("surface was null")
        }
        return eglSurface
    }

    /**
     * Makes our EGL context current, using the supplied surface for both "draw" and "read".
     */
    fun makeCurrent(eglSurface: EGLSurface) {
        if (eglDisplay === EGL14.EGL_NO_DISPLAY) Log.d(TAG, "NOTE: makeCurrent w/o display")
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    /**
     * Makes our EGL context current, using the supplied "draw" and "read" surfaces.
     */
    fun makeCurrent(drawSurface: EGLSurface, readSurface: EGLSurface) {
        if (eglDisplay === EGL14.EGL_NO_DISPLAY) Log.d(TAG, "NOTE: makeCurrent w/o display")
        if (!EGL14.eglMakeCurrent(eglDisplay, drawSurface, readSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent(draw,read) failed")
        }
    }

    /**
     * Makes no context current.
     */
    fun makeNothingCurrent() {
        if (!EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    /**
     * Calls eglSwapBuffers.  Use this to "publish" the current frame.
     *
     * @return false on failure
     */
    fun swapBuffers(eglSurface: EGLSurface): Boolean {
        return EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    /**
     * Sends the presentation time stamp to EGL.  Time is expressed in nanoseconds.
     */
    fun setPresentationTime(eglSurface: EGLSurface, nsecs: Long) {
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs)
    }

    /**
     * Returns true if our context and the specified surface are current.
     */
    fun isCurrent(eglSurface: EGLSurface): Boolean {
        return eglContext == EGL14.eglGetCurrentContext() && eglSurface == EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
    }

    /**
     * Performs a simple surface query.
     */
    fun querySurface(eglSurface: EGLSurface, what: Int): Int {
        val value = IntArray(1)
        EGL14.eglQuerySurface(eglDisplay, eglSurface, what, value, 0)
        return value[0]
    }

    /**
     * Queries a string value.
     */
    fun queryString(what: Int): String {
        return EGL14.eglQueryString(eglDisplay, what)
    }

    /**
     * Checks for EGL errors.  Throws an exception if an error has been raised.
     */
    private fun checkEglError(msg: String) {
        val error = EGL14.eglGetError()
        if (error != EGL14.EGL_SUCCESS) {
            throw RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error))
        }
    }

    companion object {
        private val TAG = EglCore::class.java.simpleName

        /**
         * Constructor flag: surface must be recordable.  This discourages EGL from using a
         * pixel format that cannot be converted efficiently to something usable by the video
         * encoder.
         */
        val FLAG_RECORDABLE = 0x01

        /**
         * Constructor flag: ask for GLES3, fall back to GLES2 if not available.  Without this
         * flag, GLES2 is used.
         */
        val FLAG_TRY_GLES3 = 0x02

        /**
         * Writes the current display, context, and surface to the log.
         */
        fun logCurrent(msg: String) {
            val display = EGL14.eglGetCurrentDisplay()
            val context = EGL14.eglGetCurrentContext()
            val surface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
            Log.i(TAG, "Current EGL ($msg): display=$display, context=$context, surface=$surface)")
        }
    }
}