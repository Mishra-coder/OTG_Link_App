package com.otgon.keeper.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.SystemClock;

/**
 * Persistent state of the keeper, plus the health signals the setup screen reports on.
 *
 * <p>Some vendor settings — notably ColorOS's "Allow background activity" — live in private
 * system storage that no third-party app can read. For those we rely on the user's
 * confirmation, and revoke it automatically if we catch the system killing the service.
 */
public final class KeeperState {

    // Stored names predate the class; keep them so existing installs don't lose their state.
    private static final String PREFS = "otg_on";

    private static final String ENABLED = "keeper_enabled";
    private static final String LAST_BOOT_START = "last_boot_start";
    private static final String BACKGROUND_CONFIRMED = "bg_confirmed";
    private static final String KILLED = "killed";
    private static final String SERVICE_ALIVE = "alive";
    private static final String SERVICE_ALIVE_SINCE = "alive_since";

    private final Context context;
    private final SharedPreferences prefs;

    public KeeperState(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- user intent ----

    public boolean isEnabled() {
        return prefs.getBoolean(ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(ENABLED, enabled).apply();
    }

    // ---- start-on-boot ----

    public void recordBootStart() {
        prefs.edit().putLong(LAST_BOOT_START, System.currentTimeMillis()).apply();
    }

    /** True if the boot receiver ran during the current boot. */
    public boolean startedAfterLastBoot() {
        return prefs.getLong(LAST_BOOT_START, 0) > bootTimeMillis();
    }

    public boolean hasEverStartedOnBoot() {
        return prefs.getLong(LAST_BOOT_START, 0) > 0;
    }

    // ---- vendor background permission ----

    public boolean isBackgroundConfirmed() {
        return prefs.getBoolean(BACKGROUND_CONFIRMED, false);
    }

    public void confirmBackground() {
        prefs.edit().putBoolean(BACKGROUND_CONFIRMED, true).putBoolean(KILLED, false).apply();
    }

    public boolean wasKilled() {
        return prefs.getBoolean(KILLED, false);
    }

    // ---- service liveness ----

    public void onServiceStarted() {
        checkForUnexpectedDeath();
        prefs.edit()
                .putBoolean(SERVICE_ALIVE, true)
                .putLong(SERVICE_ALIVE_SINCE, System.currentTimeMillis())
                .apply();
    }

    public void onServiceStopped() {
        prefs.edit().putBoolean(SERVICE_ALIVE, false).apply();
    }

    /**
     * Call when the service is known not to be running. If it was last seen alive and never
     * reached onDestroy, the system killed it: withdraw the background confirmation so the
     * setup screen asks the user to fix it.
     */
    public void checkForUnexpectedDeath() {
        boolean died = isUnexpectedDeath(
                prefs.getBoolean(SERVICE_ALIVE, false),
                prefs.getLong(SERVICE_ALIVE_SINCE, 0),
                bootTimeMillis(),
                lastUpdateTimeMillis());
        if (died) {
            prefs.edit()
                    .putBoolean(KILLED, true)
                    .putBoolean(BACKGROUND_CONFIRMED, false)
                    .putBoolean(SERVICE_ALIVE, false)
                    .apply();
        }
    }

    /**
     * A service that was alive and never cleanly stopped is gone. Reboots and app updates
     * also end it without onDestroy, so those are not counted as kills.
     */
    static boolean isUnexpectedDeath(boolean wasAlive, long aliveSince,
                                     long bootTime, long lastUpdateTime) {
        return wasAlive && aliveSince >= bootTime && aliveSince >= lastUpdateTime;
    }

    private static long bootTimeMillis() {
        return System.currentTimeMillis() - SystemClock.elapsedRealtime();
    }

    private long lastUpdateTimeMillis() {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }
}
