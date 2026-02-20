# Manga Auto-Scroller (MVP)

Drop these files into a new empty Compose project (module: `app`) in Android Studio.

- Paste the contents of `app/build.gradle` into your module `build.gradle` (or adapt to your build setup).
- Add the `AndroidManifest.xml` under `src/main`.
- Place `MainActivity.kt` and `MangaScrollerApp.kt` under `src/main/java/com/example/mangascroller/`.

Usage:

1. Open the app, select a folder (a chapter as a folder of image files).
2. Tap anywhere to start/pause auto-scroll.
3. Adjust speed with the slider.

Notes:
- Bookmarks (last scroll position) are kept in-memory per folder for the session.
- This is intentionally minimal — see user suggestions for next features.
