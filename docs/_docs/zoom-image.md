---
layout: page
title: "ZoomImageView"
description: "An ImageView implementation supporting zoom and pan"
order: 2
disqus: 1
---

`ZoomImageView` is a `ImageView` implementation that can control pan and zoom over its Drawable or Bitmap.
Use it by simply declaring it in your XML layout:

```xml
<com.otaliastudios.zoom.ZoomImageView
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:scrollbars="vertical|horizontal"  
    app:transformation="centerInside"    
    app:transformationGravity="auto"  
    app:alignment="center"                                                                                                                        
    app:overScrollHorizontal="true"
    app:overScrollVertical="true"
    app:overPinchable="true"
    app:horizontalPanEnabled="true"
    app:verticalPanEnabled="true"
    app:zoomEnabled="true"
    app:flingEnabled="true"
    app:scrollEnabled="true"
    app:oneFingerScrollEnabled="true"
    app:twoFingersScrollEnabled="true"
    app:threeFingersScrollEnabled="true"
    app:minZoom="0.7"
    app:minZoomType="zoom"
    app:maxZoom="2.5"
    app:maxZoomType="zoom"
    app:animationDuration="280"/>
```

There is nothing surprising going on. Just call `setImageDrawable()` and you are done.

Presumably ZoomImageView **won't** work if:

- the drawable has no intrinsic dimensions (like a ColorDrawable)
- the view has wrap_content as a dimension
- you change the scaleType (read about [zoom APIs](zoom-apis) to know more)

There are lots of libraries on this topic and this is not necessarily *better*, yet it is
a natural implementations of the zoom engine. It is fast, lightweight and simple.
    
### APIs

The zoom image view will forward all API calls to the internal engine: see [zoom](zoom-apis), [pan](pan-apis) 
and [low level engine](zoom-engine) documentation. You can also get the backing engine using `zoomImageView.getEngine()`.

```java
zoomImageView.panTo(x, y, true); // Shorthand for zoomImageView.getEngine().panTo(x, y, true)
zoomImageView.panBy(deltaX, deltaY, true);
zoomImageView.zoomTo(zoom, true);
zoomImageView.zoomBy(factor, true);
zoomImageView.realZoomTo(realZoom, true);
zoomImageView.moveTo(zoom, x, y, true);
```
