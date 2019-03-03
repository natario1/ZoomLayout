package com.otaliastudios.zoom.opengl.program


import android.opengl.GLES20
import android.os.Build
import androidx.annotation.RequiresApi
import com.otaliastudios.zoom.opengl.core.Egl

/**
 * Base class for a program, can create the program and load shaders.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
internal open class EglProgram {

    // Creates a program with given vertex shader and pixel shader.
    protected fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (pixelShader == 0) throw RuntimeException("Could not load fragment shader")
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) throw RuntimeException("Could not load vertex shader")

        val program = GLES20.glCreateProgram()
        Egl.check("glCreateProgram")
        if (program == 0) {
            throw RuntimeException("Could not create program")
        }
        GLES20.glAttachShader(program, vertexShader)
        Egl.check("glAttachShader")
        GLES20.glAttachShader(program, pixelShader)
        Egl.check("glAttachShader")
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val message = "Could not link program: " + GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw RuntimeException(message)
        }
        return program
    }


    // Compiles the given shader, returns a handle.
    protected fun loadShader(shaderType: Int, source: String): Int {
        val shader = GLES20.glCreateShader(shaderType)
        Egl.check("glCreateShader type=$shaderType")
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val message = "Could not compile shader $shaderType: ${GLES20.glGetShaderInfoLog(shader)} source: $source"
            GLES20.glDeleteShader(shader)
            throw RuntimeException(message)
        }
        return shader
    }

    companion object {
        internal val TAG = EglProgram::class.java.simpleName
    }
}