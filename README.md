# Endoscope

Endoscope is an Android application for USB endoscopes and other UVC-compatible cameras. Version **0.12.0** provides a focused portrait interface for inspection work while keeping the existing internal package name, `com.alyx.endoscope`, so installed versions can be upgraded without losing user settings.

## Features

- Live preview from a USB UVC endoscope or camera.
- Photo capture and video recording without audio.
- Support for compatible physical capture buttons on endoscope handles.
- Four output formats: **16:9**, **3:2**, **4:3**, and **1:1**.
- High-quality 1280 × 720 camera input with centered, distortion-free cropping for narrower formats.
- Manual real-time radial distortion correction for reducing the fisheye effect.
- Two selectable interfaces: **Atelier** and **Workshop**.
- Yellow comments, date, and time embedded directly into photos and videos.
- A dedicated Endoscope gallery with editable comments.
- A user-selectable destination folder for captured media.
- Persistent user preferences across application restarts and upgrades.
- Automatic maximum screen brightness while the application is open, followed by restoration of the previous brightness.
- Light and dark system theme support.
- Portrait orientation lock.
- Support for `armeabi-v7a` and `arm64-v8a` devices.

## Requirements

- Android 9 (API 28) or later.
- A phone or tablet with USB OTG support.
- A UVC-compatible USB endoscope or camera.

## Build

The project requires Android Studio, JDK 17, and Android SDK 36.

Build a debug APK on Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

Build a release APK:

```powershell
.\gradlew.bat :app:assembleRelease
```

Generated APK files are placed under `app/build/outputs/apk/`.

## Project origin

Endoscope is based on the open-source [AndroidUSBCamera](https://github.com/jiangdongguo/AndroidUSBCamera) project by [jiangdongguo](https://github.com/jiangdongguo). This application retains and adapts part of its USB/UVC camera foundation for a dedicated endoscope workflow.

Thanks to the original author and contributors for their work.

## License

This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.
