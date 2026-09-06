package com.focuslock.app;

import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;

/** Provides a lightweight watchdog for Android/OEM process termination. */
public final class ProtectionRestarter {
    private static final String ACTION_WATCHDOG = "com.focuslock.app.MONITOR_WATCHDOG";
    private static final long WATCHDOG_MS = 15 * 60_000L;

    private ProtectionRestarter() {}

    public static boolean shouldMonitor(Context context) {
        return AccessStore.isAllowed(context)
                && LockStore.isEnabled(context)
                && RemoteConfigStore.appBlockingEnabled(context)
                && !LockStore.packages(context).isEmpty()
                && hasUsageAccess(context)
                && Settings.canDrawOverlays(context);
    }

    public static void ensureMonitorRunning(Context context) {
        if (!shouldMonitor(context)) {
            cancel(context);
            return;
        }
        if (!MonitorHealthStore.isHealthy(context)) {
            Intent service = new Intent(context, FocusMonitorService.class);
            try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
                else context.startService(service);
            } catch (RuntimeException error) {
                DiagnosticStore.record(context, "monitor_restart_deferred", error.getClass().getSimpleName());
            }
        }
        schedule(context, WATCHDOG_MS);
    }

    public static void schedule(Context context, long delayMs) {
        if (!shouldMonitor(context)) return;
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        long trigger = SystemClock.elapsedRealtime() + Math.max(5_000L, delayMs);
        alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending(context));
    }

    public static void cancel(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) alarms.cancel(pending(context));
        MonitorHealthStore.clear(context);
    }

    private static PendingIntent pending(Context context) {
        Intent intent = new Intent(context, ProtectionRestartReceiver.class).setAction(ACTION_WATCHDOG);
        return PendingIntent.getBroadcast(context, 91, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static boolean hasUsageAccess(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        ApplicationInfo info = context.getApplicationInfo();
        return appOps != null && appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                info.uid, context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }
}
