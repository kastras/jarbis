#!/usr/bin/env bash
set -euo pipefail

# Descarga los modelos Vosk (offline) y los deja listos para el asset de la app.
# Modelos: small es+en (~35 MB cada uno). Licencia Apache-2.0.
# URL oficial: https://alphacephei.com/vosk/models

ASSETS_DIR="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets"
mkdir -p "$ASSETS_DIR"

BASE="https://alphacephei.com/vosk/models"
MODELS=(
  "vosk-model-small-es-0.42.zip"
  "vosk-model-small-en-us-0.15.zip"
)

for m in "${MODELS[@]}"; do
  if [ -f "$ASSETS_DIR/$m" ]; then
    echo "ok: $m ya existe"
    continue
  fi
  echo "download: $m ..."
  curl -fL --retry 3 -o "$ASSETS_DIR/$m" "$BASE/$m"
  echo "ok: $m"
done
echo "Modelos listos en $ASSETS_DIR"