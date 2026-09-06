package com.focuslock.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarm callback that repairs a stopped monitor without changing user settings. */
public class ProtectionRestartReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        ProtectionRestarter.ensureMonitorRunning(context);
    }
}
