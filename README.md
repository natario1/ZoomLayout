[![Build Status](https://travis-ci.org/natario1/ZoomLayout.svg?branch=master)](https://travis-ci.org/natario1/ZoomLayout)

<p align="center">
  <img src="art/logo_800.png" vspace="10" width="250" height="250">
</p>

# ZoomLayout & ZoomEngine

This library provides flexible utilities to control and animate zoom and translation of Views and much more - either
programmatically or through touch events. Try the demo app.

```groovy
compile 'com.otaliastudios:zoomlayout:1.0.2'
```

<p>
  <img src="art/preview_hierarchy.gif" width="250" vspace="20" hspace="5">
  <img src="art/preview_bitmap.gif" width="250" vspace="20" hspace="5">
</p>

## Features

- [`ZoomLayout`](#zoomlayout) : a container that supports 2D pan and zoom to a View hierarchy, even supporting clicks.
- [`ZoomImageView`](#zoomimageview) : (yet another) ImageView that supports 2D pan and zoom.
- Lightweight, no dependencies
- API 16

In fact, both `ZoomLayout` and `ZoomImageView` are just very simple implementations of the
internal [`ZoomEngine`](#zoomengine). The zoom engine lets you animate everything through
constant updates, as long as you feed it with touch events, with a `Matrix`-based mechanism
that makes it very flexible.

## ZoomLayout

A container for view hierarchies that can be panned or zoomed.

```xml
<com.otaliastudios.zoom.ZoomLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:overScrollable="true"
    app:overPinchable="true"
    app:minZoom="0.7f"
    app:minZoomType="zoom"
    app:maxZoom="3f"
    app:maxZoomType="zoom"
    app:hasClickableChildren="false">

    <!-- Content here. -->

</com.otaliastudios.zoom.ZoomLayout>
```
    
### Children
    
ZoomLayout supports only a single child, but that child can have as many children as you wish.
If any of these children is clickable or should react to touch events, you are required to set
`hasClickableChildren` to true. This is off by default because it is more expensive in terms of performance.

The child view will be measured as wrap content with no limits in space, as in a 2D scroll view.
So it can be as big as you want.

### APIs

You can access all the [internal APIs](#zoomengine) using `zoomLayout.getEngine()`.

## ZoomImageView

An `ImageView` implementation to control pan and zoom over its Drawable or Bitmap.

```xml
<com.otaliastudios.zoom.ZoomImageView
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:overScrollable="true"
    app:overPinchable="true"
    app:minZoom="0.7f"
    app:minZoomType="zoom"
    app:maxZoom="3f"
    app:maxZoomType="zoom"/>
```

There is nothing surprising going on. Just call `setImageDrawable()` and you are done.

Presumably ZoomImageView **won't** work if:

- the drawable has no intrinsic dimensions
- the view has wrap_content as a dimension
- you change the scaleType (read [later](#zoom) to know more)

There are lots of libraries on this topic and this is not necessarily *better*, yet it is
a natural implementations of the zoom engine. It is fast, lightweight and simple.
    
### APIs

You can access all the [internal APIs](#zoomengine) using `zoomImageView.getEngine()`.

## ZoomEngine

The low-level engine offers a `Matrix`-based stream of updates, as long as it is fed
with touch events and knows the dimensions of your content.

There is no strict limit over what you can do with a `Matrix`,

- move `Canvas` objects around
- transform `View` hierarchies
- apply to `ImageView`s or `Bitmap`
- transform `MotionEvent`s
- probably more

### Zoom

The engine currently applies, by default, a "center inside" policy when it is initialized.
This means that the content (whatever it is) is scaled down (or up) to fit the parent view bounds,
without cropping.

This base zoom makes the difference between **zoom** and **realZoom**. Table should be descriptive enough:

|Zoom type|Value|Description|
|---------|-----|-----------|
|Zoom|`ZoomEngine.TYPE_ZOOM`|The scale value after the initial, center-inside base zoom was applied. `zoom == 1` means that the content fits the screen perfectly.|
|Real zoom|`ZoomEngine.TYPE_REAL_ZOOM`|The actual scale value, including the initial base zoom. `realZoom == 1` means that the 1 inch of the content fits 1 inch of the screen.|

Some of the zoom APIs will let you pass an integer (either `TYPE_ZOOM` or `TYPE_REAL_ZOOM`)
to define the zoom you are referencing to. Depending on the context, imposing restrictions on one type
will make more sense than the other - e. g., in a PDF viewer, you might want to cap real zoom at `1`.

|API|Description|Default value|
|---|-----------|-------------|
|`getZoom()`|Returns the current zoom, not taking into account the base scale.|`1`|
|`getRealZoom()`|Returns the current zoom taking into account the base scale. This is the matrix scale.|`-`|
|`setMinZoom(float, @ZoomType int)`|Sets the lower bound when pinching out.|`0.8`, `TYPE_ZOOM`|
|`setMaxZoom(float, @ZoomType int)`|Sets the upper bound when pinching in.|`2.5`, `TYPE_REAL_ZOOM`|
|`setOverPinchable(boolean)`|If true, the content will be allowed to zoom outside its bounds, then return to its position.|`true`|
|`realZoomTo(float, boolean)`|Moves the real zoom to the given value, animating if needed.|`-`|
|`zoomTo(float, boolean)`|Moves the zoom to the given value, animating if needed.|`-`|
|`zoomBy(float, boolean)`|Applies the given factor to the current zoom, animating if needed. OK for both types.|`-`|

The `moveTo(float, float, float, boolean)` API will let you animate both zoom and [pan](#pan) at the same time.

### Pan

All pan APIs accept x and y coordinates. These refer to the top-left visible pixel of the content.

- If using `ZoomLayout`, the coordinate system is that of the inner view
- If using `ZoomImageView`, the coordinate system is that of the drawable intrinsic width and height
- If using the engine directly, the coordinate system is that of the rect you passed in `setContentRect`

In any case the current scale is not considered, so your system won't change if zoom changes.

|API|Description|Default value|
|---|-----------|-------------|
|`getPanX()`|Returns the current horizontal pan.|`-`|
|`getPanY()`|Returns the current vertical pan.|`-`|
|`setOverScrollable(boolean)`|If true, the content will be allowed to pan outside its bounds, then return to its position.|`true`|
|`panTo(float, float, boolean)`|Pans to the given values, animating if needed.|`-`|
|`panBy(float, float, boolean)`|Applies the given deltas to the current pan, animating if needed.|`-`|

The `moveTo(float, float, float, boolean)` API will let you animate both [zoom](#zoom) and pan at the same time.

### Direct usage

If you are interested in using the engine directly, I encourage you to take a look at the `ZoomLayout`
or `ZoomImageView` implementations. It is extremely simple. Basically:

- You construct a `ZoomEngine` passing the `View` that acts as a container for your content
- As soon as you know it (and whenever it changes), you pass the *content* dimensions
- As soon as you receive them, you pass touch updates to `onInterceptTouchEvent` or `onTouchEvent`
- The `ZoomEngine.Listener` is fed with `Matrix` updates

|API|Description|
|---|-----------|
|`setContentSize(RectF)`|Sets the size of the content, whatever it is.|
|`onTouchEvent(MotionEvent)`|Should be called to feed the engine with new events.|
|`onInterceptTouchEvent(MotionEvent)`|Should be called to feed the engine with new events.|

## Contributions

You are welcome to contribute with suggestions or pull requests.
I don't plan to add lots of features (specifically, I don't plan to have `ZoomImageView`
compete with similar libraries that already do this very well), the plan is to keep
this lightweight. But I welcome well-thought contributions.
