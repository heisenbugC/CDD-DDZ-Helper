#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/.."

# 1. Determine simulator target
SIMULATOR_TARGET="${1:-booted}"

# Open the Simulator app
open -a Simulator

# If 'booted' was requested, make sure at least one simulator is actually booted
if [ "$SIMULATOR_TARGET" = "booted" ]; then
    BOOTED_CHECK=$(xcrun simctl list devices | grep "(Booted)" | head -n 1 || true)
    if [ -z "$BOOTED_CHECK" ]; then
        echo "==> No booted simulator found. Booting default simulator..."
        # Find first available iPhone simulator
        DEFAULT_SIM=$(xcrun simctl list devices available | grep "iPhone" | head -n 1 | sed -E 's/^[ ]*//;s/ \([A-F0-9-]+\)//' || true)
        if [ -n "$DEFAULT_SIM" ]; then
            xcrun simctl boot "$DEFAULT_SIM" 2>/dev/null || true
        else
            xcrun simctl boot "iPhone 16" 2>/dev/null || true
        fi
    fi
else
    echo "==> Booting simulator: $SIMULATOR_TARGET..."
    xcrun simctl boot "$SIMULATOR_TARGET" 2>/dev/null || true
fi

# 2. Build with xcodebuild
echo "==> Building iOS App for Simulator..."
xcodebuild -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -destination "generic/platform=iOS Simulator" \
  -derivedDataPath build/iosDerivedData \
  CODE_SIGNING_ALLOWED=NO \
  build

APP_PATH="build/iosDerivedData/Build/Products/Debug-iphonesimulator/cdd-ddz-helper.app"
BUNDLE_ID="com.desmond.cddddz"

# 3. Install and launch
echo "==> Installing app onto simulator..."
xcrun simctl install booted "$APP_PATH"

echo "==> Launching $BUNDLE_ID..."
xcrun simctl launch booted "$BUNDLE_ID"

echo "==> App is running in Simulator!"

