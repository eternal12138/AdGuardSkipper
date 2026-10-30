# Build verification

- Version: **1.6.0** (versionCode 8, debug applicationId `com.adguardskipper.debug`)
- Build date: 2026-10-30 (Asia/Shanghai)
- Toolchain: JDK 17.0.2, Gradle 8.7, AGP 8.5.2, Kotlin 1.9.24, Android SDK 34 / Build-Tools 34.0.0
- Command executed successfully:
  - `gradle :app:assembleDebug`
- The debug APK was installed and launched on an Android API 37 emulator (1080 × 2400, 420 dpi). The launcher icon, Android 12+ system splash, Compose showcase, dashboard, lists, logs and scrolled dashboard were reviewed on-device.
- `:app:lintDebug` was attempted, but this sandbox could not resolve the test-only `junit` / lint transitive artifacts because Maven Central TLS handshakes were rejected; the production `assembleDebug` path, resource merge and Kotlin compile all succeeded. No lint result is claimed for this build.

## Artifact

| Artifact | Size | SHA-256 |
|---|---:|---|
| `dist/CialloSkipper-1.6.0-debug.apk` | 17,288,655 bytes | `BCA087F682315A4A23D007E79A95A53ED5FE0E67A8C2BFF06756C013FA492CA1` |

- `apksigner verify`: **Verifies**, APK Signature Scheme **v2: true**, signer `CN=Android Debug`.
- `aapt2 dump badging`: versionCode 8, versionName `1.6.0-debug`, label `Ciallo～(∠・ω< )⌒☆跳广`.

## UI / UX checks

- Manifest application label is exactly **Ciallo～(∠・ω< )⌒☆跳广**.
- Adaptive icon uses the first illustration's character on a sky-blue gradient, white sticker and cobalt ring. Square legacy icons keep the full illustration and wordmark. The foreground keeps the subject inside the platform's circular safe area.
- Android 12+ system splash uses a dedicated character-safe-zone mark and `#DDF4FF` background.
- Compose showcase uses the second "跳广" illustration as a white-bordered poster card with a loading strip, rotating star and progress bar.
- Dashboard, lists, logs and dialogs share the Ciallo poster style: sky background, cobalt outlines, sticker cards, pixel tags, coral/lemon accents, floating rounded navigation bar.
- Status bar and navigation bar explicitly request dark icons so they remain readable over the fixed light theme.

## Core behavior

- No changes were made to `service/` (accessibility engine, skip engine, jump interceptor, keep-alive) or `data/` (DataStore, repositories, models).
- Only Compose UI files, UI colour constants in the layout-inspector overlay, theme/resources, launcher icons and splash assets were changed.

## Screenshots

- [`design/screenshots/splash.png`](../design/screenshots/splash.png) — Compose showcase / loading
- [`design/screenshots/system_splash.png`](../design/screenshots/system_splash.png) — Android 12+ system splash
- [`design/screenshots/dashboard.png`](../design/screenshots/dashboard.png) — dashboard
- [`design/screenshots/dashboard_scrolled.png`](../design/screenshots/dashboard_scrolled.png) — advanced tools / rules
- [`design/screenshots/lists.png`](../design/screenshots/lists.png) — whitelist / blacklist
- [`design/screenshots/logs.png`](../design/screenshots/logs.png) — empty log state
- [`design/screenshots/launcher_icon.png`](../design/screenshots/launcher_icon.png) — launcher / app-drawer icon
- [`design/screenshots/icon_preview.png`](../design/screenshots/icon_preview.png) — icon size sheet
