---
layout: main
title: "ZoomLayout"
---

# ZoomLayout

ZoomLayout is a collection of Android components that support zooming and panning of View hierarchies, 
images, video streams, and much more.

<p align="center">
  <img src="static/banner.png" vspace="10" width="100%">
</p>

- `ZoomLayout`: a container that supports 2D pan and zoom to a View hierarchy, even supporting clicks [[docs]](docs/zoom-layout)
- `ZoomImageView`: (yet another) ImageView that supports 2D pan and zoom [[docs]](docs/zoom-image)
- `ZoomSurfaceView`: A SurfaceView that supports 2D pan and zoom with OpenGL rendering [[docs]](docs/zoom-surface)
- Powerful zoom APIs [[docs]](docs/zoom-apis)
- Powerful pan APIs [[docs]](docs/pan-apis)
- Lightweight, no dependencies
- Works down to API 16

In fact, `ZoomLayout`, `ZoomImageView` and `ZoomSurfaceView` are just very simple implementations of the
internal `ZoomEngine` [[docs]](docs/zoom-engine). The zoom engine lets you animate everything through
constant updates, as long as you feed it with touch events, with a `Matrix`-based mechanism
that makes it very flexible.

### Get started

Get started with [install info](about/install) or start reading the in-depth [documentation](docs/zoom-layout).

You can see `ZoomLayout` in action through our demo app, or if you're curious, in 
[ViewPrinter](https://github.com/natario1/ViewPrinter), a printing library heavily based on this.

### Support

If you like the project, use it with profit, and want to thank back, please consider [donating or
becoming a sponsor](extra/donate).

