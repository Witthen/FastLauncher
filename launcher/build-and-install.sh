#!/usr/bin/env bash
# Compila FastLauncher e instálalo como pantalla de inicio.
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
STOCK_LAUNCHER="${STOCK_LAUNCHER:-com.google.android.tvlauncher}"
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17)}"
export ANDROID_HOME="${ANDROID_HOME:-$([ -d ~/Library/Android/sdk ] && echo ~/Library/Android/sdk || echo /opt/homebrew/share/android-commandlinetools)}"
export PATH="$JAVA_HOME/bin:$PATH"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug -q --console=plain
PY=../tools/venv/bin/python
[ -x "$PY" ] || PY=../tools/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
APK=app/build/outputs/apk/debug/app-debug.apk
"$PY" -I ../tools/b64push.py "$APK" /data/local/tmp/fastlauncher.apk
printf '%s\n' \
  "pm install -r /data/local/tmp/fastlauncher.apk" \
  "rm -f /data/local/tmp/fastlauncher.apk" \
  "pm grant com.fastlauncher.tv android.permission.ACCESS_FINE_LOCATION 2>/dev/null; echo location-granted" \
  "cmd package set-home-activity --user 0 com.fastlauncher.tv/.MainActivity" \
  "pm disable-user --user 0 $STOCK_LAUNCHER" \
  | "$PY" -I ../tools/tv.py --batch
echo "Instalado. Pulsa Inicio en el mando."
