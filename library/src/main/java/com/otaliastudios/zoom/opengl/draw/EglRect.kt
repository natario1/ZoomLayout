package com.otaliastudios.zoom.opengl.draw

import android.os.Build
import androidx.annotation.RequiresApi
import com.otaliastudios.zoom.opengl.core.Egl
import com.otaliastudios.zoom.opengl.program.EglFlatProgram
import com.otaliastudios.zoom.opengl.program.EglTextureProgram
import java.nio.FloatBuffer


/**
 * Includes stuff from grafika's Drawable2d FULL_RECTANGLE.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
internal class EglRect: EglDrawable() {

    companion object {

        // A full square, extending from -1 to +1 in both dimensions.
        // When the model/view/projection matrix is identity, this will exactly cover the viewport.
        private val FULL_RECTANGLE_COORDS = floatArrayOf(
                -1.0f, -1.0f, // 0 bottom left
                1.0f, -1.0f, // 1 bottom right
                -1.0f, 1.0f, // 2 top left
                1.0f, 1.0f) // 3 top right

        private val FULL_RECTANGLE_TEX_COORDS = floatArrayOf(
                0.0f, 0.0f, // 0 bottom left
                1.0f, 0.0f, // 1 bottom right
                0.0f, 1.0f, // 2 top left
                1.0f, 1.0f // 3 top right
        )

        private const val SIZE_OF_FLOAT = 4
        private const val COORDS_PER_VERTEX = 2
    }

    private var mVertexCoordinates = Egl.floatBufferOf(FULL_RECTANGLE_COORDS)
    private val mTextureCoordinates = Egl.floatBufferOf(FULL_RECTANGLE_TEX_COORDS)

    override val vertexArray: FloatBuffer
        get() = mVertexCoordinates

    override val texCoordArray: FloatBuffer
        get() = mTextureCoordinates

    override val vertexCount: Int
        get() = 8 /* FULL_RECTANGLE_COORDS.size */ / COORDS_PER_VERTEX

    override val coordsPerVertex: Int
        get() = COORDS_PER_VERTEX

    override val vertexStride: Int
        get() = COORDS_PER_VERTEX * SIZE_OF_FLOAT

    override val texCoordStride: Int
        get() = 2 * SIZE_OF_FLOAT

    fun setVertexCoordinates(coords: FloatArray) {
        Egl.setFloatBuffer(mVertexCoordinates, coords)
    }

    fun drawFlat(program: EglFlatProgram, color: FloatArray, mvpMatrix: FloatArray = Egl.IDENTITY_MATRIX) {
        program.draw(mvpMatrix,
                color,
                vertexArray, 0, vertexCount,
                vertexStride, coordsPerVertex)
    }

    fun drawTexture(program: EglTextureProgram, textureId: Int, textureMatrix: FloatArray, mvpMatrix: FloatArray = Egl.IDENTITY_MATRIX) {
        program.draw(mvpMatrix,
                textureId, textureMatrix,
                vertexArray, 0, vertexCount,
                vertexStride, coordsPerVertex,
                texCoordArray, texCoordStride)
    }
}