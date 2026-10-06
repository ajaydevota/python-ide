# Python IDE (Android)

Offline Python code editor + runner for Android.

## Features
- **Editor:** Sora Editor + TextMate Python grammar (syntax highlighting)
- **Runner:** Chaquopy — a real Python 3.12 inside the app (offline)
- **Output:** Console (text) + Graphics (matplotlib -> PNG), auto-detected
- **Navigation:** Home -> Editor -> Output (separate screens); top-left menu for Settings / Install / Linux
- **Install:** runtime pip (pure-Python packages) + GitHub clone (zip download)
- **Linux (proot):** proot + Alpine Linux — run `apk add python3 py3-numpy` etc.

## Get a ready APK (GitHub Actions)
1. Open the **Actions** tab -> **Build APK** -> **Run workflow**.
2. When it finishes, download the **app-debug-apk** artifact.
3. Install it on your phone (enable "Install unknown apps").

This APK is built with **numpy** and **matplotlib** bundled.

## Build locally
```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
gradle :app:assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

## Notes
- The workflow downloads the `proot` binaries and the Alpine rootfs, and generates the
  launcher icons, before building. So this repo stays text-only.
- To add more Python packages at build time, edit `app/build.gradle.kts`:
  `chaquopy { defaultConfig { pip { install("pandas") } } }`
