package com.otaliastudios.zoom.demo;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.FrameLayout;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.video.VideoListener;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ZoomSurfaceViewContainer extends FrameLayout implements VideoListener {

    private SimpleExoPlayer player;
    private float videoAspectRatio = -1F;

    public ZoomSurfaceViewContainer(@NonNull Context context) {
        super(context);
    }

    public ZoomSurfaceViewContainer(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setPlayer(SimpleExoPlayer player) {
        if (this.player != null) this.player.getVideoComponent().removeVideoListener(this);
        this.player = player;
        this.player.getVideoComponent().addVideoListener(this);
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        Log.e("ZoomSurfaceContainer", "onVideoSizeChanged: " + width + "x" + height);
        videoAspectRatio = (height == 0 || width == 0) ? 1 : (width * pixelWidthHeightRatio) / height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (videoAspectRatio <= 0) return;
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        float viewAspectRatio = (float) width / height;
        float aspectDeformation = videoAspectRatio / viewAspectRatio - 1;
        if (aspectDeformation > 0) {
            height = (int) (width / videoAspectRatio);
        } else {
            width = (int) (height * videoAspectRatio);
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
