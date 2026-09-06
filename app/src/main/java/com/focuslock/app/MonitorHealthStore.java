package com.focuslock.app;

import android.content.Context;

/** Tracks the real monitor heartbeat separately from the user's saved ON switch. */
public final class MonitorHealthStore {
    private static final String PREFS = "focuslock_monitor_health";
    private static final String HEARTBEAT = "heartbeat_ms";
    private static final long HEALTH_WINDOW_MS = 20_000L;

    private MonitorHealthStore() {}

    public static void heartbeat(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(HEARTBEAT, System.currentTimeMillis()).apply();
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(HEARTBEAT).apply();
    }

    public static boolean isHealthy(Context context) {
        long last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(HEARTBEAT, 0L);
        long age = System.currentTimeMillis() - last;
        return last > 0L && age >= 0L && age <= HEALTH_WINDOW_MS;
    }
}
