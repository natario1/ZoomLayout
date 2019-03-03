package com.otaliastudios.zoom.opengl.core

import android.annotation.TargetApi
import android.opengl.EGL14
import android.opengl.GLSurfaceView
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay

/**
 * Helper for [GLSurfaceView.setEGLContextFactory].
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
object EglContextFactory {
    private val TAG = EglContextFactory::class.java.simpleName

    @JvmField
    val GLES2: GLSurfaceView.EGLContextFactory = Factory(2)

    @JvmField
    val GLES3: GLSurfaceView.EGLContextFactory = Factory(3)


    private class Factory(private val version: Int) : GLSurfaceView.EGLContextFactory {
        override fun createContext(egl: EGL10, display: EGLDisplay, eglConfig: EGLConfig): EGLContext {
            val attributes = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, version, EGL14.EGL_NONE)
            return egl.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, attributes)
        }

        override fun destroyContext(egl: EGL10, display: EGLDisplay, context: EGLContext) {
            if (!egl.eglDestroyContext(display, context)) {
                Log.e("EglContextFactory", "display:$display context: $context");
                throw RuntimeException("eglDestroyContex" + egl.eglGetError());
            }
        }
    }
}