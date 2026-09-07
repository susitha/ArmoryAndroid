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
