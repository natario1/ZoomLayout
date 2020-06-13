package com.otaliastudios.zoom.demo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import android.widget.GridLayout;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Random;


public class ColorGridView extends GridLayout {

    private final static int ROWS = 20;
    private final static int COLS = 20;
    private final static Random R = new Random();

    private StaticLayout mText;

    public ColorGridView(@NonNull Context context) {
        this(context, null);
    }

    public ColorGridView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ColorGridView(@NonNull final Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        setRowCount(ROWS);
        setColumnCount(COLS);
        for (int row = 0; row < ROWS; row++) {
            Spec rowSpec = spec(row);
            for (int col = 0; col < COLS; col++) {
                Spec colSpec = spec(col);
                LayoutParams params = new GridLayout.LayoutParams(rowSpec, colSpec);
                params.width = 150;
                params.height = 150;
                View view = createView(context);
                addView(view, params);
            }
        }
    }

    private static View createView(final Context context) {
        View view = new View(context);
        final int r = 200 + R.nextInt(55);
        final int g = 100 + R.nextInt(100);
        final int b = 50 + R.nextInt(100);
        int color = Color.rgb(r, g, b);
        view.setBackground(new ColorDrawable(color));
        view.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                view.setBackgroundColor(Color.BLACK);
            }
        });
        view.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                view.setBackgroundColor(Color.WHITE);
                return true;
            }
        });
        return view;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mText == null) {
            TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(150f * (float) ROWS / 10f);
            mText = new StaticLayout("This is a view hierarchy, with clickable children.",
                    paint, getWidth(), Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
        }
        canvas.save();
        canvas.translate(getWidth() / 2f, (getHeight() - mText.getHeight()) / 2f);
        mText.draw(canvas);
        canvas.restore();
    }
}
