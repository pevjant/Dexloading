#!/bin/bash

# Build Plugin APK
# This script builds the plugin module as a full APK that can be loaded
# dynamically by the host app, including all resources (drawable, layout, values).

set -e

echo "========================================"
echo "Building Dynamic Loading Plugin APK"
echo "========================================"

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

# Step 1: Build plugin module
echo ""
echo "Step 1: Building plugin APK..."
./gradlew :plugin:assembleRelease --no-daemon

# Step 2: Copy APK to app assets
echo ""
echo "Step 2: Copying APK to app assets..."

BUILD_DIR="$PROJECT_DIR/plugin/build"
APK_FILE="$BUILD_DIR/outputs/apk/release/plugin-release-unsigned.apk"

# Check if APK exists
if [ ! -f "$APK_FILE" ]; then
    # Try alternative name (signed APK)
    APK_FILE="$BUILD_DIR/outputs/apk/release/plugin-release.apk"
fi

if [ ! -f "$APK_FILE" ]; then
    echo "Error: Plugin APK not found at expected location"
    echo "Looked for: $BUILD_DIR/outputs/apk/release/plugin-release*.apk"
    exit 1
fi

echo "Found APK: $APK_FILE"

# Copy to app assets
ASSETS_DIR="$PROJECT_DIR/app/src/main/assets"
mkdir -p "$ASSETS_DIR"
cp "$APK_FILE" "$ASSETS_DIR/plugin.apk"

echo ""
echo "========================================"
echo "Build Complete!"
echo "========================================"
echo ""
echo "Plugin APK location: $APK_FILE"
echo "Copied to: $ASSETS_DIR/plugin.apk"
echo ""
echo "The plugin APK includes:"
echo "  - res/drawable/ (images, shapes)"
echo "  - res/layout/ (XML layouts)"
echo "  - res/values/ (colors, strings)"
echo "  - assets/ (raw files)"
echo ""
echo "Next steps:"
echo "1. Build and run the app: ./gradlew :app:installDebug"
echo "2. Click 'Load Plugin' button to test"
echo ""
