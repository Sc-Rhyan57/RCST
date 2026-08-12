# RCST

Simple Android browser app built with Jetpack Compose and WebView.

## Features

- **Home tab** — WebView loading Google (or any configured URL)
- **Settings tab** — theme, Material You, browser options
- **Theme modes** — System / Dark / Amoled / Light
- **Material You** — dynamic colors on Android 12+
- **Crash screen** — automatic crash capture and log display
- **Animated watermark** — RGB cycling "by rhyan57" footer

## Requirements

- Android Studio Ladybug or later
- Kotlin 2.0+
- Android 8.0+ (API 26)

## Build

```bash
./gradlew assembleDebug
```

## Tech stack

- Jetpack Compose + Material 3
- DataStore Preferences
- WebView
- ViewModel + StateFlow
