---
layout: page
title: "Pan APIs"
description: "Pan controls common to all Zoom classes"
order: 5
disqus: 1
---

This page describes all the APIs related to pan. These are common to all Zoom classes in the library,
and also to the low level [engine](zoom-engine).

### APIs

All pan APIs accept x and y coordinates. These refer to the top-left visible pixel of the content.

- If using `ZoomLayout`, the coordinate system is that of the inner view
- If using `ZoomImageView`, the coordinate system is that of the drawable intrinsic width and height
- If using `ZoomSurfaceView`, the coordinate system is that of the view dimensions
- If using the engine directly, the coordinate system is that of the rect starting at `(0f, 0f)` and having the size you passed in `setContentSize`

In any case the current scale is not considered, so your system won't change if zoom changes.

|API|Description|Default value|
|---|-----------|-------------|
|`getPan()`|Returns the current pan as a point.|`-`|
|`getPanX()`|Returns the current horizontal pan.|`-`|
|`getPanY()`|Returns the current vertical pan.|`-`|
|`getScaledPan()`|Returns the current scaled pan as a point (pan * realZoom).|`-`|
|`getScaledPanX()`|Returns the current scaled horizontal pan (panX * realZoom).|`-`|
|`getScaledPanY()`|Returns the current scaled vertical pan (panY * realZoom).|`-`|
|`setOverScrollHorizontal(boolean)`|If true, the content will be allowed to pan outside its horizontal bounds, then return to its position.|`true`|
|`setOverScrollVertical(boolean)`|If true, the content will be allowed to pan outside its vertical bounds, then return to its position.|`true`|
|`setHorizontalPanEnabled(boolean)`|If true, the content will be allowed to pan **horizontally** by user input.|`true`|
|`setVerticalPanEnabled(boolean)`|If true, the content will be allowed to pan **vertically** by user input.|`true`|
|`setFlingEnabled(boolean)`|If true, fling gestures will be detected.|`true`|
|`setScrollEnabled(boolean)`|If true, scroll gestures will be detected.|`true`|
|`setOneFingerScrollEnabled(boolean)`|If true, one finger scroll gestures will be detected.|`true`|
|`setTwoFingersScrollEnabled(boolean)`|If true, two fingers scroll gestures will be detected.|`true`|
|`setThreeFingersScrollEnabled(boolean)`|If true, three fingers scroll gestures will be detected.|`true`|
|`setAllowFlingInOverscroll(boolean)`|If true, fling gestures will be allowed even when detected while overscrolled. This might cause artifacts so it is disabled by default.|`false`|
|`panTo(float, float, boolean)`|Pans to the given values, animating if needed.|`-`|
|`panBy(float, float, boolean)`|Applies the given deltas to the current pan, animating if needed.|`-`|
|`cancelAnimations()`|Cancels all currently active animations triggered by either API calls with `animate = true` or touch input flings.|`-`|

The `moveTo(float, float, float, boolean)` API will let you animate both [zoom](zoom-apis) and pan at the same time.

>Note: To pan the content of a ZoomLayout to the right you must move it to the left - so depending 
on the situtation you might need to pass in negative coordinates to `panTo` or `moveTo` for the 
desired outcome.
