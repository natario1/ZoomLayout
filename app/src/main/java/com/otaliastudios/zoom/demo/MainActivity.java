package com.otaliastudios.zoom.demo;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridView;

import com.otaliastudios.zoom.ZoomImageView;
import com.otaliastudios.zoom.ZoomLayout;
import com.otaliastudios.zoom.ZoomLogger;

import androidx.appcompat.app.AppCompatActivity;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ZoomLogger.setLogLevel(ZoomLogger.LEVEL_INFO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Button buttonZoomLayout = findViewById(R.id.show_zl);
        final Button buttonZoomImage = findViewById(R.id.show_ziv);
        final ZoomLayout zoomLayout = findViewById(R.id.zoom_layout);
        final ZoomImageView zoomImage = findViewById(R.id.zoom_image);
        zoomImage.setImageDrawable(new ColorGridDrawable());

        final GridView gridView = findViewById(R.id.gridview);
        zoomLayout.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                ViewGroup.LayoutParams params = gridView.getLayoutParams();
                params.width = v.getWidth();
                params.height = v.getHeight();
                gridView.setLayoutParams(params);
            }
        });
        gridView.setAdapter(new GridViewAdapter());

        buttonZoomLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                zoomImage.setVisibility(View.GONE);
                zoomLayout.setVisibility(View.VISIBLE);
                buttonZoomImage.setAlpha(0.65f);
                buttonZoomLayout.setAlpha(1f);
            }
        });

        buttonZoomImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                zoomLayout.setVisibility(View.GONE);
                zoomImage.setVisibility(View.VISIBLE);
                buttonZoomLayout.setAlpha(0.65f);
                buttonZoomImage.setAlpha(1f);
            }
        });

        buttonZoomLayout.performClick();
    }
}
