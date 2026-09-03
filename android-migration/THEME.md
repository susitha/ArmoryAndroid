# Color Theme — Android Port

Extracted directly from the iOS app so the Android version matches exactly. Source
files: [`Utils/Theme.swift`](../armoryapp/ArmoryApp/ArmoryApp/Utils/Theme.swift),
[`Utils/Globals.swift`](../armoryapp/ArmoryApp/ArmoryApp/Utils/Globals.swift), and
hex values found inline across `Controllers/` and `Views/`.

## Palette

| Name              | Hex       | Used for (iOS)                                                              |
|-------------------|-----------|-------------------------------------------------------------------------------|
| `primary`         | `#496BB3` | Screen backgrounds, nav bar tint, primary text/icon accent, tertiary button bg |
| `secondary`       | `#2E4163` | Selected state on inventory breakdown buttons                                |
| `primaryButton`   | `#FFC397` | Primary CTA button background (e.g. main action buttons)                     |
| `secondaryButton` | `#708FFC` | Secondary button background                                                  |
| `disabledButton`  | `#6483C4` | Disabled state for primary buttons (login, checkin)                          |
| `menuButton`      | `#5879BD` | Dashboard menu button background                                             |
| `linkText`        | `#0443A2` | Login screen label text                                                      |
| `infoBackground`  | `#EDF5FF` | Light background behind dashboard info items                                 |
| `white`           | `#FFFFFF` | Button/label text on colored backgrounds, tertiary button border             |

### Status colors (from `ScanState.color` in `Globals.swift`)

These use iOS system colors (light-mode default values shown) for RFID scan status:

| State                        | iOS system color | Hex equivalent |
|-------------------------------|-------------------|-----------------|
| Device connected / Success    | `systemGreen`     | `#34C759`       |
| Device not connected          | `systemGray`      | `#8E8E93`       |
| Low battery                   | `systemOrange`    | `#FF9500`       |
| Scan failed / Overheated      | `systemRed`       | `#FF3B30`       |
| Scanning                      | `primary`         | `#496BB3`       |

## Android `colors.xml`

Drop this into `res/values/colors.xml` in the new Android project:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Core theme -->
    <color name="primary">#496BB3</color>
    <color name="secondary">#2E4163</color>

    <!-- Buttons -->
    <color name="primary_button">#FFC397</color>
    <color name="secondary_button">#708FFC</color>
    <color name="disabled_button">#6483C4</color>
    <color name="menu_button">#5879BD</color>

    <!-- Text / misc -->
    <color name="link_text">#0443A2</color>
    <color name="info_background">#EDF5FF</color>
    <color name="white">#FFFFFF</color>

    <!-- RFID scan status -->
    <color name="status_connected">#34C759</color>
    <color name="status_disconnected">#8E8E93</color>
    <color name="status_low_battery">#FF9500</color>
    <color name="status_error">#FF3B30</color>
</resources>
```

## Material theme mapping (`res/values/themes.xml`)

Maps the same palette onto Material components so buttons, nav bars, etc. pick it
up automatically:

```xml
<style name="Theme.ArmoryApp" parent="Theme.Material3.Light.NoActionBar">
    <item name="colorPrimary">@color/primary</item>
    <item name="colorPrimaryVariant">@color/secondary</item>
    <item name="colorOnPrimary">@color/white</item>

    <item name="colorSecondary">@color/secondary_button</item>
    <item name="colorOnSecondary">@color/white</item>

    <item name="android:statusBarColor">@color/primary</item>
    <item name="android:navigationBarColor">@color/primary</item>
</style>
```

## Button style notes (to match iOS exactly)

- **Primary button**: background `primary_button` (`#FFC397`), white bold text,
  corner radius 10dp, height 50dp.
- **Secondary button**: background `secondary_button` (`#708FFC`), white bold
  text, corner radius 10dp, height 44dp.
- **Tertiary button**: background `primary` (`#496BB3`), white bold text, white
  1.5dp border, corner radius 10dp, height 50dp.
- Disabled state for primary/tertiary buttons uses `disabled_button` (`#6483C4`).

## Dark mode

The iOS app has no dark-mode-specific colors (all hex values are fixed, not
semantic/dynamic). For parity, ship the Android app as **light-theme only**
initially (`Theme.Material3.Light.NoActionBar` as above, no `values-night`
overrides) unless a dark mode is explicitly requested later.
