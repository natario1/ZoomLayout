package com.otaliastudios.zoom.demo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import android.widget.GridLayout;

import java.util.Random;


public class ColorGridDrawable extends Drawable {

    private final static int ROWS = 20;
    private final static int COLS = 20;
    private final static Random R = new Random();
    private final static int[][] COLOR_CACHE = new int[ROWS][COLS];

    private final Paint mPaint = new Paint();
    private final TextPaint mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Rect mRect = new Rect(0, 0, 150, 150);

    public ColorGridDrawable() {
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTextSize((float) getIntrinsicHeight() / 10f);
    }

    @Override
    public void setAlpha(@IntRange(from = 0, to = 255) int i) {}

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {}

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    @Override
    public int getIntrinsicHeight() {
        return mRect.height() * COLS;
    }

    @Override
    public int getIntrinsicWidth() {
        return mRect.width() * ROWS;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        for (int col = 0; col < COLS; col++) {
            for (int row = 0; row < ROWS; row++) {
                int restore = canvas.save();
                canvas.translate(150 * col, 150 * row);
                mPaint.setColor(getColor(row, col));
                canvas.drawRect(mRect, mPaint);
                canvas.restoreToCount(restore);
            }
        }
        canvas.drawText("This is a drawable.",
                getIntrinsicWidth() / 2f,
                getIntrinsicHeight() / 2f,
                mTextPaint);
    }

    private static int getColor(int row, int col) {
        if (COLOR_CACHE[row][col] == 0) {
            final int r = 140 + R.nextInt(100);
            final int g = 140 + R.nextInt(100);
            final int b = 50 + R.nextInt(100);
            COLOR_CACHE[row][col] = Color.rgb(r, g, b);
        }
        return COLOR_CACHE[row][col];
    }
}
