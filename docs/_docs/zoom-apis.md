---
layout: page
title: "Zoom APIs"
description: "Zoom controls common to all Zoom classes"
order: 4
disqus: 1
---

This page describes all the APIs related to zoom. These are common to all Zoom classes in the library,
and also to the low level [engine](zoom-engine).

### Transformation

The transformation defines the engine **resting position**. It is a keyframe that is reached at
certain points, like at start-up or when explicitly requested through `setContentSize` or `setContainerSize`.

The keyframe is defined by two elements: 

- a `transformation` value (modifies zoom in a certain way)
- a `transformationGravity` value (modifies pan in a certain way)

which can be controlled through `setTransformation(int, int)` or `app:transformation` and `app:transformationGravity`.

|Transformation|Description|
|--------------|-----------|
|`centerInside`|The content is scaled down or up so that it fits completely inside the view bounds.|
|`centerCrop`|The content is scaled down or up so that its smaller side fits exactly inside the view bounds. The larger side will be cropped.|
|`none`|No transformation is applied.|

After transformation is applied, the transformation gravity will reposition the content with
the specified value. Supported values are most of the `android.view.Gravity` flags like `Gravity.TOP`, plus `TRANSFORMATION_GRAVITY_AUTO`.

|Transformation Gravity|Description|
|----------------------|-----------|
|`top`, ...|The content is panned so that its *top* side matches teh container *top* side. Same for other values.|
|`auto` (default)|The transformation gravity is taken from the engine [alignment](#alignment), defaults to `center` on both axes.|

>Note: after transformation and gravity are applied, the engine will apply - as always - all the active constraints,
including minZoom, maxZoom, alignment. This means that the final position might be slightly (or completely) different.

For example, when `maxZoom == 1`, the content is forced to not be any larger than the container. This means that
a `centerCrop` transformation will not have the desired effect: it will act just like a `centerInside`.

### Alignment

You can force the content position with respect to the container using the `setAlignment(int)` method
or the `alignment` XML flag of `ZoomLayout` and `ZoomImageView`. 
The default value is `Alignment.CENTER` which will center the content on both directions.

>Note: alignment does not make sense when content is larger than the container, because forcing an 
alignment (e.g. left) would mean making part of the content unreachable (e.g. the right part).

|Alignment|Description|
|---------|-----------|
|`top`, `bottom`, `left`, `right`|Force align the content to the same side of the container.|
|`center_horizontal`, `center_vertical`|Force the content to be centered inside the container on that axis.|
|`none_horizontal`, `none_vertical`|No alignment set: content is free to be moved on that axis.|

You can use the `or` operation to mix the vertical and horizontal flags:

```kotlin
engine.setAlignment(Alignment.TOP or Alignment.LEFT)
engine.setAlignment(Alignment.TOP) // Equals to Aligment.TOP or Alignment.NONE_HORIZONTAL
engine.setAlignment(Alignment.NONE) // Remove any forced alignment
```

### Zoom Types

The base transformation makes the difference between **zoom** and **realZoom**. Since we have silently applied
a base zoom to the content, we must introduce two separate types:

|Zoom type|Value|Description|
|---------|-----|-----------|
|Zoom|`TYPE_ZOOM`|The scale value after the initial transformation. `zoom == 1` means that the content was untouched after the transformation.|
|Real zoom|`TYPE_REAL_ZOOM`|The actual scale value, including the initial transformation. `realZoom == 1` means that the 1 inch of the content fits 1 inch of the screen.|

To make things clearer, when transformation is `none`, the zoom and the real zoom will be identical.
The distinction is very useful when it comes to imposing min and max constraints to our zoom value.

Note that these values will change if the `setContentSize` or `setContainerSize` APIs are used
with `applyTransformation = true`.

### APIs

Some of the zoom APIs will let you pass an integer (either `TYPE_ZOOM` or `TYPE_REAL_ZOOM`)
to define the zoom type you are referencing to. Depending on the context, imposing restrictions on one type
will make more sense than the other - e. g., in a PDF viewer, you might want to cap real zoom at `1`.

|API|Description|Default value|
|---|-----------|-------------|
|`getZoom()`|Returns the current zoom, not taking into account the base scale.|`1`|
|`getRealZoom()`|Returns the current zoom taking into account the base scale. This is the matrix scale.|`-`|
|`getMinZoom()`|Returns the current min zoom.|`0.8`|
|`getMinZoomType()`|Returns the current min zoom type.|`TYPE_ZOOM`|
|`setMinZoom(float, @ZoomType int)`|Sets the lower bound when pinching out.|`0.8`, `TYPE_ZOOM`|
|`getMaxZoom()`|Returns the current max zoom.|`2.5`|
|`getMaxZoomType()`|Returns the current max zoom type.|`TYPE_ZOOM`|
|`setMaxZoom(float, @ZoomType int)`|Sets the upper bound when pinching in.|`2.5`, `TYPE_REAL_ZOOM`|
|`setOverPinchable(boolean)`|If true, the content will be allowed to zoom outside its bounds, then return to its position.|`true`|
|`setOverZoomRange(provider)`|Sets an OverZoomRangeProvider that defines the allowed amount to zoom outside the content's bounds.|`DEFAULT_OVERZOOM_PROVIDER`|
|`setOverScrollHorizontal(boolean)`|If true, the content will be allowed to horizontally pan outside its bounds, then return to its position.|`true`|
|`setOverScrollVertical(boolean)`|If true, the content will be allowed to vertically pan outside its bounds, then return to its position.|`true`|
|`setOverPanRange(provider)`|Sets an OverPanRangeProvider that defines the allowed amount to pan outside the content's bounds.|`DEFAULT_OVERPAN_PROVIDER`|
|`realZoomTo(float, boolean)`|Moves the real zoom to the given value, animating if needed.|`-`|
|`zoomTo(float, boolean)`|Moves the zoom to the given value, animating if needed.|`-`|
|`zoomBy(float, boolean)`|Applies the given factor to the current zoom, animating if needed. OK for both types.|`-`|
|`zoomIn()`|Applies a small, animated zoom-in.|`-`|
|`zoomOut()`|Applies a small, animated zoom-out.|`-`|
|`setZoomEnabled(boolean)`|If true, the content will be allowed to zoom in and out by user input.|`true`|


The `moveTo(float, float, float, boolean)` API will let you animate both zoom and [pan](pan-apis) at the same time.
