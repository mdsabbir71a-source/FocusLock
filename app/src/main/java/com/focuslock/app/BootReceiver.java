package com.focuslock.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

/** Restarts an already-enabled boundary after a reboot or a safe app update. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!LockStore.isEnabled(context) || LockStore.packages(context).isEmpty()
                || !Settings.canDrawOverlays(context)) return;
        Intent monitor = new Intent(context, FocusMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(monitor);
        else context.startService(monitor);
    }
}
