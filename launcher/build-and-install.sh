#!/usr/bin/env bash
# En Windows usa ../instalar.ps1. Aquí: TV_HOST=192.168.1.50 ./build-and-install.sh
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?Define TV_HOST, ej. TV_HOST=192.168.1.50 $0}"
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
[ -x "$PY" ] || { echo "python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt"; exit 1; }
"$PY" -I tools/tv.py --check
cd launcher
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17)}"
export ANDROID_HOME="${ANDROID_HOME:-$([ -d ~/Library/Android/sdk ] && echo ~/Library/Android/sdk || echo "$HOME/Android/Sdk")}"
export PATH="$JAVA_HOME/bin:$PATH"
echo "sdk.dir=$ANDROID_HOME" > local.properties
CFG=app/src/main/res/values/config.xml
BAK=config-perfil.bak.xml
if [ -n "${FASTLAUNCHER_PERFIL:-}" ]; then
  PCFG="../perfiles/${FASTLAUNCHER_PERFIL}/res/values/config.xml"
  cp "$CFG" "$BAK"
  cp "$PCFG" "$CFG"
fi
./gradlew assembleDebug --console=plain || { [ -f "$BAK" ] && mv "$BAK" "$CFG"; exit 1; }
[ -f "$BAK" ] && mv "$BAK" "$CFG"
cd ..
"$PY" -I tools/tv.py --batch <<'EOF'
settings put global verifier_verify_adb_installs 0
settings put global package_verifier_enable 0
EOF
"$PY" -I tools/tv.py --push launcher/app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/fastlauncher.apk
"$PY" -I tools/tv.py --batch <<EOF
pm install -r -t --user 0 /data/local/tmp/fastlauncher.apk
rm -f /data/local/tmp/fastlauncher.apk
pm grant com.fastlauncher.tv android.permission.ACCESS_FINE_LOCATION
appops set com.fastlauncher.tv GET_USAGE_STATS allow
cmd notification allow_listener com.fastlauncher.tv/.SessionListener
cmd package set-home-activity --user 0 com.fastlauncher.tv/.MainActivity
pm disable-user --user 0 ${STOCK_LAUNCHER:-com.google.android.tvlauncher}
EOF
echo "Instalado. Pulsa Inicio en el mando."
