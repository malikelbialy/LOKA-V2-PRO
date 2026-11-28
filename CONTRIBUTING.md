# Contributing to VIVA

Thanks for your interest in VIVA (LOKA-V2-PRO).

## Getting set up

1. Install Android Studio (Giraffe or newer) with Android SDK 34.
2. Clone the repo and open it in Android Studio.
3. Let Gradle sync (dependencies: MediaPipe, Camera2, OkHttp, Gson).
4. The hand landmarker model is downloaded automatically by the Gradle
   task `downloadHandLandmarkerModel` on first build. If you prefer, place
   `hand_landmarker.task` manually in `app/src/main/assets/`.

## Building from the command line

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Code style

- Kotlin, 4-space indent, official code style.
- Keep platform code (camera / GL / sensors) under `platform/`.
- Keep app logic (windows / voice / AI / settings) under `logic/`.

## Pull requests

- One feature/fix per PR.
- Describe what changed and why.
- Test on a physical device with Camera2 support if you touch the camera or
  renderer paths.

## License

By contributing you agree that your contributions are licensed under the
project's MIT License.
