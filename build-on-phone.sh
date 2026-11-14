#!/system/bin/sh
# Build the TTBS-HRMS debug APK from AndroidIDE's Terminal tab.
# Usage:  sh build-on-phone.sh
set -u

cd "$(dirname "$0")" 2>/dev/null || cd .
echo "Project: $(pwd)"

if [ ! -f settings.gradle ] || [ ! -f build.gradle ]; then
  echo ""
  echo "ERROR: this is not the project root."
  echo "Open the folder that directly contains settings.gradle, build.gradle and app/"
  exit 1
fi

GRADLE="$(command -v gradle 2>/dev/null || true)"
if [ -z "$GRADLE" ]; then
  for c in "$HOME"/gradle-*/bin/gradle \
           "$HOME"/.gradle/wrapper/dists/*/*/gradle-*/bin/gradle \
           /data/data/com.itsaky.androidide/files/home/gradle-*/bin/gradle \
           /data/user/0/com.itsaky.androidide/files/home/gradle-*/bin/gradle; do
    if [ -x "$c" ]; then GRADLE="$c"; break; fi
  done
fi

if [ -z "$GRADLE" ]; then
  echo ""
  echo "gradle was not found on this phone. Diagnostics:"
  echo "  HOME=$HOME"
  echo "  PATH=$PATH"
  echo "--- contents of HOME ---"
  ls -1 "$HOME" 2>/dev/null | head -30
  echo "--- searching for a gradle binary ---"
  find /data /storage /system -maxdepth 7 -name gradle -type f 2>/dev/null | head -5
  exit 1
fi

echo "Using gradle: $GRADLE"
echo ""

"$GRADLE" :app:assembleDebug --no-daemon 2>&1 | tail -60

APK="app/build/outputs/apk/debug/app-debug.apk"
echo ""
if [ -f "$APK" ]; then
  echo "=== APK READY ==="
  echo "$(pwd)/$APK"
  ls -l "$APK"
else
  echo "=== No APK produced. The lines above say why. ==="
fi
