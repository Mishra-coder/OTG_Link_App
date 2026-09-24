package com.otgon.keeper.core;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

/**
 * The manufacturer's "OTG connection" toggle.
 *
 * <p>On ColorOS-based phones (Oppo, Realme, OnePlus) the toggle is backed by a plain
 * {@link Settings.Global} value, and the system's own USB service reacts to changes of it.
 * Writing it therefore has the same effect as the user flipping the switch in Settings.
 * See {@code docs/research-notes.md} for how the key was identified.
 */
public final class OtgSwitch {

    /** ColorOS / Realme UI / OxygenOS. "1" = on, "0" = off; absent on other vendors. */
    public static final String KEY = "persist.sys.oplus.otg_support";

    private static final String ON = "1";

    private OtgSwitch() {}

    public static Uri uri() {
        return Settings.Global.getUriFor(KEY);
    }

    /** True if this phone has the switch we know how to control. */
    public static boolean isSupported(ContentResolver resolver) {
        return Settings.Global.getString(resolver, KEY) != null;
    }

    public static boolean isOn(ContentResolver resolver) {
        return ON.equals(Settings.Global.getString(resolver, KEY));
    }

    /** WRITE_SECURE_SETTINGS can only be granted over ADB (or by a privileged helper). */
    public static boolean canWrite(Context context) {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Switches OTG on if it is off.
     *
     * @return true if the setting was written
     */
    public static boolean ensureOn(Context context) {
        ContentResolver resolver = context.getContentResolver();
        if (isOn(resolver) || !canWrite(context)) {
            return false;
        }
        return Settings.Global.putString(resolver, KEY, ON);
    }
}
