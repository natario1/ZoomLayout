package com.otaliastudios.zoom.demo;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.video.VideoSize;
import com.otaliastudios.zoom.ZoomImageView;
import com.otaliastudios.zoom.ZoomLayout;
import com.otaliastudios.zoom.ZoomLogger;
import com.otaliastudios.zoom.ZoomSurfaceView;

public class MainActivity extends AppCompatActivity {

    private ExoPlayer player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ZoomLogger.setLogLevel(ZoomLogger.LEVEL_VERBOSE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final boolean supportsSurfaceView = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;
        if (supportsSurfaceView) setUpVideoPlayer();

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
                if (supportsSurfaceView) player.setPlayWhenReady(false);
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
                if (supportsSurfaceView) player.setPlayWhenReady(false);
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
                if (supportsSurfaceView) {
                    player.setPlayWhenReady(true);
                    zoomImage.setVisibility(View.GONE);
                    zoomLayout.setVisibility(View.GONE);
                    zoomSurface.setVisibility(View.VISIBLE);
                    buttonZoomLayout.setAlpha(0.65f);
                    buttonZoomImage.setAlpha(0.65f);
                    buttonZoomSurface.setAlpha(1f);
                } else {
                    Toast.makeText(MainActivity.this,
                            "ZoomSurfaceView requires API 18", Toast.LENGTH_SHORT).show();
                }
            }
        });
        buttonZoomLayout.performClick();
    }

    @Override
    protected void onStop() {
        super.onStop();
        ZoomSurfaceView surface = findViewById(R.id.surface_view);
        surface.onPause();
    }


    @Override
    protected void onStart() {
        super.onStart();
        ZoomSurfaceView surface = findViewById(R.id.surface_view);
        surface.onResume();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void setUpVideoPlayer() {
        player = new ExoPlayer.Builder(this).build();
        PlayerControlView controls = findViewById(R.id.player_control_view);
        final ZoomSurfaceView surface = findViewById(R.id.surface_view);
        player.addListener(new Player.Listener() {
            @Override
            public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
                surface.setContentSize(videoSize.width, videoSize.height);
            }
        });
        surface.setBackgroundColor(ContextCompat.getColor(this, R.color.background));
        surface.addCallback(new ZoomSurfaceView.Callback() {
            @Override
            public void onZoomSurfaceCreated(@NonNull ZoomSurfaceView view) {
                player.setVideoSurface(view.getSurface());
            }

            @Override
            public void onZoomSurfaceDestroyed(@NonNull ZoomSurfaceView view) { }
        });
        controls.setPlayer(player);
        controls.setShowTimeoutMs(0);
        controls.show();
        DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);
        Uri videoUri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4");
        MediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(videoUri));
        player.setMediaSource(videoSource);
        player.prepare();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        player.release();
    }
}
