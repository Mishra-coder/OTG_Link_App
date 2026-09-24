<p align="center">
  <img src="docs/assets/logo.png" width="128" alt="OTG Link logo" />
</p>

<h1 align="center">OTG Link</h1>

<p align="center">
  Keeps USB OTG switched on for Oppo, Realme and OnePlus phones, so USB-C earphones work the moment you plug them in.
</p>

---

## The problem

Phones without a headphone jack rely on USB-C earphones. Most of these earphones contain a
small digital DAC, which the phone treats as a USB device. The phone can only talk to it
while it is acting as a USB host (**OTG**).

ColorOS-based phones (Oppo, Realme, OnePlus) ship with an **"OTG connection" toggle that
turns itself off after 10 minutes** without a connected device. The result:

1. Plug in earphones: silence.
2. Open Settings → Additional settings → OTG connection → turn it on.
3. Unplug for 10 minutes, and the toggle is off again. Repeat from step 1.

The timeout can't be configured. Vivo and iQOO phones reportedly behave the same way with a
5-minute timeout.

## How it works

The toggle is backed by a single `Settings.Global` value, `persist.sys.oplus.otg_support`
(`1` = on). Writing it has the same effect as the user flipping the switch. See
[docs/research-notes.md](docs/research-notes.md) for how this was found and verified.

OTG Link runs a small foreground service that puts the value back whenever the system
clears it:

```
 ColorOS USB service                OTG Link (foreground service)
 ───────────────────                ─────────────────────────────
 10 min idle ──► otg_support = 0 ──► ContentObserver fires
                                     └─► Settings.Global.putString(otg_support, "1")
                 otg_support = 1 ◄──┘
 OTG on again; earphones work when plugged in
```

Three triggers make sure a change is never missed:

| Trigger | Purpose |
|---|---|
| `ContentObserver` on the setting | Primary path. Reacts within a second. |
| `ACTION_SCREEN_ON` receiver | Catches anything missed while the device dozed. |
| 60 s poll | Last-resort safety net. |

Writing to `Settings.Global` requires `WRITE_SECURE_SETTINGS`. A normal install can't get
this permission. It is granted **once** over ADB and survives reboots and updates.

## Supported devices

| Vendor | Status |
|---|---|
| Realme (Realme UI / ColorOS 16, Android 16) | **Verified** on Narzo 70 (RMX5003) |
| Oppo, OnePlus (ColorOS / OxygenOS) | Expected to work (same OS and key); not yet verified |
| Vivo, iQOO | Not supported: different, unknown key ([how to add](#adding-a-new-vendor)) |
| Other brands | Not needed; OTG is not time-limited there |

On unsupported phones the app says so, instead of failing silently.

## Getting started

### Requirements

- JDK 17
- Android SDK with platform 36
- `adb` on your `PATH`, and a phone with USB debugging enabled

### Build and install

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

ColorOS shows an "Install via USB" prompt on the phone. `adb install` waits until someone
taps Install.

### One-time permission grant

```sh
./scripts/grant-permission.sh
```

On Oppo, Realme and OnePlus, first enable **Developer options → Disable permission
monitoring**. Otherwise the grant fails with a `SecurityException`. You can disable it again
afterwards.

### Finish setup on the phone

Open **OTG Link** and follow the checklist. Every step shows ✓ when done:

1. One-time permission (from the grant above)
2. Keep OTG always on (the service switch)
3. Notification: required for a foreground service to stay alive
4. Battery optimisation: exempt the app from Doze
5. Background activity: vendor-specific, with steps shown for the detected brand
6. Works after restart: passes once the app has auto-started after a reboot

## Project structure

```
.
├── app/
│   ├── build.gradle.kts
│   ├── lint.xml
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/otgon/keeper/
│       │   │   ├── core/
│       │   │   │   ├── OtgSwitch.java         # read/write the vendor OTG setting
│       │   │   │   └── KeeperState.java       # persisted state, boot + kill detection
│       │   │   ├── device/
│       │   │   │   └── Vendor.java            # brand detection, settings deep links
│       │   │   ├── service/
│       │   │   │   ├── OtgKeeperService.java  # foreground service that keeps OTG on
│       │   │   │   └── BootReceiver.java      # restarts the service after reboot/update
│       │   │   └── ui/
│       │   │       ├── MainActivity.java      # setup checklist (state → views)
│       │   │       └── SetupViews.java        # card/step view builders
│       │   └── res/                           # strings, colours (light/dark), icons
│       └── test/                              # JVM unit tests
├── docs/
│   ├── research-notes.md                      # how the OTG key and ColorOS limits were found
│   └── assets/                                # source artwork
├── scripts/
│   ├── grant-permission.sh                    # one-time ADB grant
│   └── find-otg-key.sh                        # discover the OTG key on a new vendor
├── gradle/libs.versions.toml                  # dependency versions
└── .github/workflows/android.yml              # CI: lint, tests, debug APK
```

## Design decisions

**No third-party dependencies.** The app is a setting watcher plus one screen. Using the
platform APIs keeps the APK around 250 KB and the attack surface minimal.

**Views built in code.** The setup screen is a pure function of device state and is rebuilt
on every `onResume`. `SetupViews` keeps that readable without XML layouts or data binding.

**Honest status for things we can't read.** ColorOS stores "Allow background activity" in
private system storage (see [research notes §5](docs/research-notes.md#5-the-allow-background-activity-state-cant-be-read)).
Instead of guessing, the app asks the user to confirm the setting. `KeeperState` then watches
for the system killing the service: the service was alive, never reached `onDestroy`, and no
reboot or update happened since. If that occurs, the step goes back to "needs attention".

**Deep links with fallbacks.** Vendor screens for auto-start are mostly locked to system
apps. `Vendor` tries known components where they are exported (Vivo, Xiaomi) and otherwise
opens App info, with brand-specific steps shown alongside.

**User-facing text in resources.** Every string is in `strings.xml`, so translations (Hindi
first) need no code changes.

## Known limitations

- **ADB grant required.** This is the biggest barrier for non-technical users. See the
  roadmap item on Shizuku below.
- **Play Store policy.** Lint flags `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (`BatteryLife`).
  Before publishing, either justify it under Play's acceptable-use list or switch to
  `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`. The same applies to the `specialUse`
  foreground service declaration.
- **Battery.** OTG stays powered while idle. The cost appears small but has not been measured.
- **Vendor behaviour can change.** A ColorOS update may rename the key or add its own
  "never turn off" option.

## Verification status

| Check | Result |
|---|---|
| Key discovery and live toggle over ADB | Verified |
| Earphones play after writing the key | Verified |
| Service re-enables OTG after a forced `0` | Verified (< 1 s) |
| Real 10-minute system timeout, end to end | Pending |
| Auto-start after reboot (checklist step 6) | Pending |
| Oppo / OnePlus devices | Pending |
| Unit tests (`KeeperState`, `Vendor`) | 7 passing |

## Roadmap

- [ ] **Shizuku support**: grant the permission on-device over wireless debugging, with no
      computer needed. This is the prerequisite for a public release.
- [ ] Vivo / iQOO support, once the key is found with `scripts/find-otg-key.sh`
- [ ] Hindi translation
- [ ] Measure the battery impact of keeping OTG on
- [ ] Release signing and Play Store listing

## Adding a new vendor

1. Connect the phone with USB debugging on.
2. Run `./scripts/find-otg-key.sh` and flip the OTG switch when prompted.
3. If a key changes, check that `adb shell settings put …` really toggles OTG. Then
   generalise `OtgSwitch.KEY` into a per-vendor lookup, and add the vendor's steps to
   `strings.xml`.
4. If nothing changes, the vendor keeps the switch outside Settings, and this approach won't
   work for it.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `pm grant` → `SecurityException` | Developer options → Disable permission monitoring → ON |
| `adb install` hangs | Confirm the "Install via USB" prompt on the phone |
| OTG turns off again after some time | Check checklist steps 3–5; the service was probably killed |
| Step 6 fails after a reboot | Allow auto launch / background activity (step 5), then reboot again |
| Logs | `adb logcat -s OtgKeeper` |

## Tech stack

Java 17 · Android SDK 36 (min 26) · Android Gradle Plugin 8.12 · Gradle 8.13 (Kotlin DSL,
version catalog) · JUnit 4 · GitHub Actions
