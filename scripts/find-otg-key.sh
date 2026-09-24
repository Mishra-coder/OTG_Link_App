#!/usr/bin/env bash
# Finds the settings key a vendor uses for its OTG toggle, by diffing every
# Settings.Global/System/Secure value before and after the switch is flipped.
# This is how persist.sys.oplus.otg_support was found; use it to add a new vendor.
set -euo pipefail

snapshot() {
    for ns in global system secure; do
        adb shell settings list "$ns" | sed "s/^/$ns: /"
    done | sort
}

adb get-state >/dev/null
before=$(mktemp)
after=$(mktemp)
trap 'rm -f "$before" "$after"' EXIT

snapshot >"$before"
read -rp "Flip the OTG switch in the phone's Settings, then press Enter... "
snapshot >"$after"

# Clock-like values change on their own; hide them.
if ! diff "$before" "$after" | grep '^[<>]' | grep -viE '_time=|timestamp'; then
    echo "No setting changed: this vendor keeps the OTG switch outside Settings."
fi
