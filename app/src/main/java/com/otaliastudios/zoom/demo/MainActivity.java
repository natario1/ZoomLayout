package com.otaliastudios.zoom.demo;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoListener;
import com.otaliastudios.zoom.ZoomImageView;
import com.otaliastudios.zoom.ZoomLayout;
import com.otaliastudios.zoom.ZoomLogger;
import com.otaliastudios.zoom.ZoomSurfaceView;

import org.jetbrains.annotations.NotNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;


public class MainActivity extends AppCompatActivity {

    private SimpleExoPlayer player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ZoomLogger.setLogLevel(ZoomLogger.LEVEL_VERBOSE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setUpVideoPlayer();

        final Button buttonZoomLayout = findViewById(R.id.show_zl);
        final Button buttonZoomImage = findViewById(R.id.show_ziv);
        final Button buttonZoomSurface = findViewById(R.id.show_zsv);
        final ZoomLayout zoomLayout = findViewById(R.id.zoom_layout);
        final ZoomImageView zoomImage = findViewById(R.id.zoom_image);
        final View zoomSurface = findViewById(R.id.zoom_surface);
        zoomImage.setImageDrawable(new ColorGridDrawable());

        buttonZoomLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                player.setPlayWhenReady(false);
                zoomSurface.setVisibility(View.GONE);
                zoomImage.setVisibility(View.GONE);
                zoomLayout.setVisibility(View.VISIBLE);
                buttonZoomImage.setAlpha(0.65f);
                buttonZoomSurface.setAlpha(0.65f);
                buttonZoomLayout.setAlpha(1f);
            }
        });

        buttonZoomImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                player.setPlayWhenReady(false);
                zoomSurface.setVisibility(View.GONE);
                zoomLayout.setVisibility(View.GONE);
                zoomImage.setVisibility(View.VISIBLE);
                buttonZoomLayout.setAlpha(0.65f);
                buttonZoomSurface.setAlpha(0.65f);
                buttonZoomImage.setAlpha(1f);
            }
        });
        buttonZoomSurface.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                player.setPlayWhenReady(true);
                zoomImage.setVisibility(View.GONE);
                zoomLayout.setVisibility(View.GONE);
                zoomSurface.setVisibility(View.VISIBLE);
                buttonZoomLayout.setAlpha(0.65f);
                buttonZoomImage.setAlpha(0.65f);
                buttonZoomSurface.setAlpha(1f);

            }
        });
        buttonZoomLayout.performClick();
    }

    private void setUpVideoPlayer() {
        player = ExoPlayerFactory.newSimpleInstance(this);
        PlayerControlView controls = findViewById(R.id.player_control_view);
        ZoomSurfaceView surface = findViewById(R.id.surface_view);
        VideoSurfaceContainer container = findViewById(R.id.surface_view_container);
        container.setPlayer(player);
        surface.addCallback(new ZoomSurfaceView.Callback() {
            @Override
            public void onZoomSurfaceCreated(@NotNull ZoomSurfaceView view) {
                player.setVideoSurface(view.createSurface());
            }

            @Override
            public void onZoomSurfaceChanged(@NotNull ZoomSurfaceView view, int width, int height) { }

            @Override
            public void onZoomSurfaceDestroyed(@NotNull ZoomSurfaceView view) { }
        });
        controls.setPlayer(player);
        controls.setShowTimeoutMs(0);
        controls.show();
        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this,
                Util.getUserAgent(this, "ZoomLayoutLib"));
        MediaSource videoSource = new ExtractorMediaSource.Factory(dataSourceFactory)
                .createMediaSource(Uri.parse("https://html5demos.com/assets/dizzy.mp4"));
        player.prepare(videoSource);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        player.release();
    }
}
