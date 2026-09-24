package com.otgon.keeper.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.otgon.keeper.core.KeeperState;
import com.otgon.keeper.core.OtgSwitch;

/** Restarts the keeper after a reboot or an app update, if the user had it switched on. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        KeeperState state = new KeeperState(context);
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            state.recordBootStart();
        }
        if (state.isEnabled() && OtgSwitch.canWrite(context)) {
            OtgKeeperService.start(context);
        }
    }
}
