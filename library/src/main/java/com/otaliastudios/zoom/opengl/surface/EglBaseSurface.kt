package com.otaliastudios.zoom.opengl.surface


import android.annotation.TargetApi
import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.GLES20
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.otaliastudios.zoom.opengl.core.Egl
import com.otaliastudios.zoom.opengl.core.EglCore
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Common base class for EGL surfaces.
 * There can be multiple base surfaces associated with a single [EglCore] object.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
internal open class EglBaseSurface(protected var mEglCore: EglCore) {

    private var mEGLSurface = EGL14.EGL_NO_SURFACE
    private var mWidth = -1
    private var mHeight = -1

    /**
     * Returns the surface's width, in pixels.
     *
     * If this is called on a window surface, and the underlying surface is in the process
     * of changing size, we may not see the new size right away (e.g. in the "surfaceChanged"
     * callback).  The size should match after the next buffer swap.
     */
    fun getWidth(): Int {
        return if (mWidth < 0) {
            mEglCore.querySurface(mEGLSurface, EGL14.EGL_WIDTH)
        } else {
            mWidth
        }
    }

    /**
     * Returns the surface's height, in pixels.
     */
    fun getHeight(): Int {
        return if (mHeight < 0) {
            mEglCore.querySurface(mEGLSurface, EGL14.EGL_HEIGHT)
        } else {
            mHeight
        }
    }

    /**
     * Creates a window surface.
     * The [surface] param may be a Surface or SurfaceTexture.
     */
    fun createWindowSurface(surface: Any) {
        if (mEGLSurface !== EGL14.EGL_NO_SURFACE) {
            throw IllegalStateException("surface already created")
        }
        mEGLSurface = mEglCore.createWindowSurface(surface)
        // Don't cache width/height here, because the size of the underlying surface can change
        // out from under us (see e.g. HardwareScalerActivity).
        // mWidth = mEglCore.querySurface(mEGLSurface, EGL14.EGL_WIDTH);
        // mHeight = mEglCore.querySurface(mEGLSurface, EGL14.EGL_HEIGHT);
    }

    /**
     * Creates an off-screen surface.
     */
    fun createOffscreenSurface(width: Int, height: Int) {
        if (mEGLSurface !== EGL14.EGL_NO_SURFACE) {
            throw IllegalStateException("surface already created")
        }
        mEGLSurface = mEglCore.createOffscreenSurface(width, height)
        mWidth = width
        mHeight = height
    }

    /**
     * Release the EGL surface.
     */
    fun releaseEglSurface() {
        mEglCore.releaseSurface(mEGLSurface)
        mEGLSurface = EGL14.EGL_NO_SURFACE
        mHeight = -1
        mWidth = mHeight
    }

    /**
     * Makes our EGL context and surface current.
     */
    fun makeCurrent() {
        mEglCore.makeCurrent(mEGLSurface)
    }

    /**
     * Makes our EGL context and surface current for drawing,
     * using the supplied surface for reading.
     */
    fun makeCurrentReadFrom(readSurface: EglBaseSurface) {
        mEglCore.makeCurrent(mEGLSurface, readSurface.mEGLSurface)
    }

    /**
     * Calls eglSwapBuffers.  Use this to "publish" the current frame.
     * Returns false on failure.
     */
    fun swapBuffers(): Boolean {
        val result = mEglCore.swapBuffers(mEGLSurface)
        if (!result) {
            Log.d(TAG, "WARNING: swapBuffers() failed")
        }
        return result
    }

    /**
     * Sends the presentation time stamp to EGL.
     * [nsecs] is the timestamp in nanoseconds.
     */
    fun setPresentationTime(nsecs: Long) {
        mEglCore.setPresentationTime(mEGLSurface, nsecs)
    }

    /**
     * Saves the EGL surface to a file.
     * Expects that this object's EGL surface is current.
     */
    @Throws(IOException::class)
    fun saveFrameToFile(file: File) {
        if (!mEglCore.isCurrent(mEGLSurface)) {
            throw RuntimeException("Expected EGL context/surface is not current")
        }

        // glReadPixels fills in a "direct" ByteBuffer with what is essentially big-endian RGBA
        // data (i.e. a byte of red, followed by a byte of green...).  While the Bitmap
        // constructor that takes an int[] wants little-endian ARGB (blue/red swapped), the
        // Bitmap "copy pixels" method wants the same format GL provides.
        //
        // Ideally we'd have some way to re-use the ByteBuffer, especially if we're calling
        // here often.
        //
        // Making this even more interesting is the upside-down nature of GL, which means
        // our output will look upside down relative to what appears on screen if the
        // typical GL conventions are used.
        val filename = file.toString()
        val width = getWidth()
        val height = getHeight()
        val buf = ByteBuffer.allocateDirect(width * height * 4)
        buf.order(ByteOrder.LITTLE_ENDIAN)
        GLES20.glReadPixels(0, 0, width, height,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        Egl.check("glReadPixels")
        buf.rewind()
        var bos: BufferedOutputStream? = null
        try {
            bos = BufferedOutputStream(FileOutputStream(filename))
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(buf)
            bmp.compress(Bitmap.CompressFormat.PNG, 90, bos)
            bmp.recycle()
        } finally {
            bos?.close()
        }
    }

    /**
     * Saves the EGL surface to given format.
     * Expects that this object's EGL surface is current.
     */
    fun saveFrameTo(compressFormat: Bitmap.CompressFormat): ByteArray {
        if (!mEglCore.isCurrent(mEGLSurface)) {
            throw RuntimeException("Expected EGL context/surface is not current")
        }

        // glReadPixels fills in a "direct" ByteBuffer with what is essentially big-endian RGBA
        // data (i.e. a byte of red, followed by a byte of green...).  While the Bitmap
        // constructor that takes an int[] wants little-endian ARGB (blue/red swapped), the
        // Bitmap "copy pixels" method wants the same format GL provides.
        //
        // Ideally we'd have some way to re-use the ByteBuffer, especially if we're calling
        // here often.
        //
        // Making this even more interesting is the upside-down nature of GL, which means
        // our output will look upside down relative to what appears on screen if the
        // typical GL conventions are used.

        val width = getWidth()
        val height = getHeight()
        val buf = ByteBuffer.allocateDirect(width * height * 4)
        buf.order(ByteOrder.LITTLE_ENDIAN)
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        Egl.check("glReadPixels")
        buf.rewind()

        val bos = ByteArrayOutputStream(buf.array().size)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(buf)
        bmp.compress(compressFormat, 90, bos)
        bmp.recycle()
        return bos.toByteArray()
    }

    companion object {
        protected val TAG = EglBaseSurface::class.java.simpleName
    }
}