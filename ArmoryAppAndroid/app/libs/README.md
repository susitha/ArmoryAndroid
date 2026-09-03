# Chainway SDK

`DeviceAPI_ver20251103_release.aar` — the C72's Android Studio API package from
https://www.chainway.net/Support/Info/10, already wired into
`app/build.gradle.kts`.

Its package is `com.rscja.deviceapi`; the class used by
[`ChainwayRfidManager`](../src/main/java/com/cenango/fetchcicg/rfid/ChainwayRfidManager.kt)
is `RFIDWithUHFUART` (a singleton via `RFIDWithUHFUART.getInstance()`), which
covers serial/UART-connected UHF modules — the C72's built-in reader is one of
those. Full class docs are in `API_Ver20251103/doc/` alongside wherever this
`.aar` was downloaded from (not copied into this repo — it's a large HTML
Javadoc tree, regenerate/re-download it if needed).

If a future device swaps to a different Chainway connection type (BLE, USB,
network), the equivalent classes are `RFIDWithUHFBLE`, `RFIDWithUHFUSB`, or
`RFIDWithUHFA8NetWork` etc. — same SDK, same `UhfBase` parent, so
`RfidManager`'s interface shouldn't need to change, just the implementation.
