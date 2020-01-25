---
layout: page
title: "ZoomSurfaceView"
description: "A SurfaceView implementation supporting zoom and pan"
order: 3
disqus: 1
---


A `SurfaceView` implementation (extending `GLSurfaceView`) that supports panning and zooming of its contents
through OpenGL rendering. You can use this for video streaming, camera previews or any other surface application
that streams image buffers into a `Surface`.

```xml
<com.otaliastudios.zoom.ZoomSurfaceView
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:transformation="centerInside"
    app:transformationGravity="auto"
    app:alignment="center"
    app:overScrollHorizontal="false"
    app:overScrollVertical="false"
    app:overPinchable="false"
    app:horizontalPanEnabled="true"
    app:verticalPanEnabled="true"
    app:zoomEnabled="true"
    app:flingEnabled="true"
    app:scrollEnabled="true"
    app:oneFingerScrollEnabled="true"
    app:twoFingersScrollEnabled="true"
    app:threeFingersScrollEnabled="true"
    app:minZoom="1"
    app:minZoomType="zoom"
    app:maxZoom="2.5"
    app:maxZoomType="zoom"
    app:animationDuration="280"/>
```

### Setup

There are a few special things about `ZoomSurfaceView` with respect to the other classes:

- It **requires** API level 18
- It will not draw scrollbars
- You **must** either call `ZoomSurfaceView.setContentSize()` passing the stream size, or measure the
  view so that it matches the stream aspect ratio.

> Starting from v1.7.1, you do not need to add the [Egloo](https://github.com/natario1/Egloo) dependency
in order to use `ZoomSurfaceView`.

### Usage

Using `Surface`s is not a simple topic so we won't go into details here. Please take a look
at the demo app which reproduces a zoomable/pannable video through ExoPlayer.

To get a usable `Surface` out of `ZoomSurfaceView`, please add a callback and wait for the surface
to be available:

```java
ZoomSurfaceView surfaceView = findViewById(R.id.zoom_surface_view);
surfaceView.addCallback(new ZoomSurfaceView.Callback() {
    @Override
    public void onZoomSurfaceCreated(@NotNull ZoomSurfaceView view) {
        Surface surface = view.getSurface();
        // Use this surface for video players, camera preview, ...
    }

    @Override
    public void onZoomSurfaceDestroyed(@NotNull ZoomSurfaceView view) { }
});
```

### APIs

The `ZoomSurfaceView` will forward all API calls to the internal engine: see [zoom](zoom-apis), [pan](pan-apis) 
and [low level engine](zoom-engine) documentation. You can also get the backing engine using `zoomSurfaceView.getEngine()`.

```java
zoomSurfaceView.panTo(x, y, true); // Shorthand for zoomSurfaceView.getEngine().panTo(x, y, true)
zoomSurfaceView.panBy(deltaX, deltaY, true);
zoomSurfaceView.zoomTo(zoom, true);
zoomSurfaceView.zoomBy(factor, true);
zoomSurfaceView.realZoomTo(realZoom, true);
zoomSurfaceView.moveTo(zoom, x, y, true);
```
