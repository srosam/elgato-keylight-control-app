# Studio Light Controller

An Android app to discover and control studio panel lights on your local network via mDNS.

## Features

- **Auto-discovery** - Finds lights on your network using mDNS (`_elg._tcp`)
- **Persistent storage** - Remembers your lights across app sessions
- **Offline detection** - Warns when lights become unreachable
- **Power control** - Turn lights on/off
- **Brightness** - Adjust brightness from 0-100%
- **Color temperature** - Adjust from warm (2900K) to cool (7000K)
- **Home screen widget** - Quick toggle without opening the app
- **Material 3 UI** - Modern design that follows your system theme

## Screenshots

*Coming soon*

## Requirements

- Android 12 or later (API 31+)
- Lights must be on the same WiFi network as your phone

## Supported Devices

Works with studio panel lights that use the `_elg._tcp` mDNS service type and REST API on port 9123, including:
- Elgato Key Light
- Elgato Key Light Air
- Elgato Key Light Mini
- Other compatible lights

## Installation

### From APK

1. Download the latest APK from [Releases](../../releases)
2. Enable "Install unknown apps" for your file manager or browser
3. Open the APK and install

### Build from Source

Prerequisites:
- JDK 17+
- Android SDK (API 35)

```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/studio-light-controller.git
cd studio-light-controller

# Build debug APK
./gradlew assembleDebug

# APK will be at: app/build/outputs/apk/debug/app-debug.apk
```

## Usage

### App

1. Launch the app
2. Wait for automatic light discovery (or tap the search button)
3. Tap the power switch to turn a light on/off
4. Use the brightness slider to adjust intensity
5. Use the temperature slider to adjust color warmth

### Widget

1. Open the app first to discover your lights
2. Long-press on your home screen → Widgets
3. Find "Studio Light Controller" and drag to home screen
4. Select which light the widget should control
5. Tap the widget anytime to toggle that light

## API Reference

The app communicates with lights using a simple REST API:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/elgato/lights` | GET | Get current light state |
| `/elgato/lights` | PUT | Set light state |
| `/elgato/accessory-info` | GET | Get device info |

### Light State JSON

```json
{
  "numberOfLights": 1,
  "lights": [{
    "on": 1,
    "brightness": 50,
    "temperature": 200
  }]
}
```

| Field | Range | Description |
|-------|-------|-------------|
| `on` | 0-1 | Power state (0=off, 1=on) |
| `brightness` | 0-100 | Brightness percentage |
| `temperature` | 143-344 | Color temp (143=7000K cool, 344=2900K warm) |

## Architecture

```
app/src/main/java/com/.../
├── MainActivity.kt           # App entry point
├── data/
│   ├── model/Light.kt        # Data models
│   ├── network/
│   │   ├── ElgatoApi.kt      # Retrofit HTTP client
│   │   └── LightDiscoveryService.kt  # mDNS discovery
│   └── repository/
│       ├── LightRepository.kt         # Main data layer
│       └── LightPreferencesRepository.kt  # Persistence
├── ui/
│   ├── theme/Theme.kt        # Material 3 theming
│   ├── screens/HomeScreen.kt # Main UI
│   └── components/LightCard.kt  # Light control card
├── viewmodel/LightViewModel.kt  # State management
└── widget/
    ├── LightWidgetProvider.kt   # Widget logic
    └── LightWidgetConfigureActivity.kt  # Widget setup
```

## Tech Stack

- **Kotlin** - Primary language
- **Jetpack Compose** - UI framework
- **Material 3** - Design system
- **Retrofit** - HTTP client
- **DataStore** - Persistence
- **Android NSD** - mDNS discovery
- **Coroutines + Flow** - Async operations

## License

MIT License - feel free to use, modify, and distribute.

## Contributing

Contributions welcome! Please open an issue or PR.
