#!/bin/bash
set -e

# ============================================================
# Build FoldDeck APK without Gradle - Android SDK build-tools
# ============================================================

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
ANDROID_SDK="/Users/keilz/Library/Android/sdk"
BUILD_TOOLS="$ANDROID_SDK/build-tools/33.0.1"
ANDROID_JAR="$ANDROID_SDK/platforms/android-34/android.jar"
APP_DIR="$PROJECT_DIR/app"
RES_DIR="$APP_DIR/src/main/res"
SRC_DIR="$APP_DIR/src/main/java"
MANIFEST="$APP_DIR/src/main/AndroidManifest.xml"

BUILD_DIR="$PROJECT_DIR/build"
GEN_DIR="$BUILD_DIR/gen"
CLASSES_DIR="$BUILD_DIR/classes"
DEX_DIR="$BUILD_DIR/dex"

AAPT2="$BUILD_TOOLS/aapt2"
D8="$BUILD_TOOLS/d8"
APKSIGNER="$BUILD_TOOLS/apksigner"
ZIPALIGN="$BUILD_TOOLS/zipalign"

OUTPUT_APK="$PROJECT_DIR/fold-deck.apk"

echo "=========================================="
echo "  Building FoldDeck APK (no Gradle)"
echo "=========================================="

rm -rf "$BUILD_DIR"
mkdir -p "$GEN_DIR" "$CLASSES_DIR" "$DEX_DIR"

echo "[1/7] Compiling resources..."
"$AAPT2" compile --dir "$RES_DIR" -o "$BUILD_DIR/compiled_resources.zip"
echo "  ✓ Resources compiled"

echo "[2/7] Linking resources and generating R.java..."
"$AAPT2" link \
  -o "$BUILD_DIR/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$MANIFEST" \
  --java "$GEN_DIR" \
  --min-sdk-version 28 \
  --target-sdk-version 34 \
  --version-code 1 \
  --version-name "1.0" \
  --auto-add-overlay \
  "$BUILD_DIR/compiled_resources.zip"
echo "  ✓ Resources linked"

echo "[3/7] Compiling Java sources..."
find "$SRC_DIR" "$GEN_DIR" -name "*.java" -type f -exec \
  javac -classpath "$ANDROID_JAR" -source 1.8 -target 1.8 -d "$CLASSES_DIR" {} + 2>&1
echo "  ✓ Java compiled"

echo "[4/7] Converting to DEX..."
find "$CLASSES_DIR" -name "*.class" -type f -exec \
  "$D8" --release --lib "$ANDROID_JAR" --min-api 28 --output "$DEX_DIR" {} + 2>&1
echo "  ✓ DEX created"

echo "[5/7] Adding DEX to APK..."
cp "$BUILD_DIR/base.apk" "$BUILD_DIR/unsigned.apk"
(cd "$DEX_DIR" && zip -j "$BUILD_DIR/unsigned.apk" classes.dex)
echo "  ✓ DEX added"

echo "[6/7] Aligning APK..."
if [ -f "$ZIPALIGN" ]; then
  "$ZIPALIGN" -f 4 "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/aligned.apk"
  cp "$BUILD_DIR/aligned.apk" "$BUILD_DIR/final.apk"
else
  cp "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/final.apk"
fi
echo "  ✓ APK aligned"

echo "[7/7] Signing APK..."
KEYSTORE="$PROJECT_DIR/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkey -v -keystore "$KEYSTORE" -alias androiddebugkey \
    -dname "CN=Android Debug,O=Android,C=US" \
    -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 2>&1
fi

"$APKSIGNER" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$OUTPUT_APK" \
  "$BUILD_DIR/final.apk" 2>&1
echo "  ✓ APK signed"

echo ""
echo "=========================================="
echo "  Build Complete!"
echo "=========================================="
ls -lh "$OUTPUT_APK"
"$APKSIGNER" verify --verbose "$OUTPUT_APK" 2>&1 || true
