#!/usr/bin/env bash
# Build the classes2.dex that is injected into the prebuilt LÖVE 11.5 / 0.10.2
# engine packs. Requires a JDK, an Android platform android.jar and d8.
set -euo pipefail

: "${ANDROID_JAR:=${ANDROID_HOME:-/opt/android-sdk}/platforms/android-35/android.jar}"
: "${D8:=${ANDROID_HOME:-/opt/android-sdk}/build-tools/36.0.0/d8}"

here="$(cd "$(dirname "$0")" && pwd)"
cd "$here"

rm -rf build/classes build/dex build/classes2.dex
mkdir -p build/classes build/dex

javac -source 8 -target 8 -Xlint:-options \
    -classpath "$ANDROID_JAR" \
    -d build/classes \
    $(find src -name '*.java')

"$D8" --min-api 16 --lib "$ANDROID_JAR" \
    --output build/dex \
    $(find build/classes -name '*.class')

cp build/dex/classes.dex build/classes2.dex
echo "built $here/build/classes2.dex"
