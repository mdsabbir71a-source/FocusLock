package com.focuslock.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores a user-enabled boundary after boot without changing their settings. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!AccessStore.isAllowed(context) || !LockStore.isEnabled(context)) return;
        ProtectionRestarter.startMonitor(context, "boot");
        ProtectionRestarter.schedule(context, 60_000L);
    }
}
