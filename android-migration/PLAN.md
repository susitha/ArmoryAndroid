# Android Port Plan — Armory Management App

## Context

The current app (`armoryapp/ArmoryApp`) is a native iOS/UIKit app for managing armory
inventory (enroll, checkout, checkin, search/locate, take inventory) via UHF RFID
tags. RFID hardware access goes through `AsReaderGunSDK` / `AsRingAccessorySDK`
(AsReader Bluetooth gun-style reader accessory).

For the Android version, the RFID hardware/SDK changes to a **Chainway C72** — a
rugged handheld Android device with a built-in UHF RFID module, using Chainway's
native Android Studio SDK (Java/Kotlin, AAR-based).

SDK reference: https://www.chainway.net/Support/Info/10

Relevant downloads from that page:
- `API_Ver20251103` for Android Studio — core device API
- `Demo-uhf_as.rar` — UHF RFID demo for Android Studio
- `Demo-2D_as.rar` — 2D barcode demo for Android Studio

## Key existing code to port from (iOS)

- **RFID abstraction**: [`Services/AsReaderManager/AsReaderGUNManager.swift`](../armoryapp/ArmoryApp/ArmoryApp/Services/AsReaderManager/AsReaderGUNManager.swift)
  — clean manager/delegate interface: `setTagRFIDMode()`, `setSearchRFIDMode()`,
  `startRFIDScanning()`, `stopRFIDScanning()`, `setMask(forEPC:maskParameters:)`,
  `getBatteryStatus()`, plus delegate callbacks for tag reads, connect/disconnect,
  errors, and overheating. This maps closely onto Chainway's `UHFManager` API and
  is the template for the new Android `RfidManager`.
- **Network layer**: `Services/API/API.swift` (~700 lines), `API+Endpoint.swift`,
  `API+Blueprint.swift`, plus `Requests/` and `Responses/` model folders — defines
  every backend call and payload shape 1:1 for the Android port.
- **Navigation**: `Navigation/AppCoordinator.swift` — coordinator pattern driving
  screen flow; Android equivalent is Navigation Component (or a Compose nav graph).
- **Screens** (`Controllers/`): Auth (login), Dashboard, Enroll, Checkout, Checkin,
  Search (search/locate asset), Inventory (take inventory/breakdown), Common
  (scan, category picker, user picker).
- **Models** (`Models/`): Asset, Category, User, UserGroup, TagFormat.

No backend/API changes are expected — only the client-side RFID hardware changes.

## Steps

1. **Scope and inventory the iOS app**
   - Catalog all screens/flows from `Controllers/`.
   - Catalog domain models and API endpoints — these define the Android data and
     network layers directly.

2. **Get the Chainway C72 SDK and dev hardware**
   - Download the Android Studio API package and UHF demo from the support page.
   - Get a physical C72 unit — RFID behavior is hard to fake in an emulator; test
     on real hardware early.
   - Read the C72 user manual and UHF demo source for its inventory / power-gain /
     mask / battery API surface.

3. **Set up the Android project skeleton**
   - New Android Studio project, Kotlin, min/target SDK matched to the C72's
     shipped Android version.
   - Architecture: MVVM + Navigation Component, or Jetpack Compose (worth
     considering since this is a full rewrite, not a shared codebase).

4. **Port the network/API layer**
   - Reimplement `API.swift`, `API+Endpoint.swift`, `API+Blueprint.swift`, and the
     request/response models using Retrofit + Moshi/kotlinx.serialization.
   - Port token refresh/session logic (`RefreshTokenResponse`, `RefreshTokenData`,
     `Session.swift`) to an OkHttp Authenticator/Interceptor.

5. **Build the RFID abstraction layer first, in isolation**
   - Write a Kotlin `RfidManager` wrapping Chainway's `UHFManager`, mirroring
     `AsReaderGUNManager`'s surface: continuous vs. single-tag mode, min/max power
     gain, RSSI mode for locate/search, EPC masking, trigger-key handling, battery
     status, overheat/error callbacks.
   - Validate against real hardware with a throwaway test screen before wiring it
     into any real flow — this de-risks the one genuinely new piece of the port.

6. **Port screens flow-by-flow**, reusing the RFID manager and API layer:
   - Login → Dashboard
   - Common: Scan, Category picker, User picker
   - Enroll (tag an asset)
   - Search/Locate (uses continuous + RSSI mode)
   - Checkout / Checkin
   - Inventory (take inventory / breakdown)

7. **Re-theme/re-layout for Android**
   - `Theme.swift`, `Assets.xcassets`, and the `Extensions/UI*+Extensions.swift`
     helpers won't port directly — rebuild as a Material theme plus
     Compose/XML layouts. Existing icon assets (rifle, handgun, machinegun,
     device, logo, etc.) can be reused as image files.

8. **QA on-device**, focused on RFID-specific behavior:
   - Multi-tag inventory scans
   - Tag masking accuracy
   - Trigger-button behavior
   - Low-battery and overheat handling
   - Reconnect behavior
   These are the areas most likely to differ from the old Bluetooth-gun reader.

9. **Backend compatibility check**
   - Confirm existing endpoints (`APIDetailsResponse`, `TagFormatResponse`, etc.)
     need no changes, since only the client-side reader hardware changes.

## Open questions / decisions still needed

- [ ] Compose vs. XML/View-based UI for the new app
- [ ] Min supported Android version (depends on C72's shipped OS) — scaffolded as 24, unconfirmed
- [ ] Whether to reuse existing icon/image assets as-is or redesign for Material

## Progress

- [x] Steps 3–5 (project skeleton, Retrofit client, stub `RfidManager`) scaffolded
      in [`ArmoryAppAndroid/`](../ArmoryAppAndroid) — see [SCAFFOLD.md](SCAFFOLD.md)
      for what's there and the decisions made along the way (Gson over Moshi, etc).
- [x] `RfidManager` wired to the real Chainway SDK (`RFIDWithUHFUART`), not just a stub —
      see [SCAFFOLD.md](SCAFFOLD.md)'s RFID section for what's confirmed vs. still TODO.
- [x] Step 6 (screens) **complete** — Login, Dashboard, and all five main
      flows (Enroll, Checkout, Checkin, Search, Inventory) are real and wired
      to the backend now — see [SCAFFOLD.md](SCAFFOLD.md) for the full
      per-screen detail. Highlights: Checkin's 3-step API sequence (plain
      checkin → discrepancy-note approve-and-return → admin re-auth
      approve-and-return under a one-off super-user token, deliberately never
      saved to `SessionManager`); Search/Inventory's real continuous RFID
      scanning + EPC masking, with two hand-rolled custom views
      (`SignalGaugeView`, `PulseView`) replacing iOS's third-party gauge/pulse
      libraries. Known gap carried forward: continuous-mode scanning
      (Search/Inventory) doesn't need a trigger-key fix — it already relies
      on real continuous scanning, not the trigger.
- [x] Physical trigger-key support, wired and confirmed on two real devices
      (a C72 and a C66) — see SCAFFOLD.md's Next steps §2 for the full
      detail. `ScanActivity` now scans on the physical trigger press, keyed
      off a `Set<Int>` of confirmed per-device keycodes (each Chainway unit
      tested has fired a *different* code — 293 on the C72, 294 on the C66),
      gated on `enableTriggerButton`, with the previous tap-to-retry/
      debug-simulated-tag stand-in kept as a fallback rather than removed.
      Also the project's first real Gradle build in this environment (JDK 17
      required — see SCAFFOLD.md). Two more real bugs found and fixed via
      live on-device testing along the way: a duplicate-scan-schedule race on
      a screen's second visit (two connect callbacks each independently
      arming an auto-scan timer), and felt UI delay between scans (slow
      native SDK calls blocking the main thread) — both in SCAFFOLD.md §2.
      Beep added too (`ToneGenerator`, since `RFIDWithUHFUART` has no
      beep/buzzer API) — had to switch from `STREAM_NOTIFICATION` to
      `STREAM_MUSIC` after the former proved inaudible on the C66 despite
      actually playing.
- [x] Dashboard menu-button icons and the handgun/rifle/machine-gun category
      icons are now real ported assets, not stand-ins. They'd originally been
      left as stock Android icons / letter-in-circle placeholders because
      macOS's `qlmanage -t` (used to rasterize iOS's PDF assets to PNG)
      flattens transparency onto opaque white, making solid-white template
      artwork look invisible/unusable. Re-rasterizing with PyMuPDF
      (`page.get_pixmap(..., alpha=True)`) instead proved they were fine all
      along — see SCAFFOLD.md's Dashboard/Checkout notes for detail.
- [x] The logout icon settled on `res/drawable/ic_logout.xml`, a hand-built
      vector redraw of the original ported `logout.png` glyph (door-bracket
      + arrow) — a screenshot briefly suggested the live app had moved to a
      plain arrow-in-circle instead, but that reading turned out to be
      wrong; the door+arrow shape was confirmed as the one wanted. See
      SCAFFOLD.md's Dashboard notes for the full back-and-forth.
- [x] Search's `device.png` gap closed, but with a new asset rather than a
      recovered one — the old iOS source was a photo of the AsReader
      accessory with no Chainway C72 equivalent, so there was nothing to
      recover. The user supplied a cartoon illustration of the Chainway-style
      handheld instead, now at `res/drawable-nodpi/device.png` and wired into
      `activity_locate_asset.xml` as a 120dp graphic above the signal-gauge/
      pulse-ring row — see SCAFFOLD.md's Search/Locate notes for detail.
- [x] Real launcher icons generated from the iOS `AppIcon` source (added to
      this environment mid-task — see SCAFFOLD.md's Next steps §3) — legacy
      `mipmap-*/ic_launcher(_round).png` from the 1024px master full-bleed,
      adaptive `mipmap-anydpi-v26/ic_launcher(_round).xml` with a white
      background and a transparent foreground layer from the already-ported
      logo mark. Confirmed rendering correctly on-device (App Info screen).
- [x] Login screen layout bug fixed: the version-number text was pinned to
      the whole screen's bottom edge, so opening the keyboard (which shrinks
      the screen under `adjustResize`) squeezed it up into the password
      field instead of moving with the rest of the form. Chained it below
      the login button/loading indicator instead, confirmed via screenshot
      on the C66 with the keyboard open.
- [x] `POWER_GAIN_MAX` confirmed against real hardware (a one-off `getPower()`
      diagnostic read back 30 right after connect on the C66, matching the
      constant exactly) — see SCAFFOLD.md's Next steps §2. `POWER_GAIN_MIN`
      stays an educated guess; `setPower()` never succeeded during this work
      to verify the low end the same way.
- [x] All of SCAFFOLD.md's "Next steps" punch list is now closed out —
      trigger key, beep, power-gain range, launcher icons, and the first
      real Gradle build all done and confirmed on physical hardware (a C72
      and a C66). What's left is genuinely open-ended hardening (the
      `stopInventory()` SDK reliability issue) rather than a tracked list.
- [x] Scan screen's `DEVICE_CONNECTED`/`SCANNING` icon replaced after direct
      feedback ("checkmark isn't nice") — went through two iterations
      (a hand-built signal-wave glyph, then a full device+tag illustration
      matching a reference screenshot the user shared) before landing on
      `res/drawable-nodpi/device_scan_tag.png`: the existing Search/Locate
      C72 illustration with a small RFID-tag glyph and orange signal-wave
      arcs composited onto it with Pillow, tag positioned in front of the
      device's antenna module (not floating above it — first pass had this
      wrong too). See SCAFFOLD.md's Enroll notes.
- [x] Real cross-platform data bug fixed: Android was reporting a tag's EPC
      4 hex characters shorter than iOS for the *same physical tag*
      (`E15002535072720212000369` vs. iOS's
      `3000E15002535072720212000369`) — not a display quirk, a genuine
      format mismatch that would break search/checkout/checkin matching
      across platforms. Root cause: the SDK reports a tag's PC (Protocol
      Control) word separately from its EPC (`UHFTAGInfo.getPc()` vs.
      `getEPC()`); `ChainwayRfidManager` only ever read the latter. Fixed
      with a `fullEpc()` helper (`getPc() + getEPC()`) used on every tag-read
      path — see SCAFFOLD.md's RFID abstraction section for the full
      detail, including a likely second bug this incidentally also fixed
      (the hardware EPC mask being applied to a too-short string).
- [x] A batch of Locate/Inventory/Breakdown polish and real bugs from
      continued on-device testing — full detail in SCAFFOLD.md's Search and
      Inventory flow notes: an asset header (icon/name/category/serial)
      added to the top of `LocateAssetActivity`; the signal gauge rebuilt as
      a segmented meter matching a real iOS reference screenshot (was a
      continuous-fill bar, a documented simplification, not a bug) and
      repositioned (device+pulse at its base, not beside it); a proximity
      beep added to Locate and a per-new-tag-then-every-read beep added to
      Inventory, both eventually paced to `PulseView`'s own animation rhythm
      after an unthrottled version "generated an ugly sound"; two real bugs
      — `stopScanning()` leaving `TakeInventoryActivity` in a stale
      "Stop"-labeled state after Retake, and `LocateAssetActivity.onTagRead()`
      never checking the EPC matched the target tag before updating the
      gauge (letting an unrelated stray tag drive it) — both fixed. Also a
      long dashboard/breakdown button text-fit saga (12sp → 10sp → autosize
      → padding trim → measured-and-hardcoded 9sp → shortened labels at
      11sp → 13sp with further-trimmed padding/margins → "Available"
      restored in full via a per-button autosize cap) — see SCAFFOLD.md's
      Dashboard and Inventory notes for the whole trail; the reusable lesson
      is in there too (measure the real resolved size instead of guessing).
- [x] `ScanActivity`'s auto-scan-on-connect stand-in removed on request
      ("when enrolling it scan automatically even though trigger button not
      pressing") — confirmed first that it was the long-standing intentional
      behavior (fires once, then quiet), not a regression, then removed
      anyway since only explicit trigger/tap scans are wanted now. See
      SCAFFOLD.md's Enroll flow notes.
- [x] Real splash screen added (there wasn't one before — asked, confirmed,
      then implemented) via AndroidX `core-splashscreen` (`1.0.1`), the
      current standard approach — not a separate `SplashActivity` with a
      manual delay, which Google now discourages. Applied via a
      `Theme.FetchCICG.Splash` theme (`parent="Theme.SplashScreen"`, white
      background, `postSplashScreenTheme` back to `Theme.FetchCICG`) set
      directly on `LoginActivity` (the app's real launcher — there's no
      dedicated splash activity), plus a single `installSplashScreen()` call
      before `super.onCreate()` there.
      Follow-up: the icon was first set to the raw `logo.png` and came out
      "cropped circle" ("icon is cropped circle, need the logo in the white
      screen") — the splash icon slot masks its drawable like a launcher
      icon, and `logo.png` has no safe-zone inset for that. Fixed by
      switching to `@mipmap/ic_launcher_foreground` (the adaptive-icon
      foreground layer, already inset correctly) plus an explicit white
      `windowSplashScreenIconBackgroundColor`. Re-verified via `adb
      screencap` on the emulator: full uncropped "A" mark on white, clean
      handoff into the login screen. Not yet re-verified on the C66
      (Android 11 / API 30, below the native API's floor — the compat
      library falls back to a different rendering path there) since it
      wasn't connected when this landed.
- [x] Follow-up: launcher/home-screen icon reported showing rounded corners
      ("now its showing rounded corner icon") — a separate issue from the
      splash fix above. Caused by `mipmap-anydpi-v26/ic_launcher.xml`
      declaring a real adaptive icon, which Android always clips to the
      device's own icon-mask shape wherever it's resolved (a rounded square
      on the C66's launcher). Fixed by deleting the adaptive-icon XMLs and
      shipping plain flat `ic_launcher.png`/`ic_launcher_round.png` per
      density instead, generated from the iOS `AppIcon.appiconset/1024.png`
      master (full-bleed, no adaptive safe-zone padding) — confirmed via `adb
      screencap` on the C66: hard square corners, matching the other
      Chainway-installed apps (Armory, AppCenter) on the same launcher. The
      emulator's Pixel Launcher still shows it circle-masked regardless of
      icon format — that's Android auto-masking any icon for apps targeting
      API 26+, enforced by that launcher but not by Chainway's; since this
      app only ships to the Chainway handhelds, the C66 result is the one
      that matters. See SCAFFOLD.md for the full root-cause writeup.
- [x] Follow-up-to-the-follow-up: pushing the launcher icon bolder (~92%
      canvas fill, to look less small next to Drive/Calculator) caused a
      regression — "the corners of the A shape are cut off in splash screen
      icon." Caught the actual cause by burst-capturing screenshots every
      ~40ms from `am start`: Android's cold-start icon-zoom transition (which
      happens before the splash settles) uses the launcher icon and applies
      the same circular-safe-zone masking as the launcher itself, and 92%
      fill is well outside Android's documented 66% safe circle. Fixed by
      reverting the launcher icon back to the 66%-inset version (matching
      the untouched, always-safe `ic_launcher_foreground` used by the actual
      splash icon). Confirmed clean via burst-capture on the emulator
      (including the transition frame itself) and the C66. The icon is back
      to reading smaller than bold solid-fill icons like Drive — that's
      Android's masking system, not a bug; a real fix for the "small" look
      would need thicker strokes in the mark itself, not just scaling it up.
- [x] User pushed back that it still looked small/cut-off after a genuinely
      clean reinstall + reboot on the C66. Root-caused for real this time:
      a **flat/legacy** launcher icon (no `mipmap-anydpi-v26/ic_launcher.xml`)
      gets forced through Android's icon-normalization pipeline for any app
      targeting API 26+ (this app targets 34), which renders it at a fixed,
      conservative size *regardless of the source PNG's fill ratio* —
      confirmed by unzipping the built APK and verifying the packaged PNG
      really had changed while the on-screen size stayed identical across
      66%/85%/92% fill attempts. There's no PNG-only fix for that. Fixed by
      restoring the adaptive icon (`mipmap-anydpi-v26/ic_launcher.xml` +
      `ic_launcher_round.xml`, same white background + `ic_launcher_foreground`
      as originally authored) — adaptive icons skip that normalization and
      render at their authored safe-zone size, confirmed via `adb screencap`
      to now match Drive/Calculator's visual weight on the C66. Also
      re-diffed against the very first screenshot from this whole saga: Drive
      already had the identical soft-rounded card back then, before any of
      today's changes — meaning the original "rounded corner icon" report
      was most likely just uniform launcher chrome, not a real per-app issue.
      Net result of the whole day's icon back-and-forth: back to the original
      adaptive icon declaration. Splash reconfirmed clean on both devices
      (it was never touched by any of the launcher-icon changes). Full
      writeup with the diffed screenshots' reasoning in SCAFFOLD.md.
- [x] User kept reporting the splash's corners were cut off even after
      several rounds of "looks clean to me" screenshot checks (both sides).
      Stopped eyeballing it and *measured* instead: the on-screen content's
      bounding-box aspect ratio was ~1.34–1.35 vs. the source file's 1.50 — a
      real, reproducible ~10% clip, not a perception mismatch. Root cause:
      the actual Android adaptive-icon safe zone is a 66dp circle in a 108dp
      canvas (~30.6% radius from center), and this logo's wide/short shape
      (aspect ~1.5) means the true safe *width* for it is only ~48% of the
      canvas — not the 66% used throughout this entire saga, which was
      always the wrong number for this aspect ratio. The foreground was at
      65.7% width, well past the real limit. Recomputed the correct size
      from the actual geometry (safe radius formula + 10% margin) and
      regenerated `ic_launcher_foreground.png` at ~45.6% width for every
      density, then re-verified with the same bounding-box measurement
      (not just a screenshot glance): aspect improved to ~1.446, matching
      the 1.50 target within noise. Confirmed clean on both devices. Both
      the splash icon and the launcher icon are now visibly smaller than at
      any earlier point today — that's the actual correct safe size, not a
      regression. See SCAFFOLD.md for the full math and the lesson about
      verifying masking bugs by measurement, not by eye.
