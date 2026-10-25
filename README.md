# VietSnap

Point at Vietnamese text, tap the shutter, and read the English.

- **Quick pass (on-device):** ML Kit OCR + offline Vietnamese→English. Rough but fast and works offline.
- **Refined (image model):** the photo goes to OpenRouter; `gemini-3.1-flash-lite` and `claude-haiku-5.5` race and the first to answer wins (~1.2–1.8s). Streams in and replaces the quick pass.
- **History:** every shot is saved with its translation in app-private storage. Tap the thumbnail next to the shutter to browse past shots, open one full-size, or delete it.
- **Words:** each photo's Vietnamese words are added to a vocabulary list with their English meanings (compound words like "hút thuốc" stay together). Tap the book button left of the shutter to see it; long-press a word to remove it.
- Tap the shutter again to retake. Tap the preview to focus. Turn off "On-device quick pass" if your phone is slow enough that it never beats the cloud.

## Install on your phone

**Option A: USB (recommended)**
1. On the phone: Settings → About phone → tap **Build number** 7 times, then Settings → Developer options → enable **USB debugging**.
2. Plug the phone into this Mac and accept the "Allow USB debugging?" prompt.
3. Run `./install.sh`. It detects the phone's CPU and installs the right APK.

**Option B: copy the APK**
1. Copy `dist/VietSnap-arm64.apk` to the phone (or `dist/VietSnap-arm32.apk` for older/budget phones — if the arm64 one says "App not installed", use this one).
2. Open it on the phone and allow "Install unknown apps" for your file manager or browser when asked.

On first launch: allow the camera, and stay online for ~30 seconds while the offline translation model (~30MB) downloads.

## Build

Requires JDK 17 and the Android SDK (`local.properties` points at it). The API key is read from `.env` at build time.

    export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
    ./gradlew assembleRelease          # APKs in app/build/outputs/apk/release/
    ./gradlew connectedDebugAndroidTest  # on-device OCR+translate test (needs a phone or emulator)

**The API key is embedded in the APK.** Don't share the APK publicly; anyone with it can extract the key.
