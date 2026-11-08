# Demo - Kotlin Multiplatform App

This is a Kotlin Multiplatform project targeting Android, iOS, Web (JS/Wasm), and Desktop (JVM), with **AGP 9.0+ compatibility**.

## Project Structure

### Module Organization

**composeApp** - Kotlin Multiplatform Library
- [/composeApp/src/commonMain](./composeApp/src/commonMain/kotlin) - Shared Compose UI code for all targets
- [/composeApp/src/androidMain](./composeApp/src/androidMain/kotlin) - Android-specific implementations
- [/composeApp/src/iosMain](./composeApp/src/iosMain/kotlin) - iOS-specific implementations
- [/composeApp/src/jvmMain](./composeApp/src/jvmMain/kotlin) - Desktop (JVM) specific code
- [/composeApp/src/jsMain](./composeApp/src/jsMain/kotlin) - Web (JavaScript) specific code

**androidApp** - Android Application Module (NEW)
- [/androidApp/src/androidMain](./androidApp/src/androidMain/kotlin) - Android app entry point (MainActivity)
- Depends on composeApp library
- Pure Android Gradle plugin (AGP) compatible

**iosApp** - iOS Application
- [/iosApp](./iosApp) - Contains iOS applications with SwiftUI integration

### Project Architecture (AGP 9.0+ Compatible)

This project follows the **recommended AGP 9.0+ compatible structure**:
- `composeApp` is a **KMP Library** using `com.android.library` plugin
- `androidApp` is a **pure Android Application** using `com.android.application` plugin
- `androidApp` depends on `composeApp` as a library

This separation ensures **future compatibility with AGP 9.0.0+** while supporting current AGP 8.13.2.

## Building the Project

### Prerequisites
- Java 11 or higher
- Gradle 8.14+ (included via wrapper)

### Build All Targets

```bash
# macOS / Linux
./gradlew clean build

# Windows
.\gradlew.bat clean build
```

### Android Application

**Build Debug APK:**
```bash
./gradlew :androidApp:assembleDebug     # macOS/Linux
.\gradlew.bat :androidApp:assembleDebug # Windows
```

**Build Release APK:**
```bash
./gradlew :androidApp:assembleRelease     # macOS/Linux
.\gradlew.bat :androidApp:assembleRelease # Windows
```

**Install and run on device/emulator:**
```bash
./gradlew :androidApp:installDebug     # macOS/Linux
.\gradlew.bat :androidApp:installDebug # Windows
```

### Desktop (JVM) Application

**Build and run desktop app:**
```bash
./gradlew :composeApp:run
```

### iOS Application (SwiftUI)

#### Option 1: Terminal (CLI)
You can build, install, and run on the iOS Simulator directly using the helper script:
```bash
./scripts/run_ios.sh
```
Or specify a specific simulator:
```bash
./scripts/run_ios.sh "iPhone 16 Pro"
```

Or run the manual commands:
```bash
# 1. Boot simulator & open Simulator app
open -a Simulator
xcrun simctl boot "iPhone 16 Pro" 2>/dev/null || true

# 2. Build for simulator
xcodebuild -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath build/iosDerivedData \
  CODE_SIGNING_ALLOWED=NO \
  build

# 3. Install & launch
xcrun simctl install booted build/iosDerivedData/Build/Products/Debug-iphonesimulator/cdd-ddz-helper.app
xcrun simctl launch booted com.desmond.cddddz
```

#### Option 2: Xcode GUI
Open `iosApp/iosApp.xcodeproj` in Xcode:
1. Select the `iosApp` scheme.
2. Select an iOS Simulator (e.g. iPhone 16 Pro) or your connected physical iPhone.
3. In **Signing & Capabilities**, ensure your Apple Developer account / Team is selected if running on a real device.
4. Press **⌘R** (or click the Run button) to build and run.

> **Note:** The iOS app is written natively in SwiftUI (`iosApp/iosApp`) and does not depend on the Gradle Kotlin framework compilation step. `OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED = YES` is configured in `Config.xcconfig` so that builds succeed cleanly without invoking Gradle.

## Project Configuration

| Setting | Value |
|---------|-------|
| **AGP Version** | 8.13.2 (compatible with 9.0.0+) |
| **Kotlin Version** | 2.3.0 |
| **Compose Multiplatform** | 1.10.0 |
| **Android Min SDK** | 24 |
| **Android Target SDK** | 36 |
| **Gradle Version** | 8.14.3 |

## About `composeApp [jvm]`

The **[jvm]** variant in composeApp refers to the **Desktop (JVM) Application target**:
- Compiled to JVM bytecode for desktop platforms (Windows, macOS, Linux)
- Uses Compose for Desktop for native UI rendering
- Shares all common code with other targets (Android, iOS, Web)
- Can run standalone as a native desktop application

The JVM target provides a **full native desktop experience** while sharing the exact same Compose UI code with mobile platforms.

## AGP Version Resolution

### Previous Issue
The project previously used a single `composeApp` module that applied both:
- `com.android.application` plugin (for Android app)
- `org.jetbrains.kotlin.multiplatform` plugin

This is **deprecated and incompatible with AGP 9.0.0+**.

### Solution Implemented
1. ✅ Converted `composeApp` to a **KMP Library** using `com.android.library`
2. ✅ Created new `androidApp` module as a **pure Android Application**
3. ✅ Made `androidApp` depend on `composeApp` library
4. ✅ Increased Kotlin daemon memory in `gradle.properties`

This structure is **fully compatible with AGP 9.0.0+** and follows JetBrains' official recommendations.

## Further Learning

- [Kotlin Multiplatform Documentation](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)
- [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/)
- [AGP Kotlin Multiplatform Migration Guide](https://kotl.in/gradle/agp-new-kmp)
- [Android Gradle Plugin Release Notes](https://developer.android.com/build/releases/gradle-plugin)

