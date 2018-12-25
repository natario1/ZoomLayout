package com.otaliastudios.zoom

import android.util.Log
import androidx.annotation.IntDef

/**
 * Utility class that can log traces and info.
 */
class ZoomLogger private constructor(private val mTag: String) {

    @IntDef(LEVEL_VERBOSE, LEVEL_INFO, LEVEL_WARNING, LEVEL_ERROR)
    @Retention(AnnotationRetention.SOURCE)
    internal annotation class LogLevel

    internal fun v(message: String) {
        if (should(LEVEL_VERBOSE)) {
            Log.v(mTag, message)
            lastMessage = message
            lastTag = mTag
        }
    }

    internal fun i(message: String) {
        if (should(LEVEL_INFO)) {
            Log.i(mTag, message)
            lastMessage = message
            lastTag = mTag
        }
    }

    internal fun w(message: String) {
        if (should(LEVEL_WARNING)) {
            Log.w(mTag, message)
            lastMessage = message
            lastTag = mTag
        }
    }

    internal fun e(message: String) {
        if (should(LEVEL_ERROR)) {
            Log.e(mTag, message)
            lastMessage = message
            lastTag = mTag
        }
    }

    private fun should(@LogLevel messageLevel: Int): Boolean {
        return level <= messageLevel
    }

    private fun string(@LogLevel messageLevel: Int, vararg ofData: Any): String {
        return when (should(messageLevel)) {
            true -> ofData.joinToString(separator = " ")
            else -> ""
        }
    }

    internal fun v(vararg data: Any) {
        i(string(LEVEL_VERBOSE, *data))
    }

    internal fun i(vararg data: Any) {
        i(string(LEVEL_INFO, *data))
    }

    internal fun w(vararg data: Any) {
        w(string(LEVEL_WARNING, *data))
    }

    internal fun e(vararg data: Any) {
        e(string(LEVEL_ERROR, *data))
    }

    companion object {
        /**
         * Verbose logging level
         */
        const val LEVEL_VERBOSE = 0
        /**
         * Info logging level
         */
        const val LEVEL_INFO = 1
        /**
         * Warning logging level
         */
        const val LEVEL_WARNING = 2
        /**
         * Error logging level
         */
        const val LEVEL_ERROR = 3

        /**
         * Current logging level
         */
        private var level = LEVEL_ERROR

        /**
         * Set the most verbose log level to output in log
         *
         * @param logLevel a log level
         */
        @JvmStatic
        fun setLogLevel(@LogLevel logLevel: Int) {
            level = logLevel
        }

        internal var lastMessage: String? = null
        internal var lastTag: String? = null

        internal fun create(tag: String): ZoomLogger {
            return ZoomLogger(tag)
        }
    }
}

