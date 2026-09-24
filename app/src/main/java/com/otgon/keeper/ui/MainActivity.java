package com.otgon.keeper.ui;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.Toast;

import com.otgon.keeper.R;
import com.otgon.keeper.core.KeeperState;
import com.otgon.keeper.core.OtgSwitch;
import com.otgon.keeper.device.Vendor;
import com.otgon.keeper.service.OtgKeeperService;

/** Setup checklist. Each step shows whether it's done and, if not, how to fix it. */
public class MainActivity extends Activity {

    private static final int STEP_COUNT = 6;

    private LinearLayout content;
    private KeeperState state;
    private Vendor vendor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = new KeeperState(this);
        vendor = Vendor.current();

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad / 2, pad, pad * 3 / 2);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        // targetSdk 35+ draws edge-to-edge; keep content clear of the system bars.
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        setContentView(scroll);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        render();
    }

    private void render() {
        content.removeAllViews();
        SetupViews ui = new SetupViews(this, content);
        ui.header(R.drawable.logo, getString(R.string.app_name));

        if (!OtgSwitch.isSupported(getContentResolver())) {
            ui.statusCard(false, getString(R.string.unsupported_title),
                    getString(R.string.unsupported_body, Vendor.displayBrand(), Build.MODEL));
            return;
        }

        if (!OtgKeeperService.isRunning()) state.checkForUnexpectedDeath();

        String brand = Vendor.displayBrand();
        boolean permission = OtgSwitch.canWrite(this);
        boolean enabled = state.isEnabled();
        boolean running = enabled && OtgKeeperService.isRunning();
        boolean notifications = getSystemService(NotificationManager.class)
                .areNotificationsEnabled();
        boolean batteryExempt = getSystemService(PowerManager.class)
                .isIgnoringBatteryOptimizations(getPackageName());
        boolean restricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && getSystemService(ActivityManager.class).isBackgroundRestricted();
        boolean killed = state.wasKilled();
        boolean background = !restricted && !killed && state.isBackgroundConfirmed();
        boolean bootStart = state.startedAfterLastBoot();

        int done = count(permission, running, notifications, batteryExempt, background, bootStart);

        if (running && OtgSwitch.isOn(getContentResolver())) {
            ui.statusCard(true, getString(R.string.status_on_title),
                    getString(R.string.status_on_body));
        } else {
            ui.statusCard(false, getString(OtgSwitch.isOn(getContentResolver())
                            ? R.string.status_temporary_title : R.string.status_off_title),
                    getString(R.string.status_incomplete_body));
        }

        ui.section(getString(R.string.setup_section),
                getString(R.string.setup_progress, done, STEP_COUNT));

        // 1. One-time ADB grant
        SetupViews.Step step = ui.step(1, permission, getString(R.string.step_permission_title),
                getString(permission ? R.string.step_permission_done
                        : R.string.step_permission_todo));
        if (!permission) {
            String command = grantCommand();
            step.guide(getString(vendor == Vendor.OPLUS
                                    ? R.string.step_permission_guide_debugging_oplus
                                    : R.string.step_permission_guide_debugging),
                            getString(R.string.step_permission_guide_connect),
                            getString(R.string.step_permission_guide_run))
                    .code(command)
                    .button(getString(R.string.action_copy_command), true, v -> copy(command));
        }

        // 2. Keeper on/off
        Switch keeperSwitch = new Switch(this);
        keeperSwitch.setChecked(enabled);
        keeperSwitch.setEnabled(permission);
        keeperSwitch.setOnCheckedChangeListener((view, on) -> {
            state.setEnabled(on);
            if (on) OtgKeeperService.start(this);
            else OtgKeeperService.stop(this);
            content.postDelayed(this::render, 300);
        });
        ui.step(2, running, getString(R.string.step_keeper_title),
                        getString(!permission ? R.string.step_keeper_blocked
                                : running ? R.string.step_keeper_running
                                : R.string.step_keeper_off))
                .trailing(keeperSwitch);

        // 3. Notification (a foreground service needs one to stay alive)
        step = ui.step(3, notifications, getString(R.string.step_notification_title),
                getString(notifications ? R.string.step_notification_done
                        : R.string.step_notification_todo));
        if (!notifications) {
            step.button(getString(R.string.action_allow_notification), true,
                    v -> startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));
        }

        // 4. Android battery optimisation
        step = ui.step(4, batteryExempt, getString(R.string.step_battery_title),
                getString(batteryExempt ? R.string.step_battery_done
                        : R.string.step_battery_todo));
        if (!batteryExempt) {
            step.button(getString(R.string.action_turn_off), true,
                    v -> startActivity(new Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName()))));
        }

        // 5. Vendor background activity / auto-launch
        if (background) {
            ui.step(5, true, getString(R.string.step_background_title),
                    getString(R.string.step_background_done, brand));
        } else {
            int reason = restricted ? R.string.step_background_restricted
                    : killed ? R.string.step_background_killed
                    : R.string.step_background_todo;
            ui.step(5, false, getString(R.string.step_background_title), getString(reason, brand))
                    .guide(backgroundGuide())
                    .button(getString(R.string.action_open_settings), true,
                            v -> vendor.openBackgroundSettings(this))
                    .button(getString(R.string.action_confirm_background), false, v -> {
                        state.confirmBackground();
                        render();
                    });
        }

        // 6. Survives a reboot
        int bootText = bootStart ? R.string.step_restart_done
                : state.hasEverStartedOnBoot() ? R.string.step_restart_failed
                : R.string.step_restart_todo;
        ui.step(6, bootStart, getString(R.string.step_restart_title), getString(bootText));

        ui.footer(getString(R.string.footer_supported));
    }

    private String[] backgroundGuide() {
        String[] vendorSteps = getResources().getStringArray(vendor.backgroundSteps);
        String[] all = new String[vendorSteps.length + 1];
        all[0] = getString(R.string.step_background_open);
        System.arraycopy(vendorSteps, 0, all, 1, vendorSteps.length);
        return all;
    }

    private String grantCommand() {
        return "adb shell pm grant " + getPackageName() + " "
                + Manifest.permission.WRITE_SECURE_SETTINGS;
    }

    private void copy(String text) {
        getSystemService(ClipboardManager.class)
                .setPrimaryClip(ClipData.newPlainText("adb command", text));
        Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show();
    }

    private static int count(boolean... flags) {
        int n = 0;
        for (boolean flag : flags) if (flag) n++;
        return n;
    }
}
