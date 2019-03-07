package com.otaliastudios.zoom.opengl.surface


import android.annotation.TargetApi
import android.graphics.SurfaceTexture
import android.os.Build
import android.view.Surface
import androidx.annotation.RequiresApi
import com.otaliastudios.zoom.opengl.core.EglCore


/**
 * Recordable EGL window surface.
 * It's good practice to explicitly release() the surface, preferably from a "finally" block.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class EglWindowSurface : EglBaseSurface {
    private var mSurface: Surface? = null
    private var mReleaseSurface = false

    /**
     * Associates an EGL surface with the native window surface.
     *
     * Set releaseSurface to true if you want the Surface to be released when release() is
     * called.  This is convenient, but can interfere with framework classes that expect to
     * manage the Surface themselves (e.g. if you release a SurfaceView's Surface, the
     * surfaceDestroyed() callback won't fire).
     */
    constructor(eglCore: EglCore, surface: Surface, releaseSurface: Boolean) : super(eglCore) {
        createWindowSurface(surface)
        mSurface = surface
        mReleaseSurface = releaseSurface
    }

    /**
     * Associates an EGL surface with the SurfaceTexture.
     */
    constructor(eglCore: EglCore, surfaceTexture: SurfaceTexture) : super(eglCore) {
        createWindowSurface(surfaceTexture)
    }

    /**
     * Associates an EGL surface with the Surface.
     */
    constructor(eglCore: EglCore, surface: Surface) : super(eglCore) {
        createWindowSurface(surface)
    }

    /**
     * Releases any resources associated with the EGL surface (and, if configured to do so,
     * with the Surface as well).
     * Does not require that the surface's EGL context be current.
     */
    fun release() {
        releaseEglSurface()
        if (mSurface != null) {
            if (mReleaseSurface) {
                mSurface!!.release()
            }
            mSurface = null
        }
    }

    /**
     * Recreate the EGLSurface, using the new EglBase.  The caller should have already
     * freed the old EGLSurface with releaseEglSurface().
     *
     * This is useful when we want to update the EGLSurface associated with a Surface.
     * For example, if we want to share with a different EGLContext, which can only
     * be done by tearing down and recreating the context.  (That's handled by the caller;
     * this just creates a new EGLSurface for the Surface we were handed earlier.)
     *
     *
     * If the previous EGLSurface isn't fully destroyed, e.g. it's still current on a
     * context somewhere, the create call will fail with complaints from the Surface
     * about already being connected.
     */
    fun recreate(newEglCore: EglCore) {
        if (mSurface == null) {
            throw RuntimeException("not implemented for SurfaceTexture")
        }
        mEglCore = newEglCore          // switch to new context
        createWindowSurface(mSurface!!)  // create new surface
    }
}
