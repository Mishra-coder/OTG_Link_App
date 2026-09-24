package com.otgon.keeper.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.otgon.keeper.R;
import com.otgon.keeper.core.KeeperState;
import com.otgon.keeper.core.OtgSwitch;
import com.otgon.keeper.ui.MainActivity;

/**
 * Foreground service that keeps OTG on.
 *
 * <p>ColorOS switches OTG off after 10 minutes without a USB device. The service observes the
 * setting and writes it back to "on" as soon as the system flips it. A screen-on receiver and
 * a slow poll cover the unlikely case of a missed observer callback.
 */
public class OtgKeeperService extends Service {

    private static final String TAG = "OtgKeeper";
    private static final String CHANNEL_ID = "keeper";
    private static final int NOTIFICATION_ID = 1;
    private static final long POLL_INTERVAL_MS = 60_000;

    /** Whether the service is alive in this process. Read by the setup screen. */
    private static volatile boolean running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private KeeperState state;

    private final ContentObserver otgObserver = new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
            enforce("setting changed");
        }
    };

    private final BroadcastReceiver screenOnReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            enforce("screen on");
        }
    };

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            enforce("poll");
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    public static void start(Context context) {
        context.startForegroundService(new Intent(context, OtgKeeperService.class));
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, OtgKeeperService.class));
    }

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        state = new KeeperState(this);
        state.onServiceStarted();
        startInForeground();

        getContentResolver().registerContentObserver(OtgSwitch.uri(), false, otgObserver);
        registerReceiver(screenOnReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
        handler.post(poll);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        enforce("start");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(poll);
        getContentResolver().unregisterContentObserver(otgObserver);
        unregisterReceiver(screenOnReceiver);
        state.onServiceStopped();
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void enforce(String trigger) {
        try {
            if (OtgSwitch.ensureOn(this)) {
                Log.i(TAG, "OTG re-enabled (" + trigger + ")");
            }
        } catch (SecurityException e) {
            Log.w(TAG, "WRITE_SECURE_SETTINGS is not granted", e);
        }
    }

    private void startInForeground() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_MIN));

        PendingIntent openApp = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(openApp)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }
}
