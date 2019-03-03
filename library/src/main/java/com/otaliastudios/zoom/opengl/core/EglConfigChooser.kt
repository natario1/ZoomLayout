package com.otaliastudios.zoom.opengl.core

import android.annotation.TargetApi
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLSurfaceView
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay


/**
 * Helper for [GLSurfaceView.setEGLConfigChooser], plus
 * some handy methods for configs.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
object EglConfigChooser {
    private val TAG = EglConfigChooser::class.java.simpleName
    private const val EGL_RECORDABLE_ANDROID = 0x3142 // Android-specific extension.

    @JvmField
    val GLES2: GLSurfaceView.EGLConfigChooser = Chooser(2)

    @JvmField
    val GLES3: GLSurfaceView.EGLConfigChooser = Chooser(3)

    /**
     * Finds a suitable EGLConfig by querying [EGL14].
     */
    fun getConfig(display: android.opengl.EGLDisplay, version: Int, recordable: Boolean): android.opengl.EGLConfig? {
        val attributes = getConfigSpec(version, recordable)
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(display,
                        attributes, 0,
                        configs, 0, configs.size,
                        numConfigs, 0)) {
            Log.w(TAG, "Unable to find RGB8888 / $version EGLConfig")
            return null
        }
        return configs[0]
    }

    /**
     * Finds a suitable EGLConfig with r=8, g=8, b=8, a=8.
     * Does not specify depth or stencil size.
     *
     * The actual drawing surface is generally RGBA or RGBX, so omitting the alpha doesn't
     * really help - it can also lead to huge performance hit on glReadPixels() when reading
     * into a GL_RGBA buffer.
     */
    private fun getConfigSpec(version: Int, recordable: Boolean): IntArray {
        val renderableType = if (version >= 3) {
            EGL14.EGL_OPENGL_ES2_BIT or EGLExt.EGL_OPENGL_ES3_BIT_KHR
        } else {
            EGL14.EGL_OPENGL_ES2_BIT
        }
        val attributes = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, renderableType,
                EGL14.EGL_NONE, 0, // placeholder for recordable [@-3]
                EGL14.EGL_NONE)
        if (recordable) {
            attributes[attributes.size - 3] = EGL_RECORDABLE_ANDROID
            attributes[attributes.size - 2] = 1
        }
        return attributes
    }

    private class Chooser(private val version: Int) : GLSurfaceView.EGLConfigChooser {

        // https://github.com/MasayukiSuda/ExoPlayerFilter/blob/master/epf/src/main/java/com/daasuu/epf/chooser/EConfigChooser.java
        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            val configSizeArray = IntArray(1)
            val configSpec = getConfigSpec(version, true)
            if (!egl.eglChooseConfig(display, configSpec, null, 0, configSizeArray)) {
                throw IllegalArgumentException("eglChooseConfig failed")
            }
            val configSize = configSizeArray[0]
            if (configSize <= 0) throw IllegalArgumentException("No configs match configSpec")

            val configs = arrayOfNulls<EGLConfig>(configSize)
            if (!egl.eglChooseConfig(display, configSpec, configs, configSize, configSizeArray)) {
                throw IllegalArgumentException("eglChooseConfig#2 failed")
            }
            return chooseConfig(egl, display, configs.filterNotNull().toTypedArray())
                    ?: throw IllegalArgumentException("No config chosen")
        }


        // https://github.com/MasayukiSuda/ExoPlayerFilter/blob/master/epf/src/main/java/com/daasuu/epf/chooser/EConfigChooser.java
        private fun chooseConfig(egl: EGL10, display: EGLDisplay, configs: Array<EGLConfig>): EGLConfig? {
            for (config in configs) {
                val d = egl.findConfigAttrib(display, config, EGL10.EGL_DEPTH_SIZE, 0)
                val s = egl.findConfigAttrib(display, config, EGL10.EGL_STENCIL_SIZE, 0)
                if (d >= 0 && s >= 0) {
                    val r = egl.findConfigAttrib(display, config, EGL10.EGL_RED_SIZE, 0)
                    val g = egl.findConfigAttrib(display, config, EGL10.EGL_GREEN_SIZE, 0)
                    val b = egl.findConfigAttrib(display, config, EGL10.EGL_BLUE_SIZE, 0)
                    val a = egl.findConfigAttrib(display, config, EGL10.EGL_ALPHA_SIZE, 0)
                    if (r == 8 && g == 8 && b == 8 && a == 8) {
                        return config
                    }
                }
            }
            return null
        }

        private fun EGL10.findConfigAttrib(display: EGLDisplay, config: EGLConfig, attribute: Int, defaultValue: Int): Int {
            val value = IntArray(1)
            return if (eglGetConfigAttrib(display, config, attribute, value)) {
                value[0]
            } else defaultValue
        }
    }
}