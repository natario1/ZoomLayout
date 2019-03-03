package com.otaliastudios.zoom.opengl.program

import android.os.Build
import androidx.annotation.RequiresApi
import com.otaliastudios.zoom.opengl.draw.EglRect

/**
 * A simple [EglTextureProgram] that draws using an [EglRect] shape.
 * This is like grafika FullFrameRect: binds together the drawable and the
 * program.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
internal class EglRectTextureProgram : EglTextureProgram() {

    private val drawable = EglRect()

    internal fun drawRect(textureId: Int, textureMatrix: FloatArray) {
        drawEglDrawable(textureId, textureMatrix, drawable)
    }

}