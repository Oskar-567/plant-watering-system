# PlantWatch App

React Native mobile app for the PlantWatch plant watering system. Communicates with the Spring Boot server via REST.

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Expo SDK 56 (Managed Workflow) |
| React Native | 0.85.3 |
| Routing | expo-router v3 (file-based) |
| State | Zustand 5 |
| Auth storage | expo-secure-store (JWT) |
| Charts | react-native-svg (custom SVG) |
| Fonts | Manrope + Space Grotesk |

## Prerequisites

- Node.js 18+
- Android Studio (for Android builds)
- CMake 4.1.2+ installed via Android Studio SDK Manager
- Windows long path support enabled (one-time, run as admin):
  ```powershell
  Set-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\FileSystem" -Name "LongPathsEnabled" -Value 1
  ```

## Setup

```bash
npm install
```

## Building for Android

### First-time setup

Generate the native Android project:
```bash
npx expo prebuild --platform android
```

### Build APK

Open the `android/` folder in Android Studio, then:

```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```

APK output: `android/app/build/outputs/apk/debug/app-debug.apk`

### Install on device

```bash
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

## Important Notes

- **Do NOT upgrade Gradle** when Android Studio prompts — Gradle 9.6.0 breaks the build. Prebuild generates the correct version (9.3.1).
- The `android/` folder is fully generated and can be deleted and regenerated at any time.
- After `npm install`, re-apply the Kotlin compiler patches in node_modules (see `AGENTS.md` for details).

## Screens

| Screen | Route |
|---|---|
| Dashboard | `/(tabs)` |
| Instance detail | `/instance/[id]` |
| New instance | `/instance/new` |
| Settings | `/(tabs)/settings` |
| Login | `/(auth)/login` |
