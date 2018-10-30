package com.otaliastudios.zoom;

import androidx.annotation.IntDef;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Utility class that can log traces and info.
 */
public final class ZoomLogger {

    public final static int LEVEL_VERBOSE = 0;
    public final static int LEVEL_INFO = 1;
    public final static int LEVEL_WARNING = 2;
    public final static int LEVEL_ERROR = 3;

    @IntDef({LEVEL_VERBOSE, LEVEL_WARNING, LEVEL_ERROR})
    @Retention(RetentionPolicy.SOURCE)
    @interface LogLevel {}

    private static int level = LEVEL_ERROR;

    public static void setLogLevel(int logLevel) {
        level = logLevel;
    }

    static String lastMessage;
    static String lastTag;

    static ZoomLogger create(String tag) {
        return new ZoomLogger(tag);
    }

    private String mTag;

    private ZoomLogger(String tag) {
        mTag = tag;
    }

    void v(String message) {
        if (should(LEVEL_VERBOSE)) {
            Log.v(mTag, message);
            lastMessage = message;
            lastTag = mTag;
        }
    }

    void i(String message) {
        if (should(LEVEL_INFO)) {
            Log.i(mTag, message);
            lastMessage = message;
            lastTag = mTag;
        }
    }

    void w(String message) {
        if (should(LEVEL_WARNING)) {
            Log.w(mTag, message);
            lastMessage = message;
            lastTag = mTag;
        }
    }

    void e(String message) {
        if (should(LEVEL_ERROR)) {
            Log.e(mTag, message);
            lastMessage = message;
            lastTag = mTag;
        }
    }

    private boolean should(int messageLevel) {
        return level <= messageLevel;
    }

    private String string(int messageLevel, Object... ofData) {
        String message = "";
        if (should(messageLevel)) {
            for (Object o : ofData) {
                message += String.valueOf(o);
                message += " ";
            }
        }
        return message.trim();
    }

    void v(Object... data) {
        i(string(LEVEL_VERBOSE, data));
    }

    void i(Object... data) {
        i(string(LEVEL_INFO, data));
    }

    void w(Object... data) {
        w(string(LEVEL_WARNING, data));
    }

    void e(Object... data) {
        e(string(LEVEL_ERROR, data));
    }
}

