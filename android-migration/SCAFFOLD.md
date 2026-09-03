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
2. Verify `POWER_GAIN_MIN`/`MAX` and wire up the trigger key against a
   physical C72 (see the two TODOs in `ChainwayRfidManager.kt`) — this also
   unblocks giving `ScanActivity` real continuous-scan/retry behavior instead
   of its current tap-to-retry stand-in, and would let the debug-only
   simulated-tag path in `ScanActivity` be removed once real hardware is
   always available for testing. `LocateAssetActivity`/`TakeInventoryActivity`
   need no such fix — both already use real continuous scanning.
3. Generate real launcher icons (`android:icon`/`roundIcon` — see the TODO in
   `AndroidManifest.xml`) from the iOS `AppIcon` source.
4. This project has never been run through an actual Gradle build in this
   environment (no Android SDK available here) — every file has been
   reviewed by hand and cross-checked (ids, strings, colors, drawables, model
   field names) but a real build, and testing against a physical C72, are
   still outstanding.
