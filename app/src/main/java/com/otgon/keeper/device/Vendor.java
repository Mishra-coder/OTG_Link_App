package com.otgon.keeper.device;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.otgon.keeper.R;

import java.util.Locale;

/**
 * Phone manufacturers, and where each one hides the "let this app run in the background"
 * controls. Every vendor names and places these differently, and most lock the screens
 * to their own system apps, so we deep-link where we can and fall back to App info.
 */
public enum Vendor {

    /** Oppo, Realme, OnePlus (ColorOS). Auto-launch and power screens are not exported. */
    OPLUS(R.array.background_steps_oplus),

    VIVO(R.array.background_steps_vivo,
            new ComponentName("com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            new ComponentName("com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),

    XIAOMI(R.array.background_steps_xiaomi,
            new ComponentName("com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity")),

    SAMSUNG(R.array.background_steps_samsung),

    OTHER(R.array.background_steps_default);

    /** String-array resource with the steps to show under "Open settings". */
    public final int backgroundSteps;
    private final ComponentName[] autoStartScreens;

    Vendor(int backgroundSteps, ComponentName... autoStartScreens) {
        this.backgroundSteps = backgroundSteps;
        this.autoStartScreens = autoStartScreens;
    }

    public static Vendor current() {
        return from(Build.MANUFACTURER, Build.BRAND);
    }

    static Vendor from(String manufacturer, String brand) {
        String id = (manufacturer + " " + brand).toLowerCase(Locale.ROOT);
        if (id.contains("oppo") || id.contains("realme") || id.contains("oneplus")) return OPLUS;
        if (id.contains("vivo") || id.contains("iqoo")) return VIVO;
        if (id.contains("xiaomi") || id.contains("redmi") || id.contains("poco")) return XIAOMI;
        if (id.contains("samsung")) return SAMSUNG;
        return OTHER;
    }

    /** Brand as shown to the user, e.g. "Realme". */
    public static String displayBrand() {
        String brand = Build.BRAND;
        if (brand == null || brand.isEmpty()) return Build.MANUFACTURER;
        return brand.substring(0, 1).toUpperCase(Locale.ROOT) + brand.substring(1);
    }

    /** Opens the closest reachable screen for the background / auto-start controls. */
    public void openBackgroundSettings(Context context) {
        for (ComponentName screen : autoStartScreens) {
            if (tryStart(context, new Intent().setComponent(screen))) return;
        }
        tryStart(context, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + context.getPackageName())));
    }

    private static boolean tryStart(Context context, Intent intent) {
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
