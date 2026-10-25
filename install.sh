#!/bin/sh
# Installs VietSnap on a USB-connected Android phone (USB debugging on), picking the right CPU build.
set -e
ADB="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}/platform-tools/adb"
cd "$(dirname "$0")"
"$ADB" wait-for-device
ABI=$("$ADB" shell getprop ro.product.cpu.abi | tr -d '\r')
case "$ABI" in
  arm64-v8a) APK=dist/VietSnap-arm64.apk ;;
  armeabi-v7a|armeabi) APK=dist/VietSnap-arm32.apk ;;
  *) echo "Unsupported CPU type: $ABI"; exit 1 ;;
esac
echo "Phone CPU: $ABI -> installing $APK"
"$ADB" install -r "$APK"
"$ADB" shell monkey -p com.vietsnap -c android.intent.category.LAUNCHER 1 >/dev/null
echo "Installed and launched VietSnap."
