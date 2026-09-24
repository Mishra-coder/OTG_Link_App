#!/usr/bin/env bash
# One-time setup: allows OTG Link to write the OTG setting.
# Requires USB debugging and the app installed on the connected phone.
set -euo pipefail

PKG="com.otgon.keeper"
PERMISSION="android.permission.WRITE_SECURE_SETTINGS"

adb get-state >/dev/null

is_granted() {
    adb shell dumpsys package "$PKG" | grep -q "$PERMISSION: granted=true"
}

if is_granted; then
    echo "$PKG already has $PERMISSION."
    exit 0
fi

if ! adb shell pm grant "$PKG" "$PERMISSION"; then
    cat >&2 <<'MSG'

Grant failed. On Oppo / Realme / OnePlus, turn on
  Settings → Developer options → Disable permission monitoring
then run this script again. You can turn it back off afterwards.
MSG
    exit 1
fi

is_granted
echo "Granted $PERMISSION to $PKG."
