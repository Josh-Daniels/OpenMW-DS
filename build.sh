#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

rm -f local.properties

docker run --rm -it --platform linux/amd64 \
  -e ALPHA3_DOCKER=1 \
  -v "$PWD":/workspace \
  -v alpha3-gradle:/root/.gradle \
  -w /workspace alpha3-builder \
  bash -lc "chmod +x ./gradlew && ./gradlew assembleDebug --no-daemon"