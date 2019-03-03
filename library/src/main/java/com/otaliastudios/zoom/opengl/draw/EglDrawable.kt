package com.otaliastudios.zoom.opengl.draw


import android.os.Build
import androidx.annotation.RequiresApi
import java.nio.FloatBuffer

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
internal abstract class EglDrawable {

    /**
     * Returns the array of vertices.
     * To avoid allocations, this returns internal state.  The caller must not modify it.
     */
    abstract val vertexArray: FloatBuffer

    /**
     * Returns the number of vertices stored in the vertex array.
     */
    abstract val vertexCount: Int

    /**
     * Returns the width, in bytes, of the data for each vertex.
     */
    abstract val vertexStride: Int

    /**
     * Returns the array of texture coordinates.
     * To avoid allocations, this returns internal state.  The caller must not modify it.
     */
    abstract val texCoordArray: FloatBuffer

    /**
     * Returns the width, in bytes, of the data for each texture coordinate.
     */
    abstract val texCoordStride: Int

    /**
     * Returns the number of position coordinates per vertex.  This will be 2 or 3.
     */
    abstract val texCoordsPerVertex: Int
}