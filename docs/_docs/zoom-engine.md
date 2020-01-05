---
layout: page
title: "Internal Engine"
description: "Advanced usage through the internal ZoomEngine"
order: 6
disqus: 1
---

The low-level engine offers a `Matrix`-based stream of updates, as long as it is fed
with touch events and knows the dimensions of your content.

There is no strict limit over what you can do with a `Matrix`,

- move `Canvas` objects around
- transform `View` hierarchies
- apply to `ImageView`s or `Bitmap`
- transform `MotionEvent`s
- probably more

### Direct usage

If you are interested in using the engine directly, I encourage you to take a look at the `ZoomLayout`
or `ZoomImageView` implementations. It is extremely simple. Basically:

- You construct a `ZoomEngine` passing the `View` that acts as a container for your content
- As soon as you know it (and whenever it changes), you pass the *content* dimensions using `setContentSize(float, float)`
- As soon as you receive them, you pass touch updates to `onInterceptTouchEvent` or `onTouchEvent`
- Any `ZoomEngine.Listener` subscribed will be passed `Matrix` updates

|API|Description|
|---|-----------|
|`setContentSize(float, float)`|Sets the size of the content, whatever it is.|
|`onTouchEvent(MotionEvent)`|Should be called to feed the engine with new events.|
|`onInterceptTouchEvent(MotionEvent)`|Should be called to feed the engine with new events.|
|`setContainerSize(float, float)`|Updates the container size. This is generally not needed. The engine will get the container dimensions using a OnGlobalLayout listener. However, in some cases, you might want to trigger this directly.|

The size methods will also accept a boolean indicating whether the engine should re-apply the transformation.
The transformation is always applied if the engine is in its starting state.