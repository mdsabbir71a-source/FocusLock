package com.focuslock.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Restores user-enabled protection after the phone finishes booting. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!AccessStore.isAllowed(context) || !LockStore.isEnabled(context)) return;

        if (RemoteConfigStore.appBlockingEnabled(context) && !LockStore.packages(context).isEmpty()) {
            Intent monitor = new Intent(context, FocusMonitorService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(monitor);
            else context.startService(monitor);
            ProtectionRestarter.schedule(context, 15 * 60_000L);
        }
    }
}
