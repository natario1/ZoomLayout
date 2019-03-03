package com.otaliastudios.zoom.opengl.core

import android.annotation.TargetApi
import android.opengl.*
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
object EglOptions {

    @JvmField
    val CLAMP_TO_EDGE = GLES20.GL_CLAMP_TO_EDGE

    @JvmField
    val CLAMP_TO_BORDER = 0x812D // GLES32.GL_CLAMP_TO_BORDER (Api24) GLES31Ext.GL_CLAMP_TO_BORDER_EXT (Api21)

    @JvmField
    val REPEAT = GLES20.GL_REPEAT

    @JvmField
    val MIRRORED_REPEAT = GLES20.GL_MIRRORED_REPEAT

    internal var glTextureWrapS = CLAMP_TO_EDGE
        private set

    internal var glTextureWrapT = CLAMP_TO_EDGE
        private set

    @JvmStatic
    fun setTextureWrapS(value: Int) {
        glTextureWrapS = value
    }

    @JvmStatic
    fun setTextureWrapT(value: Int) {
        glTextureWrapT = value
    }

}