#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

./gradlew :app:assembleDebug --console=plain

APK="app/build/outputs/apk/debug/app-debug.apk"
OUT="jarbis-debug.apk"
cp "$APK" "$OUT"

echo ""
echo "APK generado: $(pwd)/$OUT"
if adb get-state >/dev/null 2>&1; then
  echo "Dispositivo detectado — instalando…"
  adb install -r "$OUT"
else
  echo "Sin dispositivo conectado. Instala en el móvil con:"
  echo "  adb install -r \"$(pwd)/$OUT\""
fi