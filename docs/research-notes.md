# Research notes

Findings from reverse-engineering the OTG toggle on ColorOS. Everything in this document was
observed on a single device over ADB. Re-verify on other models and OS versions before relying
on it.

| | |
|---|---|
| Device | Realme Narzo 70 (`RMX5003`) |
| OS | Realme UI / ColorOS 16.0.5 (`RMX5003_16.0.5.1010(EX01)`), Android 16 (SDK 36) |
| Date | 2026-09-24 |

## 1. Where the OTG switch lives

Method (now [`scripts/find-otg-key.sh`](../scripts/find-otg-key.sh)): dump every
`Settings.Global`, `Settings.System` and `Settings.Secure` value, flip OTG on in Settings, dump
again, then diff the two dumps.

Out of 2,053 values, exactly one changed, apart from a clock-style counter:

```
global: persist.sys.oplus.otg_support=0  →  1
```

- Despite the `persist.sys.` prefix, this is **not** a system property. `getprop` has no OTG
  entry. It is an ordinary `Settings.Global` row.
- No timeout-related key exists (searched for `timeout`, `600000` and `otg`). The 10-minute
  auto-off lives inside the vendor's USB service and can't be configured.

## 2. Writing the key really switches OTG on

With OTG off:

```sh
adb shell settings put global persist.sys.oplus.otg_support 1
```

- The toggle in Settings flipped to ON while the screen was open, and stayed ON after
  reopening the screen.
- USB-C earphones (a digital DAC) plugged in afterwards played audio. The OS reacts to the
  value, not only to the UI.
- Below the toggle, the Settings UI says: *"Automatically turns off if not used for 10
  minutes."*

**Conclusion:** watching the key and writing `1` back whenever it drops to `0` is enough.
With the keeper service running, forcing the key to `0` over ADB was reverted in under a
second:

```
I OtgKeeper: OTG re-enabled (setting changed)
```

## 3. Granting `WRITE_SECURE_SETTINGS`

On ColorOS, `pm grant` fails by default:

```
java.lang.SecurityException: grantRuntimePermission: Neither user 2000 nor current process
has android.permission.GRANT_RUNTIME_PERMISSIONS.
```

Fix: **Developer options → Disable permission monitoring** → ON, then grant again. The
permission survives turning that option back off, and survives app updates installed with
`adb install -r`.

## 4. Which settings screens a third-party app can open

The goal was to deep-link users straight to the "background activity" and "auto launch"
switches. Tested with `adb shell am start`, which is at least as privileged as a normal app:

| Target | Result |
|---|---|
| `com.oplus.battery/com.oplus.startupapp.view.StartupAppListActivity` (auto-launch list) | Denied, requires `oplus.permission.OPLUS_COMPONENT_SAFE` |
| action `oplus.intent.action.power_control` (`PowerUsageModelActivity`) | Denied, requires `com.oplus.permission.safe.SETTINGS` |
| `com.oplus.battery/com.oplus.powermanager.fuelgaue.PowerControlActivity` (per-app "Power consumption control") | Not exported |
| `android.settings.APPLICATION_DETAILS_SETTINGS` | Works: App info → *Battery usage* opens `PowerControlActivity` |
| `android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL` | Works: AOSP-style page with a single "Allow background usage" toggle |
| `android.settings.APP_BATTERY_SETTINGS` | Not resolvable |

The app links to **App info**. From there, *Battery usage* leads to the ColorOS page with
the three real options: *Allow background activity*, *Smart mode* and *Restrict background
activity*.

## 5. The "Allow background activity" state can't be read

The app wanted to show a green tick once the user picked *Allow background activity*. Values
compared between *Allow* and *Smart mode*:

- `cmd appops get <pkg>`: `RUN_ANY_IN_BACKGROUND: allow` in both
- `dumpsys deviceidle whitelist`: whitelisted in both captures
- `am get-standby-bucket`: `5` (exempt) in both
- all `Settings.*` tables: no relevant change
- `dumpsys` of `athenaservice`, `oplus_freeze`, `osensemanager`, `power_monitor` and
  `oplus.hans.IHansComunication`: no entry for the package

ColorOS keeps this choice in `com.oplus.battery`'s private storage. One reading taken right
after the mode changed showed the app off the battery-optimisation whitelist, and a later
reading showed it back on. That wasn't reproduced cleanly, so the app doesn't rely on it.

**Design consequence:** the app asks the user to confirm the setting, and withdraws that
confirmation automatically if it detects the system killing the service
(`KeeperState.checkForUnexpectedDeath`).

## 6. Other ColorOS behaviour worth knowing

- `adb install` of an update waits on an on-device "Install via USB" confirmation. The command
  blocks until someone taps Install.
- The per-app page is reachable only through App info → Battery usage. On this build it has no
  separate "Auto launch" toggle.

## 7. Other vendors (not verified on a device)

- **Vivo / iQOO:** OTG reportedly turns off after 5 minutes idle
  ([vivo FAQ](https://www.vivoglobal.ph/questionlist/otg/)). The key is unknown. Run
  `scripts/find-otg-key.sh` on a Vivo phone to find it.
- **OnePlus / Oppo:** same 10-minute behaviour
  ([OnePlus Community](https://community.oneplus.com/threads/otg-auto-turns-off-after-10-miniutes.661270/),
  [XDA](https://xdaforums.com/t/disable-automatically-turn-off-when-not-in-use-for-10-minutes.3744626/)).
  The same key is expected, since they share ColorOS, but this is unverified.
