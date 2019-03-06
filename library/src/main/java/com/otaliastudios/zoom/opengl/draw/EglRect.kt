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
        private val FULL_RECTANGLE_COORDS = Egl.floatBufferOf(floatArrayOf(
                -1.0f, -1.0f, // 0 bottom left
                1.0f, -1.0f, // 1 bottom right
                -1.0f, 1.0f, // 2 top left
                1.0f, 1.0f)) // 3 top right

        private const val COORDS_PER_VERTEX = 2
    }

    fun setVertexCoords(array: FloatArray) {
        vertexCoords = Egl.floatBufferOf(array)
    }

    private var vertexCoords = FULL_RECTANGLE_COORDS

    override val vertexArray: FloatBuffer
        get() = vertexCoords

    override val vertexCount: Int
        get() = 8 /* floats in FULL_RECTANGLE_COORDS */ / coordsPerVertex

    override val coordsPerVertex = COORDS_PER_VERTEX
}