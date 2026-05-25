# PodClassicMod

PodClassicMod is an Android local music player styled after the iPod Classic experience.

PodClassicMod 是一个模仿 iPod Classic 交互体验的 Android 本地音乐播放器。

![PodClassic screenshot](./img/1.jpg)

## Overview / 项目简介

This fork keeps the original retro interaction model and focuses on making the project easier to use and easier to release.

这个 fork 保留了原作的复古交互逻辑，并重点补强了可发布性、可维护性和实际使用体验。

## Downloads / 下载

Latest release / 最新发布:

[v1.5.16](https://github.com/Dora-z/PodClassicMod/releases/tag/v1.5.16)

Included APKs / 当前提供的 APK:

- `PodClassic_launcher_v1.5.16_signed.apk`
- `PodClassic_normal_v1.5.16_signed.apk`

## Builds / 版本说明

Launcher mode:

- Can act as a home launcher.
- Keeps the launcher-oriented behavior enabled in this fork.

Launcher 模式:

- 可以注册为桌面启动器。
- 保留本 fork 中已经完成的 launcher 相关适配。

Normal mode:

- Works like a standard local music player app.
- Does not register as a home launcher.

普通模式:

- 作为常规本地音乐播放器运行。
- 不会注册为桌面启动器。

## Highlights / 主要改动

- Rebuilt CoverFlow to better match the classic layered iTunes style.
- Tuned cover size, centering, overlap, depth, and animation through multiple visual revisions.
- Added stable album and artist text below the centered cover.
- Reduced blank or white covers during fast scrolling by improving preloading behavior.
- Fixed the initial CoverFlow position so the first album opens centered.
- Fixed progress time labels on the now-playing page so they stay on one line.
- Added and maintained a launcher build that can still be released separately.

- 重做 CoverFlow，使其更接近经典 iTunes 的分层透视效果。
- 多轮调整封面大小、居中、重叠、纵深和动画表现。
- 在中间封面下方稳定显示当前专辑名和歌手名。
- 优化快速滚动时的封面预加载，减少空白或白块封面。
- 修复 CoverFlow 首次进入时首张专辑不能正确居中的问题。
- 修复正在播放页面进度条两侧时间换行的问题。
- 重新维护并保留可单独发布的 Launcher 版本。

## Source Defaults / 源码默认状态

The repository is kept in launcher mode by default.

当前仓库源码默认保持为 launcher 模式。

Default switches / 默认开关位置:

- `app/src/main/java/com/example/podclassic/values/Values.kt`
- `app/src/main/AndroidManifest.xml`

In the default source state:

- `Values.LAUNCHER` is `true`
- `AndroidManifest.xml` includes the `HOME` and launcher-related `DEFAULT` categories

默认源码状态下:

- `Values.LAUNCHER` 为 `true`
- `AndroidManifest.xml` 中保留 `HOME` 以及 launcher 相关的 `DEFAULT` 分类

## Build / 构建

Requirements / 需求:

- JDK 17
- Android SDK with Build Tools 34

Build the default launcher release / 构建默认 launcher 版:

```bash
./gradlew assembleRelease
```

Build the normal release / 构建普通版:

1. Set `Values.LAUNCHER = false`
2. Remove or comment out the launcher-only intent categories in `AndroidManifest.xml`
3. Run `./gradlew assembleRelease`

1. 将 `Values.LAUNCHER` 改为 `false`
2. 删除或注释 `AndroidManifest.xml` 中仅 launcher 模式需要的 intent category
3. 运行 `./gradlew assembleRelease`

## Key Files / 关键文件

- `app/src/main/java/com/example/podclassic/view/ComposeCoverFlowView.kt`
- `app/src/main/java/com/example/podclassic/view/CoverFlowNativeView.kt`
- `app/src/main/java/com/example/podclassic/view/MusicPlayerView.kt`
- `app/src/main/java/com/example/podclassic/view/MusicPlayerView3rd.kt`
- `app/src/main/java/com/example/podclassic/values/Values.kt`
- `app/src/main/AndroidManifest.xml`

## Credits / 致谢

Original project / 原始项目:

[0x1317bf7/PodClassic](https://github.com/0x1317bf7/PodClassic)

Maintained fork / 当前 fork:

[Dora-z/PodClassicMod](https://github.com/Dora-z/PodClassicMod)
