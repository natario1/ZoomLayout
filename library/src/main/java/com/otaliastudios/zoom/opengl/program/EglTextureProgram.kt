package com.otaliastudios.zoom.opengl.program


import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Build
import androidx.annotation.RequiresApi
import com.otaliastudios.zoom.opengl.core.Egl
import com.otaliastudios.zoom.opengl.core.EglOptions
import com.otaliastudios.zoom.opengl.draw.EglDrawable
import java.nio.FloatBuffer

/**
 * An [EglProgram] that uses a simple vertex shader and a texture fragment shader.
 * This means that the fragment shader draws texture2D elements.
 *
 * Internally this uses [GLES20.glBindTexture] and [GLES11Ext.GL_TEXTURE_EXTERNAL_OES].
 * The texture ID is passed outside so the callers can draw on that texture ID and then
 * call draw() here.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
open class EglTextureProgram : EglProgram() {

    companion object {
        internal val TAG = EglTextureProgram::class.java.simpleName

        // Simple vertex shader.
        private val SIMPLE_VERTEX_SHADER =
                "uniform mat4 uMVPMatrix;\n" +
                        "uniform mat4 uTexMatrix;\n" +
                        "attribute vec4 aPosition;\n" +
                        "attribute vec4 aTextureCoord;\n" +
                        "varying vec2 vTextureCoord;\n" +
                        "void main() {\n" +
                        "    gl_Position = uMVPMatrix * aPosition;\n" +
                        "    vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n" +
                        "}\n"

        // Simple fragment shader for use with external 2D textures
        private val SIMPLE_FRAGMENT_SHADER =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;\n" +
                        "varying vec2 vTextureCoord;\n" +
                        "uniform samplerExternalOES sTexture;\n" +
                        "void main() {\n" +
                        "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                        "}\n"
    }

    // Stuff from Texture2dProgram
    private val mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
    private var mProgramHandle = createProgram(SIMPLE_VERTEX_SHADER, SIMPLE_FRAGMENT_SHADER)
    init {
        if (mProgramHandle == 0) {
            throw RuntimeException("Could not create program.")
        }
    }

    private val maPositionLocation: Int
    private val maTextureCoordLocation: Int
    private val muMVPMatrixLocation: Int
    private val muTexMatrixLocation: Int
    init {
        maPositionLocation = GLES20.glGetAttribLocation(mProgramHandle, "aPosition")
        Egl.checkLocation(maPositionLocation, "aPosition")
        maTextureCoordLocation = GLES20.glGetAttribLocation(mProgramHandle, "aTextureCoord")
        Egl.checkLocation(maTextureCoordLocation, "aTextureCoord")
        muMVPMatrixLocation = GLES20.glGetUniformLocation(mProgramHandle, "uMVPMatrix")
        Egl.checkLocation(muMVPMatrixLocation, "uMVPMatrix")
        muTexMatrixLocation = GLES20.glGetUniformLocation(mProgramHandle, "uTexMatrix")
        Egl.checkLocation(muTexMatrixLocation, "uTexMatrix")
    }


    fun release(doEglCleanup: Boolean) {
        if (doEglCleanup) GLES20.glDeleteProgram(mProgramHandle)
        mProgramHandle = -1
    }

    fun release() {
        release(true)
    }

    fun createTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        Egl.check("glGenTextures")

        val texId = textures[0]
        GLES20.glBindTexture(mTextureTarget, texId)
        Egl.check("glBindTexture $texId")

        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, EglOptions.glTextureWrapS)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, EglOptions.glTextureWrapT)
        Egl.check("glTexParameter")

        return texId
    }

    fun draw(mvpMatrix: FloatArray, textureId: Int, textureMatrix: FloatArray,
             vertexBuffer: FloatBuffer, firstVertex: Int, vertexCount: Int, vertexStride: Int,
             coordsPerVertex: Int,
             texCoordBuffer: FloatBuffer, texCoordStride: Int) {
        Egl.check("draw start")

        // Select the program.
        GLES20.glUseProgram(mProgramHandle)
        Egl.check("glUseProgram")

        // Set the texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(mTextureTarget, textureId)

        // Copy the texture transformation matrix over.
        GLES20.glUniformMatrix4fv(muTexMatrixLocation, 1, false, textureMatrix, 0)
        Egl.check("glUniformMatrix4fv")

        // Copy the model / view / projection matrix over.
        GLES20.glUniformMatrix4fv(muMVPMatrixLocation, 1, false, mvpMatrix, 0)
        Egl.check("glUniformMatrix4fv")

        // Enable the "aPosition" vertex attribute.
        // Connect vertexBuffer to "aPosition".
        GLES20.glEnableVertexAttribArray(maPositionLocation)
        Egl.check("glEnableVertexAttribArray")
        GLES20.glVertexAttribPointer(maPositionLocation, coordsPerVertex, GLES20.GL_FLOAT, false, vertexStride, vertexBuffer)
        Egl.check("glVertexAttribPointer")

        // Enable the "aTextureCoord" vertex attribute.
        // Connect texBuffer to "aTextureCoord".
        GLES20.glEnableVertexAttribArray(maTextureCoordLocation)
        Egl.check("glEnableVertexAttribArray")
        GLES20.glVertexAttribPointer(maTextureCoordLocation, 2, GLES20.GL_FLOAT, false, texCoordStride, texCoordBuffer)
        Egl.check("glVertexAttribPointer")


        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, firstVertex, vertexCount)
        Egl.check("glDrawArrays")

        // Done -- disable vertex array, texture, and program.
        GLES20.glDisableVertexAttribArray(maPositionLocation)
        GLES20.glDisableVertexAttribArray(maTextureCoordLocation)
        GLES20.glBindTexture(mTextureTarget, 0)
        GLES20.glUseProgram(0)
    }
}