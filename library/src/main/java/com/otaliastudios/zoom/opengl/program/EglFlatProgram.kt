package com.otaliastudios.zoom.opengl.program


import android.opengl.GLES20
import android.os.Build
import androidx.annotation.RequiresApi
import com.otaliastudios.zoom.opengl.core.Egl
import com.otaliastudios.zoom.opengl.draw.EglDrawable
import java.nio.FloatBuffer

/**
 * An [EglProgram] that uses basic flat-shading rendering,
 * based on FlatShadedProgram from grafika.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
open class EglFlatProgram : EglProgram() {

    companion object {
        internal val TAG = EglFlatProgram::class.java.simpleName

        private val VERTEX_SHADER =
                "uniform mat4 uMVPMatrix;\n" +
                        "attribute vec4 aPosition;\n" +
                        "void main() {\n" +
                        "    gl_Position = uMVPMatrix * aPosition;\n" +
                        "}\n"

        private val FRAGMENT_SHADER =
                "precision mediump float;\n" +
                        "uniform vec4 uColor;\n" +
                        "void main() {\n" +
                        "    gl_FragColor = uColor;\n" +
                        "}\n"
    }

    private var mProgramHandle = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
    init {
        if (mProgramHandle == 0) {
            throw RuntimeException("Could not create program.")
        }
    }

    private val maPositionLocation: Int
    private val muMVPMatrixLocation: Int
    private val muColorLocation: Int
    init {
        maPositionLocation = GLES20.glGetAttribLocation(mProgramHandle, "aPosition")
        Egl.checkLocation(maPositionLocation, "aPosition")
        muMVPMatrixLocation = GLES20.glGetUniformLocation(mProgramHandle, "uMVPMatrix")
        Egl.checkLocation(muMVPMatrixLocation, "uMVPMatrix")
        muColorLocation = GLES20.glGetUniformLocation(mProgramHandle, "uColor")
        Egl.checkLocation(muColorLocation, "uColor")
    }


    fun release(doEglCleanup: Boolean) {
        if (doEglCleanup) GLES20.glDeleteProgram(mProgramHandle)
        mProgramHandle = -1
    }

    fun release() {
        release(true)
    }

    /**
     * @param color A 4-element color vector.
     * @param mvpMatrix The 4x4 projection matrix.
     * @param vertexBuffer Buffer with vertex data.
     * @param firstVertex Index of first vertex to use in vertexBuffer.
     * @param vertexCount Number of vertices in vertexBuffer.
     * @param coordsPerVertex The number of coordinates per vertex (e.g. x,y is 2).
     * @param vertexStride Width, in bytes, of the data for each vertex (often vertexCount * sizeof(float)).
     */
    fun draw(mvpMatrix: FloatArray, color: FloatArray,
                     vertexBuffer: FloatBuffer, firstVertex: Int,
                     vertexCount: Int, vertexStride: Int, coordsPerVertex: Int) {
        Egl.check("draw start")

        // Select the program.
        GLES20.glUseProgram(mProgramHandle)
        Egl.check("glUseProgram")

        // Copy the model / view / projection matrix over.
        GLES20.glUniformMatrix4fv(muMVPMatrixLocation, 1, false, mvpMatrix, 0)
        Egl.check("glUniformMatrix4fv")

        // Copy the color vector in.
        GLES20.glUniform4fv(muColorLocation, 1, color, 0)
        Egl.check("glUniform4fv")

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(maPositionLocation)
        Egl.check("glEnableVertexAttribArray")

        // Connect vertexBuffer to "aPosition".
        GLES20.glVertexAttribPointer(maPositionLocation, coordsPerVertex,
                GLES20.GL_FLOAT, false, vertexStride, vertexBuffer)
        Egl.check("glVertexAttribPointer")

        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, firstVertex, vertexCount)
        Egl.check("glDrawArrays")

        // Done -- disable vertex array and program.
        GLES20.glDisableVertexAttribArray(maPositionLocation)
        GLES20.glUseProgram(0)
    }

}