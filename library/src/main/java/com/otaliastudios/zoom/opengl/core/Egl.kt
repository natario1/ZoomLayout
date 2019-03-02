package com.otaliastudios.zoom.opengl.core


import android.opengl.GLES20
import android.opengl.GLU
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

internal object Egl {

    // Identity matrix for general use.
    val IDENTITY_MATRIX = FloatArray(16)
    init {
        Matrix.setIdentityM(IDENTITY_MATRIX, 0)
    }

    // Allocates a direct float buffer, and populates it with the float array data.
    // Allocate a direct ByteBuffer, using 4 bytes per float, and copy coords into it.
    fun floatBuffer(coords: FloatArray): FloatBuffer {
        val bb = ByteBuffer.allocateDirect(coords.size * 4)
        bb.order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        fb.put(coords)
        fb.position(0)
        return fb
    }

    fun check(opName: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            val message = "Error during $opName: glError 0x ${Integer.toHexString(error)}"
            Log.e("Egl", message + " " + GLU.gluErrorString(error))
            throw RuntimeException(message)
        }
    }

    // Check for valid location.
    fun checkLocation(location: Int, label: String) {
        if (location < 0) {
            val message = "Unable to locate $label in program"
            Log.e("Egl", message)
            throw RuntimeException(message)
        }
    }
}