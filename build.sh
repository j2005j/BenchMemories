#!/usr/bin/env bash
# Builds jbenchmark.apk — JBenchmark 1.1.1 baked into a native Android app
# (original jar classes dexed unmodified + hand-written MIDP shim).
# Requires: JDK 17+, ANDROID_HOME with platforms/android-34 and build-tools;34.0.0
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_HOME="${ANDROID_HOME:?set ANDROID_HOME}"
BT="$ANDROID_HOME/build-tools/34.0.0"
ANDROID_JAR="$ANDROID_HOME/platforms/android-34/android.jar"
[ -f "$ANDROID_JAR" ] || { echo "missing $ANDROID_JAR"; exit 1; }

SRC=app/src/main/java
MANIFEST=app/src/main/AndroidManifest.xml
RES=app/src/main/res
ASSETS=app/src/main/assets
JAR="$ASSETS/JBenchmark.jar"
if [ ! -f "$JAR" ]; then
    # The original JBenchmark.jar is NOT redistributed with this repo
    # (it is (c) 2005 Kishonti LP). Fetch it from the public archive:
    echo "[0/7] fetching JBenchmark.jar from the simbiansattic archive"
    mkdir -p "$ASSETS"
    curl -fsSL --max-time 120 \
        -o "$JAR" \
        "https://files.simbiansattic.lt/downloads/JBtools/JB/JBenchmark.jar"
fi
[ -f "$JAR" ] || { echo "missing $JAR (download failed?)"; exit 1; }
OUT=build
rm -rf "$OUT"
mkdir -p "$OUT/classes"

echo "[1/7] aapt2 compile (resources)"
"$BT/aapt2" compile --dir "$RES" -o "$OUT/res.zip"

echo "[2/7] aapt2 link (manifest + resources + assets)"
"$BT/aapt2" link \
    -o "$OUT/base.apk" \
    -I "$ANDROID_JAR" \
    -A "$ASSETS" \
    --manifest "$MANIFEST" \
    --min-sdk-version 16 \
    --target-sdk-version 22 \
    --auto-add-overlay \
    "$OUT/res.zip"

echo "[3/7] javac (shim + launcher against the baked JBenchmark.jar)"
find "$SRC" -name '*.java' > "$OUT/sources.txt"
javac \
    -source 8 -target 8 \
    -encoding UTF-8 \
    -bootclasspath "$ANDROID_JAR" \
    -cp "$JAR" \
    -nowarn \
    -d "$OUT/classes" \
    @"$OUT/sources.txt"

echo "[4/7] d8 (our classes + UNMODIFIED JBenchmark.jar classes -> one dex)"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" \
    --release \
    --min-api 16 \
    --lib "$ANDROID_JAR" \
    --output "$OUT" \
    $(cat "$OUT/classes.txt") \
    "$JAR"

echo "[5/7] add classes.dex"
(cd "$OUT" && zip -q base.apk classes.dex)

echo "[6/7] zipalign"
"$BT/zipalign" -f -p 4 "$OUT/base.apk" "$OUT/aligned.apk"

echo "[7/7] sign"
if [ ! -f keystore.jks ]; then
    keytool -genkeypair -keystore keystore.jks -storepass android \
        -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 \
        -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi
"$BT/apksigner" sign \
    --ks keystore.jks --ks-pass pass:android --key-pass pass:android \
    --out "$OUT/jbenchmark.apk" \
    "$OUT/aligned.apk"
"$BT/apksigner" verify --verbose "$OUT/jbenchmark.apk" | head -5

echo
echo "APK: $PWD/$OUT/jbenchmark.apk"
ls -la "$OUT/jbenchmark.apk"
