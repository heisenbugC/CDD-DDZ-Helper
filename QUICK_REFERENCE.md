# Quick Reference Guide

## 🚀 Quick Start

### Build Everything
```bash
.\gradlew.bat clean assemble
```

### Run on Android
```bash
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :androidApp:installDebug
```

### Run Desktop App
```bash
.\gradlew.bat :composeApp:run
```

### Run Web App (Modern)
```bash
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
```

---

## 📁 Project Structure at a Glance

```
composeApp/              ← Kotlin Multiplatform Library (for all targets)
  ├── commonMain/        ← Shared Compose UI code
  ├── androidMain/       ← Android utilities
  ├── jvmMain/           ← Desktop app code
  ├── iosMain/           ← iOS utilities
  └── jsMain/            ← Web utilities

androidApp/             ← Android Application ONLY
  └── MainActivity.kt    ← Entry point

iosApp/                 ← iOS app (Xcode project)
```

---

## ✅ What Was Fixed

| Issue | Solution |
|-------|----------|
| AGP incompatibility | Split into composeApp (library) + androidApp (app) |
| `composeApp [jvm]` unclear | It's the Desktop/JVM target |
| Build errors | Resolved dependency issues & AGP structure |

---

## 🎯 Dialog Types in Demo App

1. **Alert Dialog** - Information with OK button
2. **Confirmation Dialog** - Yes/No decision
3. **Choice Dialog** - Single selection from list
4. **Input Dialog** - Text input field
5. **Custom Dialog** - Display current form state
6. **Progress Dialog** - Loading progress animation

---

## 📊 Target Platforms

| Platform | Module | Command | Output |
|----------|--------|---------|--------|
| Android | androidApp | `assembleDebug` | APK |
| Desktop | composeApp | `run` | Native App |
| Web | composeApp | `wasmJsBrowserDevelopmentRun` | Browser |
| iOS | iosApp | Xcode | Native App |

---

## 🔧 Configuration

- **AGP:** 8.13.2 (→ 9.0.0+ compatible)
- **Kotlin:** 2.3.0
- **Compose:** 1.10.0
- **Android Target:** SDK 36, Min 24
- **Gradle Daemon:** 4GB RAM

---

## 📝 Key Files Modified

- `composeApp/build.gradle.kts` - Changed to library
- `androidApp/build.gradle.kts` - Created new
- `settings.gradle.kts` - Added androidApp module
- `gradle.properties` - Increased memory
- `App.kt` - Enhanced demo

---

## 💡 Tips

- Use `./gradlew.bat` on Windows, `./gradlew` on Mac/Linux
- Add `--info` flag for verbose output
- Use `clean` to force full rebuild
- Check `build/reports/` for detailed error reports

---

## 🔗 Learn More

- **AGP & KMP:** https://kotl.in/gradle/agp-new-kmp
- **Compose Multiplatform:** https://github.com/JetBrains/compose-multiplatform/
- **Documentation:** See PROJECT_SUMMARY.md and BUILD_AND_RUN.md

