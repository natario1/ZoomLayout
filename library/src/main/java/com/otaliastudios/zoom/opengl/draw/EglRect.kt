package com.otaliastudios.zoom.opengl.draw

import com.otaliastudios.zoom.opengl.core.Egl
import java.nio.FloatBuffer


/**
 * Includes stuff from grafika's Drawable2d FULL_RECTANGLE.
 */
internal class EglRect: EglDrawable() {

    companion object {

        // A full square, extending from -1 to +1 in both dimensions.
        // When the model/view/projection matrix is identity, this will exactly cover the viewport.
        private val FULL_RECTANGLE_COORDS = floatArrayOf(-1.0f, -1.0f, // 0 bottom left
                1.0f, -1.0f, // 1 bottom right
                -1.0f, 1.0f, // 2 top left
                1.0f, 1.0f)// 3 top right

        // A full square, extending from -1 to +1 in both dimensions.
        private val FULL_RECTANGLE_TEX_COORDS = floatArrayOf(0.0f, 0.0f, // 0 bottom left
                1.0f, 0.0f, // 1 bottom right
                0.0f, 1.0f, // 2 top left
                1.0f, 1.0f      // 3 top right
        )

        private const val SIZE_OF_FLOAT = 4
        private const val COORDS_PER_VERTEX = 2
    }

    private val mVertexCoordinatesArray = Egl.floatBuffer(FULL_RECTANGLE_COORDS)
    private val mTextureCoordinatesArray = Egl.floatBuffer(FULL_RECTANGLE_TEX_COORDS)

    override val vertexArray: FloatBuffer
        get() = mVertexCoordinatesArray

    override val texCoordArray: FloatBuffer
        get() = mTextureCoordinatesArray

    override val vertexCount: Int
        get() = FULL_RECTANGLE_COORDS.size / COORDS_PER_VERTEX

    override val texCoordsPerVertex: Int
        get() = COORDS_PER_VERTEX

    override val vertexStride: Int
        get() = COORDS_PER_VERTEX * SIZE_OF_FLOAT

    override val texCoordStride: Int
        get() = 2 * SIZE_OF_FLOAT
}