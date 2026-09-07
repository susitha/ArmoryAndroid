# Android Project Scaffold

Location: [`ArmoryAppAndroid/`](../ArmoryAppAndroid) (sibling to `armoryapp/`).
Kotlin, Gradle Kotlin DSL, package `com.cenango.fetchcicg`, app name
"FetchCICG". **Correction**: this was originally built as package
`com.cenango.armoryapp` / app name "Armory" — wrong. `com.cenango.FetchCICG`
is the real iOS app's bundle id (confirmed in `project.pbxproj`); `ArmoryApp`
only appears on the iOS *test-target* bundle ids and the Xcode project/folder
name, and "Armory Management System" is just the README's description, not
the shipped product name. Fixed via a project-wide rename: package/namespace/
applicationId, the `app_name` string, the `Application` class
(`FetchCICGApplication`, was `ArmoryApplication`), and the app theme
(`Theme.FetchCICG`, was `Theme.ArmoryApp`). Internal-only names that don't
reflect user-facing branding (`ArmoryApiService`, the `armory_session`/
`armory_storage` SharedPreferences file names) were deliberately left as-is —
not the same category of mistake.

This covers step 3–5 of [PLAN.md](PLAN.md): project skeleton, the Retrofit
network layer, and a stub RFID abstraction. Screens/UI (steps 6–7) haven't
been started — `ui/main/MainActivity.kt` is a placeholder launcher screen only
(needed so the app has something to install/run; it shows session/RFID status
as a startup smoke test), not a real screen. Replace it once Login/Dashboard
land.

## Module layout

```
ArmoryAppAndroid/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/wrapper/gradle-wrapper.properties   (Gradle 8.9 — run `gradle wrapper`
│                                                or open in Android Studio to
│                                                generate the actual wrapper jar)
└── app/
    ├── build.gradle.kts        (AGP 8.5.2, Kotlin 1.9.24, minSdk 24*, targetSdk/compileSdk 34)
    ├── libs/DeviceAPI_ver20251103_release.aar   (Chainway SDK, com.rscja.deviceapi)
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/cenango/armoryapp/
        │   ├── ArmoryApplication.kt      — holds SessionManager, RetrofitClient.api, RfidManager
        │   ├── data/
        │   │   ├── network/              — Retrofit/OkHttp (see below)
        │   │   ├── model/                — Asset, Category, User, UserGroup, TagFormat, InventoryGroup
        │   │   │   ├── requests/         — LoginRequest, RefreshTokenRequest, AssetRequest, AssetMoveRequest
        │   │   │   └── responses/        — one per non-trivial API response shape
        │   │   └── session/SessionManager.kt   — SharedPreferences, mirrors iOS `Session`
        │   ├── rfid/                      — RfidManager abstraction (see below)
        │   └── util/ApiResult.kt          — Result<T, Error> equivalent for API calls
        └── res/values/{colors,themes,strings}.xml   — from THEME.md

* minSdk 24 is a placeholder — confirm against the C72's shipped Android version.
```

## Retrofit / networking

- **`ArmoryApiService.kt`** — one Retrofit method per case in iOS's
  `FetchCICGAPI` enum (API+Blueprint.swift): same paths, HTTP methods, and
  query/body shapes. Uses Kotlin `suspend fun`, not callbacks.
- **`ApiSettings.kt`** — mirrors `ApiSettings.swift` (API version = 10) and the
  `#if DEBUG` host switch from `FetchCICGAPI.buildURL`.
- **`RetrofitClient.kt`** — debug builds hit the dev host directly; release
  builds resolve the real host at runtime from `SessionManager` (populated by
  the api-details discovery call, same two-host scheme as iOS) via a
  dynamic-host `Interceptor`, since Retrofit's base URL is otherwise fixed at
  client-creation time.
- **`AuthInterceptor.kt`** — adds the raw token as the `Authorization` header
  (no `Bearer ` prefix — matches the existing backend contract exactly).
  Endpoints that must skip auth (`login`, `getApiDetails`) are tagged
  `@Headers("No-Authentication: true")`, which the interceptor strips.
- **`TokenAuthenticator.kt`** — OkHttp `Authenticator`; on a 401, exchanges
  the stored refresh token for a new access token and retries once (mirrors
  `API.refreshToken` in API.swift). Clears the session on refresh failure.
- **`util/ApiResult.kt`** — sealed `ApiResult<T>`/`ApiError` mirroring
  `Result<T, API.Error>` (API+Error.swift), with a `safeApiCall {}` wrapper so
  call sites don't deal with raw exceptions.
- **JSON**: Gson (`converter-gson`), not Moshi/kotlinx.serialization as
  originally floated in PLAN.md — simpler to scaffold with no extra codegen
  plugin, and `@SerializedName` maps 1:1 onto iOS's `CodingKeys`.
- Response wrapper types that iOS decodes via `singleValueContainer` (bare
  array/object, e.g. `CategoriesResponse`, `UsersResponse`, `AssetResponse`)
  were **not** ported as wrapper classes — the API methods just return
  `List<Category>`, `List<User>`, `Asset` directly, since Gson/Retrofit don't
  need an intermediate type for that.
- One inherited backend quirk: `searchAssets`'s `category_id` query param
  expects Swift's `Array<Int>.description` format (`"[1, 2, 3]"`). Kotlin's
  `List<Int>.toString()` produces the same string, so pass that in directly —
  documented inline in `ArmoryApiService.searchAssets`.

## RFID abstraction (`rfid/` package)

- **`RfidManager.kt`** — interface mirroring `AsReaderGUNManager`'s public API
  1:1: `setTagRfidMode()`, `setSearchRfidMode()`, `startRfidScanning()`,
  `stopRfidScanning()`, `clearMask()`, `setMask(epc, parameters)`,
  `getBatteryStatus()`, plus `isDeviceConnected`/`hasBarcodeSupport`/
  `hasRfidSupport`/`enableTriggerButton`.
- **`RfidManagerListener.kt`** — mirrors `AsReaderGUNManagerDelegate`
  (connected/disconnected/error/tag-read/overheated callbacks).
- **`RfidBatteryStatus.kt`**, **`RfidError.kt`** — mirror
  `AsReaderGUNManager+Enums.swift` / `+Error.swift`. Chainway reports an
  actual battery percentage rather than a discrete level, so
  `RfidBatteryStatus.fromPercentage()` buckets it back into the same 5 levels
  the UI already expects.
- **`ChainwayRfidManager.kt`** — the real implementation, now wired to the
  actual SDK (`DeviceAPI_ver20251103_release.aar`, dropped into `app/libs/`
  and added as a dependency). Verified against that SDK's bundled Javadoc
  (package `com.rscja.deviceapi`, class `RFIDWithUHFUART` — a UART-based UHF
  reader singleton, of which the C72's built-in module is one implementation):
  `getInstance()` / `init(context)` / `free()` for lifecycle,
  `setInventoryCallback` + `startInventoryTag()`/`stopInventory()` for
  continuous (search-mode) reads, `inventorySingleTag()` (run off the main
  thread — it blocks on UART I/O) for single-tag (tag-mode) reads,
  `setPower(int)` for gain, and `setFilter(bank, ptr, cnt, data)` for EPC
  masking. `RfidError` was updated to use the SDK's real
  `UhfBase.ErrorCode` values instead of placeholder AsReader codes.
  Two things are still **not** verified against real hardware:
  - **Power gain range** (`POWER_GAIN_MIN`/`MAX` in the companion object) —
    the Javadoc doesn't state valid bounds for `setPower()`.
  - **Trigger key wiring** — the SDK doesn't expose the physical scan button
    as a callback; it's typically an Android `KeyEvent` handled at the
    `Activity` level with a device-specific keycode. Left as a TODO.
  - Also worth noting: unlike the AsReader gun, there's no accessory battery
    to query, so `getBatteryStatus()` reads the handheld's own battery via
    Android's `BatteryManager` instead of anything UHF-specific, and there's
    no overheat *callback* at all (only a polled `getTemperature()`), so
    `onDeviceOverheated()` is currently never fired — see the `NOTE` at the
    bottom of the file.
  - **Real cross-platform data bug, found from "in ios app epc read as
    3000E15002535072720212000369 but in android app it read as
    E15002535072720212000369"** — a genuine format mismatch, not just a
    display difference, since the same physical tag needs to produce the
    same ID string on both platforms for search/checkout/checkin/masking to
    ever match across them. Root-caused via a live logcat capture (temporary
    diagnostic logging of a real tag read) rather than guessed: `UHFTAGInfo`
    carries the tag's **PC** (Protocol Control) word in its own `getPc()`
    field, separate from `getEPC()` — confirmed live: `PC="3000"`,
    `EPC="E15002535072720212000369"`, and `PC + EPC` matches iOS's reported
    ID exactly. `ChainwayRfidManager` was only ever reading `getEPC()`, on
    both the continuous-inventory and single-tag paths. **Fixed** with a
    `fullEpc(tagInfo)` helper (`getPc() + getEPC()`, empty-string fallback
    for a null PC) used everywhere a tag read gets reported to a listener.
    This also means `LocateAssetActivity.setMask()`'s `format.maskIndex`/
    `format.offset`/etc. (backend-supplied, calibrated against iOS's
    full-length EPC string) were being applied to a **4-hex-char-shorter**
    string than they were calibrated for before this fix — likely a second,
    quieter bug (wrong substring extracted for the hardware mask) beyond
    just the display/search-string mismatch, now also corrected as a
    side effect.

## Screens (`ui/` package) — step 6, in progress

- **`ui/auth/LoginActivity.kt`** + `res/layout/activity_login.xml` — a real
  port of `LoginViewController.swift`: same ~40/60 split layout (logo over a
  primary-blue input area), same colors (`link_text` title, white
  labels/inputs, `login_button_background` selector matching
  `TertiaryButton`'s normal/disabled backgrounds), same behavior (login button
  disabled until both fields are non-blank, best-effort API-details resolution
  on load, Enter/Done submits). Calls `ArmoryApiService.login()` for real and
  saves the session via `SessionManager` on success.
  - The logo and `login_background` pattern (`res/drawable-nodpi/*.png`) were
    rasterized from the iOS assets (`Assets.xcassets/*.imageset/*.pdf`) —
    **initially via macOS's `qlmanage -t`, which turned out to flatten PDF
    transparency onto an opaque white background** (confirmed by inspecting
    pixel alpha: every "transparent" pixel came out `(255,255,255,255)`, not
    `(0,0,0,0)`) — invisible as long as the icon sits on a white surface, but
    a visible white box wherever it doesn't (this bit `logo.png`, which
    layers over `login_background`'s pattern). **Re-rasterized using PyMuPDF
    (`pip install pymupdf`, `page.get_pixmap(matrix=mat, alpha=True)`)**,
    which preserves real alpha — every PDF-sourced PNG in this project
    (`logo`, `login_background`, `scan_frame`, `status_success`,
    `status_failed`, and the 4 `dashboard_*` icons) was regenerated this way.
    Use PyMuPDF, not `qlmanage`, for any future PDF→PNG conversion in this
    project — regenerate at a cleaner resolution (or export a proper
    multi-density PNG/vector from the source files) rather than relying on
    these long-term either way.
  - Fields deliberately do **not** use a Material `TextInputLayout`/floating
    hint — iOS's `InputTextFieldView` puts a separate plain label *above* a
    plain white rounded box (`ExtendedTextField`: white fill, 10dp corners,
    thin gray border, trailing icon), so that's what's built here
    (`bg_input_field.xml` drawable + plain `EditText` with `drawableEnd`) to
    match the real look instead of substituting a generic Material pattern.
    The login button also got `TertiaryButton`'s white 1.5dp border added.
  - iOS prefills admin credentials in `#if DEBUG` builds; that wasn't ported
    (deliberately) — add local dev-only prefill yourself if wanted, rather
    than checking credentials into this file.
- **`ui/dashboard/DashboardActivity.kt`** + `res/layout/activity_dashboard.xml`
  + `res/layout/item_dashboard_info.xml` — a real port of
  `DashboardViewController.swift`: title row with a logout action, a 2x2 grid
  of live summary counts (`GET /api/dashboard`, refreshed on every `onResume`
  like iOS's `viewDidAppear`), and the 5-button menu (Checkin/Checkout/
  Enroll/Search/Inventory) on a primary-blue area below. On first load it also
  fetches and caches tag-format/categories/users data via the new
  **`data/storage/Storage.kt`** (Kotlin port of `Utils/Storage.swift` —
  separate from `SessionManager`, which only holds auth state).
  - The 4 info-card icons (`res/drawable-nodpi/dashboard_*.png`) are real,
    rasterized the same way as the login assets — these ones happen to be
    solid-colored in the source PDFs (not template/tintable), so the
    conversion produced a faithful result directly. (They did carry the same
    opaque-white-background bug as `logo.png` at first — not visible here
    since the card background is a similar light color, but fixed in the
    same PyMuPDF re-rasterization pass noted under Login above.)
  - The 5 menu-button icons **are now ported from iOS**
    (`res/drawable-nodpi/menu_checkin.png`, `menu_checkout.png`,
    `menu_enroll.png`, `menu_search.png`, `menu_inventory.png`). These were
    originally left as stock Android framework icon stand-ins (`ic_menu_revert`,
    `ic_menu_send`, `ic_input_add`, `ic_menu_search`, `ic_menu_agenda`)
    because the source PDFs (`Menu/*.imageset`) are white template shapes
    meant to be tinted at render time, and `qlmanage -t`'s thumbnail renderer
    flattens transparency onto a white backdrop — which makes solid-white
    artwork render as an invisible white box, easy to mistake for "there's
    no real artwork here." Re-rasterizing with PyMuPDF (`page.get_pixmap(matrix=mat,
    alpha=True)`, same fix as the Login/Dashboard-info-card assets above)
    proved they were fine all along — genuine white glyphs on a transparent
    background, verified by compositing each onto the real menu-blue button
    background before wiring them in. `activity_dashboard.xml`'s `app:icon`
    values now point at these real files instead of the framework stand-ins.
  - **Menu button label font size, three iterations to actually land it**:
    reported as "Inventory" (the longest of the 5 labels, in the narrower
    3-button bottom row) wrapping to 2 lines, plus the Scan screen's EPC
    text (see the Enroll flow notes — grew 4 chars with the PC-prefix fix
    above) doing the same. First tried 12sp uniformly across all 5 buttons —
    still wrapped. Then 10sp — **still wrapped, confirmed via `aapt2 dump
    xmltree` on both the built and the actually-installed-on-device APK**
    that the value really was 10sp and really was running, ruling out a
    stale-build red herring before trying anything else. Switched to
    `autoSizeTextType="uniform"` (7–14sp range, `maxLines="1"`) to guarantee
    a fit regardless of label length — this worked, but each button then
    resolved to a *different* size (short labels stayed at 14sp, "Inventory"
    shrunk further), which read as visually inconsistent. Settled on a
    single uniform fixed `textSize="8sp"` for all 5 (removing autosize) —
    fit, but reported straight back as "too small to read." Next: the actual
    secondary problem was `MaterialButton`'s default style eating a large
    chunk of the already-narrow button width in horizontal padding — added
    `paddingStart`/`paddingEnd="2dp"` + `insetLeft`/`insetRight="0dp"` to
    reclaim it, paired with a fixed `textSize="12sp"` — better, but
    "Inventory" specifically (still the longest label) started clipping
    mid-word at that fixed size, while the 4 shorter labels had room to
    spare. Tried `autoSizeTextType="uniform"` (8–14sp) on top of the padding
    fix next, which guaranteed a fit — but then reported back *again*, this
    time as visibly mismatched sizes across the row (expected: autosize
    resolves each button's size independently, capped only by what that
    button's own label needs).
    **Actual final fix — measured, not guessed**: added a one-off diagnostic
    (`button.post { Log.d(..., "$\{button.textSize}") }` on all 5, removed
    again once done) to read back the *exact* size autosize had resolved for
    each button with the padding fix in place: **14sp for the 4 short
    labels, 9sp for "Inventory."** Replaced autosize with that measured 9sp
    as one fixed, uniform value across all 5 — real uniformity (not
    independently-resolved-and-therefore-mismatched sizes) at the largest
    size that's actually confirmed to fit the longest label, arrived at by
    reading the real number instead of another guess-rebuild-reinstall
    round. Same value applied directly to `InventoryBreakdownActivity`'s 3
    toggle buttons too (see its own note below) — same button width, same
    9-character longest label ("Available"/"Checkouts"), same padding fix
    already in place there; reasonable to extrapolate rather than repeat the
    measurement given that screen takes a full scan flow to even reach.
  - **The logout icon took a detour before settling**: `logout.imageset/logout.pdf`
    was initially ported as `logout.png` (a door-bracket + arrow glyph,
    faithful to that PDF). A screenshot that looked like the real device
    showed only a plain arrow in a soft circle, which read as the live app
    having moved past that source asset — so `logout.png` was deleted and
    swapped for a hand-built plain-arrow-in-circle vector to match it. A
    follow-up screenshot clarified that reading was wrong: the door+arrow
    shape was the one actually wanted after all. Landed on
    **`res/drawable/ic_logout.xml`** — the same door-bracket + arrow glyph as
    the original PNG, redrawn as stroked vector paths (no background badge)
    instead of a raster asset, so it stays crisp at any density. Colors are
    baked in (primary blue, no fill), same "colors final, not meant to be
    tinted" convention as `ic_status_{connected,not_connected,low_battery,
    overheated}.xml`. `activity_dashboard.xml`'s `logoutButton` points at
    `@drawable/ic_logout` with no `android:tint`. Worth remembering: a
    screenshot that looks device-authentic isn't automatically ground truth
    over a straightforward re-rasterization of the actual source asset —
    see [[armory-android-screenshot-vs-source]].
  - Checkin/Checkout/Enroll/Search/Inventory are all built now (see their own
    sections below) — the Dashboard's `comingSoon` toast lambda was removed
    once the last of the five was wired up.

- **Enroll flow** — `ui/common/ScanActivity.kt` + `ui/enroll/EnterAssetDetailsActivity.kt`
  + `ui/enroll/AssetSummaryActivity.kt`, plus two shared pickers
  (`ui/common/CategoryPickerActivity.kt`, `ui/common/UserPickerActivity.kt`).
  Real port of `Controllers/Common/ScanViewController.swift` +
  `Controllers/Enroll/*` + `Controllers/Common/{Category,User}PickerViewController.swift`.
  This is the first screen to actually exercise `ChainwayRfidManager` and the
  first multi-screen flow built.
  - **`ScanActivity`** is written generically (`ScanFlow.ENROLL/CHECKIN/CHECKOUT`)
    since iOS reuses one `ScanViewController` for all three flows — only
    `ENROLL` is wired from Dashboard so far; Checkin/Checkout still show
    "coming soon". Shows connection/battery status and a single-tag scan.
  - **Important behavioral gap**: iOS starts scanning when the reader's
    physical trigger key is pressed. The Chainway SDK doesn't expose that key
    as a callback (see `ChainwayRfidManager`'s TODO), so `ScanActivity`
    auto-starts one scan attempt on connect and lets the status card be
    tapped to retry, as a stand-in. Fix properly once the trigger key is wired.
  - **No reader connected at all** (emulator, or no C72 on hand yet): tapping
    the card in a `BuildConfig.DEBUG` build simulates a tag read (a random
    `3000E2`-prefixed EPC, one every tap so repeat enrollments don't collide)
    instead of doing nothing, and the "not connected" subtitle says so. This
    mirrors iOS's own `ScanViewController.initiateReviewMode()`, which injects
    a hardcoded EPC the same way for App Store review — same problem, same
    fix, just triggered by `DEBUG` instead of a `Session.isInReview` flag.
    Lets the rest of the flow (Enter Details → pickers → Summary → the real
    `POST /api/assets`) be exercised without hardware. Release builds get none
    of this — real `ScanFlow` behavior only.
  - **Card colors — settled, after going back and forth twice**: every screen
    in this flow (Scan, Enter Details, Summary) uses the literal
    `ScanViewController.swift`/`EnterAssetDetailsViewController.swift`/
    `AssetDetailSummaryViewController.swift` pattern —
    **primary-blue full screen, white rounded card(s) inset, dark/black text
    and gray-bordered white inputs on the card(s), white large-title text
    above the card, per-state colored status title on Scan
    (`ScanState.color` — green/gray/orange/red/blue) — a peach `PrimaryButton`
    outside the card.** That's what's built now: `bg_white_card.xml` for
    every card (`cardContainer` in Scan, the fields card in Enter Details,
    each `item_summary_card.xml`), `FieldLabel` (in `styles.xml`) black text.
    A screenshot of the Scan screen briefly suggested the opposite (white
    screen, blue card) and the whole flow was flipped to match it — then a
    second, clearer screenshot of the Enter Details screen confirmed the
    literal-source version instead, and everything was flipped back. If a
    future screenshot ever contradicts this again, treat the *source code*
    as the tiebreaker unless the screenshot is unambiguous and recent — the
    first flip cost real churn. The Category/User picker screens
    (`activity_picker_list.xml`) were never part of this — they're plain
    white modal list screens, not a card-on-page layout.
  - **Bug fix — "can't select a category" (reported after real on-device
    testing)**: root cause was a cache race, not a click-handling bug.
    `CategoryPickerActivity`/`UserPickerActivity` only ever read from
    `Storage`'s local cache, which is populated by Dashboard's `fetchCategories()`/
    `fetchUsers()` — a fire-and-forget coroutine started in `onCreate()` — and
    wiped by `Storage.clear()` on every logout. A user who logs back in and
    reaches Enroll quickly enough (or hits a slow/failed network request)
    beats that fetch and lands on the picker with an empty cache — the
    RecyclerView renders nothing, no error, no indication anything's wrong.
    This existed structurally in iOS too (`DashboardViewController.fetchCategories()`
    is the same fire-and-forget pattern over `Storage.shared`), just less
    likely to be hit. Fixed two ways: (1) both Dashboard fetches now log +
    Toast on `ApiResult.Error` instead of silently leaving the cache stale,
    matching the pattern `updateDashboardInfoViews()` already used; (2) both
    pickers now check their cache on `onCreate()` and, if it's empty (or for
    categories, has no selectable subcategories), fetch directly via
    `safeApiCall` themselves — with a `ProgressBar` while loading and a real
    error message (`pick_category_load_failed`/`pick_user_load_failed`, both
    `%1$s`-formatted with `ApiError.message`) if that fetch also fails,
    instead of a silent blank list. A genuine improvement over iOS's
    Dashboard-only fetch, not just a port of it.
    **Actual root cause, confirmed via this diagnostic**: not the cache race
    above — the fetch was succeeding fine (no `pick_category_load_failed`
    Toast), but returned categories with zero subcategories under them. The
    empty-state message was extended once more to name the fetched
    categories directly (`pick_category_empty`, e.g. "Fetched: Weapons,
    Ammunition") specifically to tell "these are genuinely empty parent
    groups" apart from "these are already leaf items miscategorized as
    headers" without needing device logcat access — and it showed the
    former: the account's backend data simply had no subcategories assigned
    to either category yet. Confirmed resolved once subcategories were added
    on the backend. Not an app defect at all, on either platform — iOS's
    identical two-level header/subcategory picker logic would have shown the
    exact same empty result against this same data. Worth remembering if
    this recurs for a different account/environment: check the empty-state
    message's category names before assuming a client-side bug.
  - The `device_connected`/`device_disconnected`/`device_battery`/
    `device_overheated` iOS illustrations were **deliberately not ported** —
    they're literal photos of the old AsReader gun accessory, which doesn't
    exist on the C72 (see the `armory-android-hardware-swap` memory). Those 4
    states use hand-built vector icons instead — `res/drawable/ic_status_{connected,
    not_connected,low_battery,overheated}.xml` — each a colored circle + white
    glyph (checkmark/X/battery/warning-triangle) with the status color baked
    in, matching the same two-tone visual language as the ported `scan`/
    `success`/`failed` illustrations (hardware-agnostic, so those **were**
    ported: `res/drawable-nodpi/scan_frame.png`, `status_success.png`,
    `status_failed.png`). An earlier pass used tiny tinted framework
    "presence" dot icons here, which looked poor stretched up to a 120dp
    status graphic — replaced for that reason.
  - **`DEVICE_CONNECTED`/`SCANNING` icon replaced again, this time on direct
    user feedback against the real device** ("check mark ... not nice, try
    something other than that"). Went through two iterations: first a
    hand-built signal-wave glyph in the same colored-circle style as the
    other `ic_status_*` icons (kept the checkmark's problem though — it
    still didn't read as "device online" distinctly from `SUCCESS`'s own
    checkmark) — then, after the user shared a reference screenshot from a
    different app showing a scanner+tag illustration, a full illustration:
    **`res/drawable-nodpi/device_scan_tag.png`**, generated with a one-off
    Pillow script (not checked in) that draws a small RFID-tag glyph (blue
    rounded rect, white chip square, sine-wave antenna squiggle) and orange
    signal-wave arcs directly onto a copy of the existing Search/Locate
    device illustration (`device.png`), canvas widened 200px to the right so
    the tag sits **in front of the device's antenna module** — the first
    version placed it floating above the device instead, corrected on
    follow-up feedback ("show rfid tag infront of the devcie"). Used for
    both `DEVICE_CONNECTED` and `SCANNING` (the "present a tag" states);
    `NOT_CONNECTED`/`LOW_BATTERY`/`OVERHEATED`/`FAILED` keep their existing
    distinct icons since those convey specific problem states an
    illustration wouldn't. `ic_status_connected.xml` (the intermediate
    signal-wave attempt) and the `scan_frame.png` reference were both
    removed from `ScanUiState`; `scan_frame.png` itself was deliberately
    left in `res/` rather than deleted — it's a legitimate ported iOS asset,
    just currently unreferenced.
  - **Battery row removed** ("showing battery percentage in the scan view is
    no need since device battery status showing in the top") — `batteryRow`/
    `batteryText` deleted from `activity_scan.xml`, along with
    `ScanActivity`'s `updateBatteryLevel()` and every call site
    (`onCreate`/`onReaderConnected`/`onReaderDisconnected`). `tagText`
    re-anchored to `cardContainer`'s bottom directly instead of
    `batteryRow`'s (now-gone) top. `LocateAssetActivity`/
    `TakeInventoryActivity` keep their own battery rows — this request was
    specifically about the Scan screen (Enroll/Checkin/Checkout), not those.
  - `EnterAssetDetailsActivity`/`AssetSummaryActivity` pass data forward via
    plain `Intent` extras (name/description/categoryId/tag/serialNumber/
    assignedUserId), not Parcelable objects — avoids needing `@Parcelize`
    setup for a first pass; revisit if more screens need to pass whole model
    objects around.
  - `EXTRA_ASSIGNED_USER_ID` uses `-1` as a "no user" sentinel since Intent
    extras have no nullable `Int`.

- **Checkout flow** — `ui/checkout/EnterCheckoutAssetDetailsActivity.kt` +
  `ui/checkout/CheckoutAssetDetailsSummaryActivity.kt`. Real port of
  `Controllers/Checkout/EnterCheckoutAssetDetailsViewController.swift` +
  `CheckoutAssetDetailsSummaryViewController.swift`. `ScanActivity` (already
  generic) now handles `ScanFlow.CHECKOUT` for real instead of a "coming
  soon" toast; Dashboard's Checkout button launches it.
  - `EnterCheckoutAssetDetailsActivity` fetches the scanned asset
    (`GET /api/assets/by/epc/{tag}`), shows it via a new shared
    **`view_asset_header.xml`** + **`AssetHeaderBinder.kt`** (bindable from
    any screen that needs to show an asset's icon/name/category/serial —
    Checkin will want this too), then collects User (required),
    Password (required), and No. of Mags / Total Weight (required only when
    `category.isWeightRequired`).
  - **Icon substitution, later reversed** (same root cause as the Dashboard
    menu icons, same fix): iOS's per-category icon (`AssetDetailsView.swift` —
    handgun/rifle/machine gun images, white-tinted) initially hit the same
    `qlmanage`-flattens-transparency problem (confirmed: all rendered solid
    white, invisible) and was replaced with a colored circle + first-letter
    stand-in via `AssetCategoryVisuals.kt` + `bg_circle.xml`. Re-rasterizing
    with PyMuPDF proved these were genuine white glyphs on a transparent
    background, same as the menu icons — now ported as real assets
    (`res/drawable-nodpi/handgun.png`, `rifle.png`, `machinegun.png`).
    `view_asset_header.xml` shows both an `ImageView` (`assetIconImage`) and
    the old `TextView` letter (`assetIconLetter`) stacked in the same
    `FrameLayout`, on top of the colored `bg_circle` background
    (`assetIconBackground`, still tinted per-category exactly as before);
    `AssetCategoryVisuals.iconFor(categoryName)` returns the matching
    drawable for "hand gun"/"rifle"/"machine gun" and `null` otherwise, and
    `AssetHeaderBinder.bindAssetHeader()` shows the `ImageView` when an icon
    resource exists and falls back to the letter circle when it doesn't —
    keeping the graceful degradation for unrecognized category names that
    iOS's hardcoded fallback-to-handgun-icon doesn't have.
  - **Access check ported as-is**: `checkForAccessLevel()` blocks checkout
    with an "Access Denied" alert when the category requires admin privilege
    and the logged-in user's `userGroup.id != 1`. That magic number `1` comes
    straight from the iOS source with no further documentation of what group
    it names — flagged here in case that ever needs revisiting.
  - `CheckoutAssetDetailsSummaryActivity` **reuses** Enroll's
    `activity_asset_summary.xml` + `item_summary_card.xml` layouts rather
    than duplicating a near-identical screen — it just overrides the title
    and button text in code (`R.string.checkout_title` /
    `checkout_summary_button`) and populates different cards. Same
    Intent-extras-not-Parcelable pattern as Enroll; `-1`/empty-string
    sentinels again stand in for "not entered" on the nullable mags/weight.

- **Checkin flow** — `ui/checkin/EnterCheckinAssetDetailsActivity.kt` +
  `ui/checkin/CheckinAssetDetailsSummaryActivity.kt` +
  `ui/checkin/CheckinAuthActivity.kt`. Real port of
  `Controllers/Checkin/EnterCheckinAssetDetailsViewController.swift` +
  `CheckinAssetDetailsSummaryViewController.swift` + `CheckinAuthView.swift`.
  `ScanActivity` now handles `ScanFlow.CHECKIN` for real; Dashboard's Checkin
  button launches it. This is the most involved flow ported so far — a
  3-step API sequence, not just fetch-then-submit:
  1. `EnterCheckinAssetDetailsActivity` — same shape as Checkout's equivalent
     screen (fetch asset by tag, User/Password/Mags/Weight), minus the
     assigned-user prefill (iOS doesn't do that for checkin either).
  2. `CheckinAssetDetailsSummaryActivity` calls `POST .../return` first (plain
     checkin). A **409 conflict** means the backend detected a discrepancy
     (e.g. a mag/weight mismatch) — prompts for a note via an inline
     `AlertDialog` + `EditText` (mirrors iOS's `showdiscrepancyAlert`), then
     retries as `POST .../approve-and-return` with that note attached.
  3. If *that* comes back **403 unauthorized** (the logged-in user can't
     self-approve), hands off to `CheckinAuthActivity` — a mini re-login
     screen (asset header on a muted-blue top section, Username/Password/
     Proceed on a primary-blue bottom section, reusing Login's field styling
     and TertiaryButton-style button) where an *admin* authenticates and the
     approve-and-return is retried under their token via
     `checkinApproveAssetWithSuperUser` — added to `ArmoryApiService` for
     this. That admin login is **never** written to `SessionManager` — it
     doesn't replace the current user's session, only authorizes this one
     call. Needed a small `AuthInterceptor` change: it now leaves a request's
     `Authorization` header alone if one is already set (via `@Header` on the
     Retrofit method), instead of always overwriting it with the session
     token — confirmed against iOS's own `checkinApproveAssetWithSuperUser`,
     which builds its request manually for the same reason and (unlike the
     non-superuser version) has no 401/token-refresh retry logic; neither
     does ours.
  - **`AssetHeaderBinder.kt` gained a primitives overload** — `Summary` and
    `CheckinAuthActivity` only carry a few of an asset's fields forward via
    `Intent` extras (not a whole `Asset`/`Category` object), so
    `bindAssetHeader(view, name, categoryName, serialNumber)` was added
    alongside the original `bindAssetHeader(view, asset)`, which now just
    delegates to it.

- **Search flow** — `ui/search/SearchAssetActivity.kt` (list) +
  `SearchAssetDetailsActivity.kt` (reuses the summary layout again — 4th time
  now, after Enroll/Checkout/Checkin) + `LocateAssetActivity.kt`. Real port of
  `Controllers/Search/SearchAssetViewController.swift` +
  `SearchAssetDetailsViewController.swift` + `LocateAssetViewController.swift`.
  Dashboard's Search button launches it.
  - **`SearchAssetActivity`**: a search box (simple cancel-and-relaunch
    coroutine debounce standing in for iOS's `SearchTextField` library
    callback) over a paginated `RecyclerView` list. Rows reuse
    **`view_asset_header.xml`**/`bindAssetHeader` again — iOS's `AssetCell`
    turned out to have the exact same icon/name/category/serial shape as the
    asset header used elsewhere, just as a list row instead of a card. New
    **`AssetListAdapter.kt`** wraps that reuse for `RecyclerView`.
  - **`LocateAssetActivity`** is the first screen to exercise
    `RfidManager.setMask()`/`EpcMaskParameters` for real (masks continuous
    inventory to one specific EPC) and the first to use genuine **continuous**
    scanning rather than single-shot Tag mode — `startRfidScanning()` in
    search mode calls the Chainway SDK's real `startInventoryTag()`
    continuous-callback API, so unlike `ScanActivity` it does **not** depend
    on the physical trigger key and needed no debug-simulation stand-in.
  - **Two new hand-rolled custom views**, replacing iOS's third-party
    libraries rather than pulling in Android equivalents: **`SignalGaugeView.kt`**
    stands in for `FDBarGauge` (a vertical bar, colored red→yellow→green by
    continuous interpolation rather than iOS's exact hard-threshold logic —
    a deliberate simplification, not a bug); **`PulseView.kt`** stands in for
    the `Pulsator` library (expanding/fading rings via `ValueAnimator`).
    Neither pulls in a new dependency.
  - **`device.png` was deliberately not ported at first** — same root cause
    as the AsReader hardware images: it's a literal photo of a phone clipped
    into the old accessory sled (see the `armory-android-hardware-swap`
    memory). A plain colored dot (tinted primary blue at runtime,
    `bg_circle.xml`) still sits at the center of the pulse rings — that part
    wasn't changed.
  - **A user-supplied cartoon illustration of the Chainway-style handheld now
    lives at `res/drawable-nodpi/device.png`** (512×512, RGB, white
    background — resized down from a ~1250px source to match the scale of
    the project's other `drawable-nodpi` assets) and **is wired into
    `activity_locate_asset.xml`**: `gaugeRow` (shown only while
    connected/scanning, alongside `statusIcon`'s mutually-exclusive
    disconnected/error states) changed from a single horizontal row to a
    vertical `LinearLayout` — a new 120dp `deviceImage` `ImageView` on top,
    with the original signal-gauge + pulse-ring row unchanged below it. No
    Kotlin changes were needed: `gaugeRow`'s existing show/hide logic in
    `LocateAssetActivity.render()` already covers the new child. The image's
    baked-in white background blends into `bg_white_card` seamlessly, so no
    transparency work was needed here (unlike the PDF-sourced icons — see
    [[armory-android-icon-transparency-bug]]).
  - **Signal gauge made taller** (28×120dp → 28×220dp in
    `activity_locate_asset.xml`) on direct feedback that it was too short to
    read at a glance. `SignalGaugeView.onDraw()` already draws relative to
    its own measured `width`/`height` with no hardcoded pixel assumptions,
    so this was a pure layout change.
  - **Real bug, found from "it's always showing green, does it actually
    detect the tag?"**: confirmed via a live `getRssi()` logcat capture that
    the raw RSSI values coming off the reader were genuine and varying
    (-43 to -80 dBm) — the parsing (`toFloatOrNull()`) was never the
    problem. The actual bug: `LocateAssetActivity.onTagRead()` fed *every*
    tag's RSSI into the gauge with no check that the tag matched the one
    being located. The hardware mask (`setMask()`) is supposed to filter
    continuous inventory to just that EPC, but `setFilter()` — the native
    call underneath it — has the same reliability issue as
    `stopInventory()` (see the trigger-key section above), so a read for a
    *different*, unrelated tag could still arrive and stomp the gauge with
    irrelevant RSSI. The same logcat capture caught it directly: two
    different EPCs interleaved in one session (a leftover test tag from
    earlier work, plus the actual target). Fixed with a client-side
    safety-net filter — `onTagRead()` now returns early unless
    `tag == this.tag` — regardless of whether the hardware mask holds.
  - **Also found while investigating the above, from "first time it stuck
    somewhere, second time it worked"**: `LocateAssetActivity` had the exact
    same duplicate-schedule race already found and fixed in `ScanActivity`
    (`onReaderConnected()` firing twice within ~100ms of the screen opening,
    each independently launching a delayed setup coroutine with no guard
    against a previous one still pending) — it just never got the same fix
    when `ScanActivity`'s was made. Fixed the same way: a `scanLoopJob: Job?`
    field, cancelled before each relaunch. Also applied to
    `TakeInventoryActivity.onConnected()`, which has the identical pattern
    (lower-impact there, since it doesn't auto-start an actual scan — but
    the same redundant-scheduling bug regardless).
  - **Asset header added to the top of the screen** on direct request
    ("show the asset which going to search... like the entries showing in
    the search view before the locate screen") — `activity_locate_asset.xml`
    now `<include>`s `view_asset_header.xml` between the title and the main
    card, same placement convention as Checkout/Checkin's entry-details
    screens. Needed a small data-flow gap closed first: `LocateAssetActivity`
    previously only received `EXTRA_TAG` — no name/category/serial — because
    `SearchAssetDetailsActivity` never forwarded them to it (and didn't even
    have a category name on hand itself; `SearchAssetActivity.openDetails()`
    wasn't passing `asset.category.name` to it at all). Threaded through:
    `SearchAssetActivity` → `SearchAssetDetailsActivity`
    (new `EXTRA_ASSET_CATEGORY_NAME`) → `LocateAssetActivity` (new
    `EXTRA_ASSET_NAME`/`EXTRA_ASSET_CATEGORY_NAME`/`EXTRA_ASSET_SERIAL_NUMBER`,
    bound via the existing `bindAssetHeader()` primitives overload — no new
    binder code needed, just wiring).
  - **Proximity beep added** ("small beep that signaling asset nearby...
    the beep delay getting lower when asset near by" — a metal-detector/
    geiger-counter feel, not a single beep-on-find): `onTagRead()` now calls
    `beepForProximity(rssi)`, which linearly interpolates the interval
    between beeps from `BEEP_INTERVAL_MAX_MS` (700ms, weakest in-range
    signal) down to `BEEP_INTERVAL_MIN_MS` (90ms, strongest — close to the
    reader's own ~80ms read cadence, so it reads as near-continuous right on
    top of the tag) based on the same RSSI ratio the gauge uses.
    `SignalGaugeView.setRssi()`'s ratio math was pulled out into a public
    companion `ratioFor(rssi)` so both the gauge and the beep timing derive
    from one source instead of two copies of `MIN_RSSI`/`MAX_RSSI`. Uses its
    own `ToneGenerator` on `STREAM_MUSIC` (same audibility fix as
    `ScanActivity`'s beep — `STREAM_NOTIFICATION` proved inaudible on the
    C66 despite actually playing).
  - **Real bug, root-caused and fixed (not just mitigated) — from "sometimes
    its stuck when tag very near by, after that need to stop and start it
    again"**: first confirmed live via logcat while reproducing on-device — a
    `setFilter() err :-1`, then zero further `setInventoryCallback` firings
    of any kind, ever. Initially mitigated with a watchdog (see below) before
    a follow-up report narrowed it precisely: **"this happens when the first
    time locating asset, then stop and start won't give that issue"** — i.e.
    not proximity-related at all, and not random; specifically the *first*
    locate attempt on a given screen visit, never a manual restart after
    that. That pattern pointed straight at a real race:
    `ChainwayRfidManager.startRfidScanning()`'s continuous-mode branch called
    `reader.startInventoryTag()` **directly on the caller's thread**, while
    `setSearchRfidMode()`/`clearMask()`/`setMask()` all queue their
    `setPower()`/`setFilter()` calls asynchronously on `uartExecutor` (the
    single-thread executor added for the earlier main-thread-blocking fix).
    `LocateAssetActivity.setTagToSearch()` calls `setMask()` then
    `startRfidScanning()` back to back with no wait — so on a screen's first
    locate, `startInventoryTag()` could fire on the UART before the
    just-queued `setFilter()` had actually finished, exactly the one
    sequence (`setSearchRfidMode` → `clearMask` → `setMask` →
    `startRfidScanning`) a manual Stop/Start never repeats (that just calls
    `stopRfidScanning()`/`startRfidScanning()` alone). **Fixed** by routing
    `startInventoryTag()` through `uartExecutor` too, so it's correctly
    serialized after any pending `setFilter()`/`setPower()` instead of racing
    them.
    - **Watchdog kept as a safety net, not removed**: `LocateAssetActivity`
      still tracks `lastReadAtMs` (updated on *any* tag read, even a
      mismatched one) and restarts scanning if silent for >6s while
      `isLocating`, checked every 2s. The race above was a confirmed, fully
      explained cause of *this specific* symptom, but the underlying
      `stopInventory()`/`setPower()`/`setFilter()` native reliability issue
      is still real and could plausibly cause the same kind of silent stop
      through some other path — the watchdog costs little (a restart is
      cheap and harmless when nothing's wrong) and stays as insurance.
  - **`SignalGaugeView` rebuilt as a segmented meter**, on the user sharing an
    actual reference screenshot of the real iOS screen: a stack of discrete
    lit/unlit blocks (10 segments) with hard red→yellow→green color zones at
    the 35%/80% thresholds, not the smooth single-fill continuous-gradient
    bar this originally shipped as. That continuous version was a
    deliberate, explicitly-documented simplification (see the class's own
    old doc comment) rather than a bug — replaced now that the segmented
    look was specifically requested against a real reference rather than
    guessed at. `ratioFor(rssi)` (used by both the gauge and
    `LocateAssetActivity`'s proximity beep) is unchanged; only `onDraw()`'s
    rendering changed, from one `fillRect` to a loop of `SEGMENT_COUNT`
    rounded rects, each independently colored by whether the current ratio
    reaches that segment and which zone that segment's own range falls in
    (not the live ratio's zone — a segment's color is fixed by its position,
    same as the reference's bar).
  - **Follow-up polish on the same segmented gauge, from "first and last
    segments are not full, make it full, position the gauge in the middle,
    and device bottom of the gauge with pulse"** — three fixes together:
    1. Each segment was rounding all four of its own corners, which made the
       top and bottom segments look visibly smaller than the middle ones
       (no adjacent segment to visually mask the rounding on their outer
       edge). Switched to `Path.addRoundRect()` with per-corner radii — only
       the true outer corners of the whole stack (top of the top segment,
       bottom of the bottom one) are rounded now; every segment fills its
       full allotted rect.
    2. `gaugeRow` was a horizontal row (gauge on the left, device+pulse on
       the right) — not what the reference shows. Rebuilt as a `FrameLayout`
       with the gauge `layout_gravity="top|center_horizontal"` and the
       device+pulse group `layout_gravity="bottom|center_horizontal"`, so
       the device sits right at the gauge's base with the pulse rings around
       it, both horizontally centered — matching the reference layout, not
       the earlier side-by-side one.
    3. The plain `centerDot` (a tinted `bg_circle`, standing in for "the
       device" inside the pulse rings) was replaced by the real
       `device.png` illustration, shrunk to 48dp to fit inside the pulse
       ring's 100dp frame — the same asset already used elsewhere on this
       screen, just resized and moved rather than a new one. `LocateAssetActivity`'s
       `centerDot` tinting code (and its now-unused `GradientDrawable`
       import) was removed since there's no plain dot left to tint.
  - **`device.png` given real transparency** — moving it inside the pulse
    rings exposed that it was still opaque with a baked-in white background
    (noted as fine at the time it was first added, since it then only ever
    sat on the white card — see the earlier note in this section), which
    showed as an ugly white square over the blue pulse rings/card once
    repositioned. Went looking for an alpha-preserving source first: the
    newly-added iOS `armoryapp/.../Assets.xcassets/device.imageset/device.png`
    turned out to be a *different* image entirely — the literal old AsReader
    gun-sled photo this project deliberately never ported (see
    [[armory-android-hardware-swap]]), not this illustration, just
    coincidentally sharing a filename. No usable source existed, so fixed by
    flood-filling the existing PNG's background directly (from all four
    corners, since none of the illustration's own content touches the image
    border) to transparent, `thresh=30` to catch anti-aliased near-white
    edge pixels without eating into the actual dark device body — confirmed
    via pixel inspection afterward (corner alpha=0, screen-area alpha=255).
    `device_scan_tag.png` (`ScanActivity`'s illustration, generated by
    drawing onto a *copy* of `device.png` before this change) is unaffected
    — it's a separate, already-baked static file, still opaque, still fine
    since it only ever sits on `ScanActivity`'s white card.

- **Inventory flow** — `ui/inventory/TakeInventoryActivity.kt` +
  `InventoryBreakdownActivity.kt`. Real port of
  `Controllers/Inventory/TakeInventoryViewController.swift` +
  `InventoryBreakdownViewController.swift`. Dashboard's Inventory button
  launches it — **all five Dashboard flows are now real**, none left
  showing the placeholder "coming soon" toast.
  - **`TakeInventoryActivity`**: continuous, *unmasked* inventory (collects
    every unique EPC seen, unlike `LocateAssetActivity`'s single-EPC mask),
    reusing the same `PulseView` from the `search` package rather than
    duplicating it. **Behavioral difference from `LocateAssetActivity`,
    confirmed against source rather than assumed from similarity**: scanning
    here does *not* auto-start on connect — iOS only swaps to the live-count/
    pulse view from the Start button's own tap handler, so the status card
    stays visible (with its text updated to "Scanning…") until the user
    manually taps Start. Mirrored via a `renderStatus()`
    (always-available status display) separate from `startScanning()`/
    `stopScanning()` (the manual-tap-gated view swap).
  - **One deliberate deviation**: iOS updates the status *text* on an error/
    overheat mid-scan without forcing the view back from the live-count/pulse
    display — which can leave that message invisible while still "scanning".
    This port forces back to the status card in that case instead, so the
    problem is actually seen.
  - **Pulse rings' `centerDot` swapped for the device illustration**, same
    fix as `LocateAssetActivity` and for the same request ("make the
    inventory view pulsar same with the device illustration") — the plain
    tinted `bg_circle` dot replaced by the (now-transparent, see the Search
    flow's device.png note) `device.png`, 48dp inside a 100dp pulse frame
    (was 120dp, matching the `LocateAssetActivity` sizing this mirrors).
    `centerDot`'s `GradientDrawable` tinting code and that now-unused import
    removed from `TakeInventoryActivity.kt` the same way.
  - **Real bug — stale "Stop" button/view after Retake**: `stopScanning()`
    correctly sets `isScanning = false` before navigating to
    `InventoryBreakdownActivity`, but never reset `actionButton`'s text or
    called `renderStatus()` to swap the view back from the scanning row to
    the status card — harmless while `InventoryBreakdownActivity` is on top,
    but `TakeInventoryActivity` itself is never finished (just pushed under
    it), so "Retake" finishing back to it resumed the exact stale UI Stop
    left behind: button still reading "Stop", scanning row still showing,
    despite `isScanning` already being correctly `false`. Fixed by adding
    `actionButton.setText(R.string.locate_start_button)` and
    `renderStatus(ScanUiState.DEVICE_CONNECTED)` to `stopScanning()` itself
    — `renderStatus()` already checks `!isScanning` to decide status-card vs.
    scanning-row visibility, so this was enough; no new state needed.
  - **`InventoryBreakdownActivity`**: an Available/Missing/Checkedout toggle
    row (green/red/yellow text, `inventory_group_button_background.xml`
    selector for the primary/secondary fill) over a paginated list — the 4th
    reuse of `AssetListAdapter`/`view_asset_header.xml`, this time with a
    **deliberate no-op click callback**, since iOS's table here has no
    `didSelectRowAt` (rows aren't tappable, only pagination-on-scroll is
    wired) — confirmed from source rather than assumed from the Search list
    looking similar. "Retake" just `finish()`es back to `TakeInventoryActivity`,
    matching iOS's simple `coordinator?.goBack()`.
  - **One text fix, not a faithful port**: iOS's Available-button label has a
    typo ("Avaiable"); this port spells it correctly. A deliberate content
    fix, not an oversight — flagging it here in case anyone diffs against
    the iOS strings and wonders why they don't match exactly.
  - **Same narrow-3-button text-fit issue as Dashboard's menu row, same
    fix**: these 3 toggle buttons force a count + label onto two lines via a
    literal `\n` in the format strings (`inventory_group_*_format`) plus
    `android:lines="2"`, but at the original 14sp the label itself
    ("Available"/"Checkouts", 9 chars) didn't fit its own dedicated line and
    got clipped mid-word ("Availabl", "Checko") rather than wrapping further
    — there's nowhere left to wrap to. First dropped to the same uniform 8sp
    `Dashboard`'s menu buttons had landed on — fit, but reported as "too
    small to read." Then padding-trimmed (`paddingStart`/`paddingEnd="2dp"`,
    `insetLeft`/`insetRight="0dp"` — `MaterialButton`'s default style eats a
    fair amount of the already-narrow 1/3-width space) with a fixed 12sp —
    better, but "Available"/"Checkouts" (still the longest labels) started
    clipping again while the shorter one had room to spare. **Final fix,
    same as Dashboard's menu row**: fixed uniform **9sp** (padding trim
    kept) — the exact value `Dashboard`'s equivalent 9-character-longest-
    label row measured (via a temporary `button.textSize` diagnostic log,
    not another guess) as the largest size autosize would resolve for a
    label that long in this same button width. Applied directly here rather
    than re-running the same diagnostic, since this screen only reachable
    via a full scan flow — same button width, same label length, reasonable
    to extrapolate. Worth remembering for any future narrow-button
    label-fit issue in this app: measure the real resolved size with a
    throwaway autosize + logging pass, then hard-code that — don't keep
    guessing static values, and don't ship autosize itself if uniform sizing
    across a row matters more than each button's own best-fit.

## Next steps

All five Dashboard flows (Enroll, Checkout, Checkin, Search, Inventory) are
now built. What's left is polish and hardware verification, not new screens:

1. ~~Replace the 5 menu-button icons, logout icon, and the 3 category icons~~
   **Done** — all 9 were recovered by re-rasterizing their source PDFs with
   PyMuPDF instead of `qlmanage` (see the Dashboard and Checkout notes above);
   they were genuine transparent-background artwork the whole time, not
   unusable white templates. `device.png` (Search's connection-status
   graphic) is a different problem and still unresolved: it's a photo of the
   iOS-only AsReader Bluetooth accessory, which has no Chainway C72
   equivalent since the C72's UHF reader is built into the handheld itself —
   see [[armory-android-hardware-swap]] — so there's no source asset to
   recover for it, real or otherwise; it needs a new C72-appropriate graphic
   (or none at all) rather than a rasterization fix.
2. ~~Verify `POWER_GAIN_MIN`/`MAX` and wire up the trigger key against a
   physical C72~~ **Both done**, `POWER_GAIN_MAX` confirmed live (`MIN`
   still an educated guess — see the constant's own doc in
   `ChainwayRfidManager.kt` for why `setPower()` couldn't be tested at the
   low end): a one-off diagnostic `getPower()` call read back **30** right
   after a real connect on the C66, exactly matching the existing
   `POWER_GAIN_MAX` constant — real confirmation, not a guess, that 30 is a
   genuine in-range firmware value. (Took two attempts to even get this
   reading: `RfidManager` is constructed lazily off the first screen that
   touches it, not at app startup, so the first diagnostic build's logging
   silently never ran until navigating into Enroll.)
   `ScanActivity` now overrides `dispatchKeyEvent()` and calls `attemptScan()`
   when the keycode is in `TRIGGER_KEYCODES`, verified end-to-end (built +
   installed real debug APKs, pressed the physical trigger, confirmed it
   scans a tag in Enroll) on two different physical units. Gated on
   `RfidManager.enableTriggerButton`, which `ScanActivity` now sets
   `true`/`false` in `onCreate`/`onDestroy` — previously declared on the
   interface but never read anywhere. The tap-to-retry card and the
   debug-only simulated-tag path were deliberately **kept**, not removed, as
   fallbacks. `LocateAssetActivity`/`TakeInventoryActivity` still need no
   such fix — both already use real continuous scanning, unaffected by this.
   - **Keycode is per-device, not universal — confirmed twice now**: a C72
     (`HC720A210800130`, model `c72e`) fires **`293`** (scanCode `186`); a
     C66 (`1c46e4a0`, model `C66`) fires **`294`** (`WindowManager`'s
     `interceptKeyTi` log, not a plain `KeyEvent(...)` line — same fix,
     different log line to grep for). Both found live via `adb logcat` during
     an actual physical press — a vendor config app's displayed values
     (`280`/`139`, from the C72's "keyboardemulator" system settings app,
     UHF > KeyCode) turned out *not* to match either real device once that
     app's own legacy scanning service is disabled. `TRIGGER_KEYCODES` in
     `ScanActivity` is a `Set<Int>` of every confirmed value (`293`, `294`,
     plus `280`/`139` kept as unconfirmed fallbacks) rather than one
     "correct" code, and its doc says what to do for the next device model:
     `adb logcat` for `keyCode=` or `interceptKeyTi` during a real press,
     add the number to the set. Lesson holds for both: don't trust a vendor
     config app's displayed values over a live capture on the exact device.
   - **Beep added, then its stream corrected on a second device**:
     `ScanActivity` fires a short `ToneGenerator` tone from `onTagRead`,
     since `RFIDWithUHFUART` (the class this app uses) has no beep/buzzer API
     at all — confirmed by disassembling the SDK jar; other reader classes in
     the same SDK (`BluetoothReader`/`RFIDWithUHFUSB`/etc.) do have one, but
     not this one. Originally used `STREAM_NOTIFICATION`; on the C66 that
     produced a real `AudioTrack` with frames actually delivered (confirmed
     in logcat) but **no audible sound** — this hardware doesn't route that
     stream to a speaker path as reliably as `STREAM_MUSIC`, which rugged/
     kiosk-style Android devices almost always wire to the loudest built-in
     speaker. Switched to `STREAM_MUSIC` and confirmed audible on the C66.
     The device-level `com.rscja.scanner` "keyboardemulator" app has its own
     `Success Sound` toggle, but that's a separate legacy keyboard-wedge
     mode, disabled on both units tested and unrelated to this app's direct
     SDK usage either way.
   - **Two real bugs found via live hardware testing, not just review —
     "can't scan another tag" / "scanning automatically without touching
     anything"**. Both were confirmed with hard evidence (`adb logcat`), not
     guessed, and the first fix attempt only addressed the second, smaller
     issue — worth recording both since the symptom looked identical from
     the outside.
     1. **Primary cause, found second but responsible for most of the
        symptom — a duplicate-schedule race, worse on a screen's *second*
        visit**: instrumented logging (`Log.w`, since this device's default
        `log.tag` is `I` and silently drops `Log.d`) showed the reader's
        `ConnectionStatusCallback` reporting `CONNECTED` **twice** within
        ~100ms of `ScanActivity` opening — once from `onCreate`'s own
        `isDeviceConnected` check, once from a genuine second callback
        (reproducible, worse on re-entry). `beginScanningAfterDelay()` had no
        guard against being scheduled twice, so each `CONNECTED` independently
        launched its own 1.5s-delayed `attemptScan()` coroutine — two scan
        loops running concurrently, racing on the shared reader/
        `isScanInFlight` state. **Fixed** by tracking the coroutine as a
        `Job` (`scanLoopJob`) and cancelling any still-pending one before
        launching a new one. Confirmed fixed on-device: reopening Enroll
        repeatedly now produces exactly one automatic scan per visit (the
        existing, intentional auto-scan-on-connect — see the class doc), not
        a repeating stream.
     2. **Secondary, still-real issue — `stopInventory()` reliably fails**:
        separately confirmed in `adb logcat`'s raw SDK output
        (`DeviceAPI`/`DeviceAPI_UHF` tags): after a tag read,
        `ChainwayRfidManager.stopRfidScanning()``s call to `uhf.stopInventory()`
        showed `UHF_StopGet: send STOP cmd` retried 5 times then
        `UHF_StopGet: stop failed` / `stopInventory() err :-1`, and
        `setPower()`/`setFilter()` (called from `setTagRfidMode()`/
        `clearMask()`) failed the same way. This is an SDK/hardware
        reliability issue outside app control — not fixed, and likely needs
        vendor support or a different SDK version. Mitigated defensively:
        `ScanActivity` tracks `isScanInFlight` (true only while a scan was
        actually requested) and `onTagRead()` ignores any read that arrives
        while it's false, so a stray/leftover read doesn't re-beep or
        re-populate the UI even if this failure mode recurs.
        - **Follow-up, also fixed**: these native calls block the calling
          thread for ~2-2.5s while internally retrying before failing, every
          time on this hardware. `setPower()`/`setFilter()`/`stopInventory()`
          used to run directly on whatever thread called them — often the
          main thread, since `ScanActivity.beginScanningAfterDelay()` calls
          `setTagRfidMode()`/`clearMask()` from a `lifecycleScope.launch{}`
          coroutine (`Dispatchers.Main` by default), and `onTagRead()` calls
          `stopRfidScanning()` directly. Reported by the user as "a small
          delay" between scans on a C66 — matches the `InputDispatcher: spent
          2500+ms processing input event` warnings seen in logcat throughout
          this work. **Fixed**: `ChainwayRfidManager`'s single-thread
          executor (renamed `uartExecutor`, previously `singleReadExecutor`
          and only used for `inventorySingleTag()`) now runs *every* UART
          command — `setPower`, `setFilter`, `stopInventory`, and the
          existing `inventorySingleTag` — so none of them block a caller's
          thread, while FIFO ordering on that single thread still preserves
          correctness (e.g. `setPower` completes before the read that
          depends on it). Confirmed fixed on the C66 — the felt delay between
          scans dropped noticeably.
3. ~~Generate real launcher icons (`android:icon`/`roundIcon`) from the iOS
   `AppIcon` source~~ **Done.** The `armoryapp/` iOS source tree (referenced
   throughout this doc) wasn't actually present in this environment when this
   item was first picked up — it was added mid-task, at which point the real
   master (`Assets.xcassets/AppIcon.appiconset/1024.png`, 1024×1024, opaque
   white background per iOS convention — no transparency, since iOS icons
   don't support it) became available and was used instead of an
   approximation. Legacy `mipmap-{m,h,xh,xxh,xxx}hdpi/ic_launcher(_round).png`
   are that master resized full-bleed at each density (48/72/96/144/192px),
   matching the actual designed icon rather than a re-inset guess. Adaptive
   icons (`mipmap-anydpi-v26/ic_launcher(_round).xml`, API 26+) use a
   `@color/white` background plus a separate transparent-background
   foreground layer (`ic_launcher_foreground.png` per density) generated from
   the already-ported `drawable-nodpi/logo.png` (verified to be the same
   mark, alpha-trimmed, inset to the standard 66% adaptive-icon safe zone).
   `AndroidManifest.xml`'s `<application>` now sets
   `android:icon="@mipmap/ic_launcher"` / `android:roundIcon="@mipmap/ic_launcher_round"`,
   replacing the TODO. Generated via a one-off Python/Pillow script (not
   checked in — this was a single generation pass, not a repeatable build
   step); rebuilt, installed, and confirmed rendering correctly (proper mark
   on a white adaptive background, OS-applied squircle mask) via the device's
   own App Info screen on the C66.
4. ~~This project has never been run through an actual Gradle build in this
   environment~~ **No longer true** — `gradle :app:assembleDebug` (system
   Gradle 9.6.1, `JAVA_HOME` pointed at a JDK 17 install; the project's own
   Gradle 8.9 wrapper jar still isn't generated) built and installed cleanly
   on a real C72 for the trigger-key work above. Worth noting for next time:
   the system default JDK here is 26, which fails AGP's `androidJdkImage`
   transform (`jlink` against `android-34`'s `core-for-system-modules.jar`) —
   needs JDK 17, not whatever `java_home` defaults to.
