---
layout: page
title: "Changelog"
order: 2
---

New versions are released through GitHub, so the reference page is the [GitHub Releases](https://github.com/natario1/ZoomLayout/releases) page.

Starting from 1.7.0, you can now [support development](https://github.com/natario1/ZoomLayout/issues/125) through the GitHub Sponsors program. 
Companies can share a tiny part of their revenue and get private support hours in return. Thanks!

## v1.9.0

The library is now hosted on the Maven Central repository.

- Enhancement: open ZoomEngine constructor, thanks to [@coffeemakr][coffeemakr] ([#193][193])
- Fix: improved moveTo animation ([#189][189])
- Fix: fix verbose logging, thanks to [@r4zzz4k][r4zzz4k] ([#214][214])

<https://github.com/natario1/ZoomLayout/compare/v1.8.0...v1.9.0>

## v1.8.0

- Enhancement: raise an exception if a drawable without intrinsic dimensions is used ([#157][157])
- Enhancement: overzoom customization ([#164][164])
- Enhancement: max less than min ([#166][166])
- Enhancement: move to center ([#167][167])
- Fix: cancel fling with touch ([#159][159])
- Fix: pan to center ([#158][158])
- Fix: updated video url ([#156][156])

<https://github.com/natario1/ZoomLayout/compare/v1.7.1...v1.8.0>

#### v1.7.1

- Enhancement: the Egloo library is now a declared dependency and you don't need to add it separately. ([#136][136]) 

<https://github.com/natario1/ZoomLayout/compare/v1.7.0...v1.7.1>

## v1.7.0

- **Breaking change**: To use ZoomSurfaceView, you need to use `com.otaliastudios.opengl:egloo:0.2.3` instead of `com.otaliastudios.opengl:egl-core`. ([#114][114]) 
- Dependencies and tools updates ([#114][114])

<https://github.com/natario1/ZoomLayout/compare/v1.6.1...v1.7.0>

#### v1.6.1

- Enhancement: Internal refactoring into smaller components [@natario1][natario1] ([#97][97]) 
- Enhancement: Added scrollEnabled, oneFingerScrollEnabled, twoFingersScrollEnabled and threeFingersScrollEnabled [@RayyanTahir][RayyanTahir] ([#102][102])
- Fix: fixed invalid use of `setContentRect` in README [@r4zzz4k][r4zzz4k] ([#105][105])

<https://github.com/natario1/ZoomLayout/compare/v1.6.0...v1.6.1>

## v1.6.0

- Introduces ZoomSurfaceView, a zoomable and pannable Surface container. Please read docs for usage ([#94][94]).

<https://github.com/natario1/ZoomLayout/compare/v1.5.1...v1.6.0>

#### v1.5.1

- Fix: fix a context leak in ZoomLayout thanks to [@dmazzoni][dmazzoni] ([#92][92]).
- Fix: fix a bug in vertical Alignment thanks to  leak in ZoomLayout thanks to [@asclepix][asclepix] ([#90][90]).
- Enhancement: cancel active fling animations thanks to [@markusressel][markusressel] ([#85][85]).
- Fix: fix Kotlin nullability crashes thanks to [@Sly112][Sly112] ([#83][83]).
- Fix: sources not present in published repo ([#81][81]).


<https://github.com/natario1/ZoomLayout/compare/v1.5.0...v1.5.1>

## v1.5.0

- New: Project source code was fully translated to Kotlin thanks to [@markusressel][markusressel] ([#38][38]).
- New: New APIs to disable fling and flings started when overscrolling: `setFlingEnabled` and `setAllowFlingInOverscroll`,
  thanks to [@markusressel][markusressel] ([#70][70]).
- New: Ability to pan while zooming, thanks to [@markusressel][markusressel] ([#68][68]).
- Enhancement: Prevent entering the scrolling/flinging state if pan is disabled, thanks to [@nil2l][nil2l] ([#79][79]).
- New: New `Alignment` APIs control the alignment of the content with respect to the container. Please read the documentation about them.
  Thanks to [@LRP-sgravel][LRP-sgravel] ([#71][71] and [#76][76])


<https://github.com/natario1/ZoomLayout/compare/v1.4.0...v1.5.0>


[natario1]: https://github.com/natario1
[markusressel]: https://github.com/markusressel
[nil2l]: https://github.com/nil2l
[LRP-sgravel]: https://github.com/LRP-sgravel
[dmazzoni]: https://github.com/dmazzoni
[asclepix]: https://github.com/asclepix
[Sly112]: https://github.com/Sly112
[RayyanTahir]: https://github.com/RayyanTahir
[r4zzz4k]: https://github.com/r4zzz4k
[coffeemakr]: https://github.com/coffeemakr

[38]: https://github.com/natario1/ZoomLayout/pull/38
[70]: https://github.com/natario1/ZoomLayout/pull/70
[68]: https://github.com/natario1/ZoomLayout/pull/68
[79]: https://github.com/natario1/ZoomLayout/pull/79
[71]: https://github.com/natario1/ZoomLayout/pull/71
[76]: https://github.com/natario1/ZoomLayout/pull/76
[81]: https://github.com/natario1/ZoomLayout/pull/81
[83]: https://github.com/natario1/ZoomLayout/pull/83
[85]: https://github.com/natario1/ZoomLayout/pull/85
[90]: https://github.com/natario1/ZoomLayout/pull/90
[92]: https://github.com/natario1/ZoomLayout/pull/92
[94]: https://github.com/natario1/ZoomLayout/pull/94
[97]: https://github.com/natario1/ZoomLayout/pull/97
[102]: https://github.com/natario1/ZoomLayout/pull/102
[105]: https://github.com/natario1/ZoomLayout/pull/105
[114]: https://github.com/natario1/ZoomLayout/pull/114
[136]: https://github.com/natario1/ZoomLayout/pull/136
[157]: https://github.com/natario1/ZoomLayout/pull/157
[164]: https://github.com/natario1/ZoomLayout/pull/164
[166]: https://github.com/natario1/ZoomLayout/pull/166
[167]: https://github.com/natario1/ZoomLayout/pull/167
[159]: https://github.com/natario1/ZoomLayout/pull/159
[158]: https://github.com/natario1/ZoomLayout/pull/158
[156]: https://github.com/natario1/ZoomLayout/pull/156
[193]: https://github.com/natario1/ZoomLayout/pull/193
[189]: https://github.com/natario1/ZoomLayout/pull/189
[214]: https://github.com/natario1/ZoomLayout/pull/214
