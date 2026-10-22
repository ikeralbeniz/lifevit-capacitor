# Lifevit example app

A minimal Capacitor app (no framework) to test the `lifevit-capacitor` plugin on
a real device. Implements the full flow: permissions on launch, a device list,
pair‑by‑measurement, and a measurement sheet with live values, a pulse waveform,
timeout and cancel.

> The native platform folders (`android/`, `ios/`) are **not** committed — they
> are generated with `npx cap add`. Follow the setup below.

## Requirements

- Node, and the plugin built first: from the repo root run `npm install && npm run build`.
- **Android:** Android Studio + a device. JDK 21+ (Capacitor 8 compiles with
  `release 21`).
- **iOS:** Xcode + an iPhone. The Lifevit iOS SDK comes from the git submodule, so
  clone the repo with `--recurse-submodules` (or run
  `git submodule update --init --recursive`).

## Setup

```bash
cd example
npm install
```

### Android

```bash
npx cap add android
```

Add the Lifevit Maven repository to `android/build.gradle` (project level):

```gradle
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url "https://raw.githubusercontent.com/lifevit/android-sdk/master" }
    }
}
```

Then:

```bash
npx cap sync android
npx cap run android
```

### iOS

```bash
npx cap add ios
```

In `ios/App/App/Info.plist` add:

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>Connect to your Lifevit health devices over Bluetooth.</string>
```

Open the project and set your signing **Team** in Xcode (Signing & Capabilities):

```bash
npx cap sync ios
npx cap open ios   # select your iPhone, then Run
```

> The bundled Lifevit `xcframework` ships only a device slice (arm64) plus a
> legacy i386/x86_64 simulator slice — so it won't link on an Apple‑Silicon
> **simulator**. Test on a **physical iPhone** (which is where BLE runs anyway).

## How to test

1. Tap **Pedir permisos** / accept the Bluetooth (and, on Android, Location)
   prompts. Keep Bluetooth and Location services on.
2. **Añadir dispositivo** → pick the type (e.g. Tensiómetro) → power on the
   device / press its button so it advertises → it is detected and saved.
3. Tap the saved device → follow the on‑screen prompt to start the measurement.
   - Blood‑pressure: the cuff inflates and shows systolic / diastolic / pulse.
   - Oximeter: live SpO₂ / pulse / PI / RPM and a pulse waveform; remove the
     finger to finalize.
