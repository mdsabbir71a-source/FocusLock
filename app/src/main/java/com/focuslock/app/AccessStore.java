package com.focuslock.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Cached server entitlement. Online verification is required at least once every 24 hours. */
public final class AccessStore {
    private static final String PREFS = "focuslock_access";
    private static final long OFFLINE_GRACE_MS = 24L * 60L * 60L * 1000L;

    private AccessStore() {}

    public static void allow(Context context, String level) {
        prefs(context).edit()
                .putBoolean("allowed", true)
                .putString("level", level == null ? "free" : level)
                .putLong("checked_at", System.currentTimeMillis())
                .apply();
    }

    public static void deny(Context context, String reason) {
        prefs(context).edit()
                .putBoolean("allowed", false)
                .putString("reason", reason == null ? "Access unavailable" : reason)
                .putLong("checked_at", System.currentTimeMillis())
                .apply();
    }

    public static boolean isAllowed(Context context) {
        SharedPreferences p = prefs(context);
        return p.getBoolean("allowed", false)
                && System.currentTimeMillis() - p.getLong("checked_at", 0) <= OFFLINE_GRACE_MS;
    }

    public static String reason(Context context) {
        return prefs(context).getString("reason", "Your FocusLock access is currently unavailable.");
    }

    public static String level(Context context) {
        return prefs(context).getString("level", "free");
    }

    public static void clear(Context context) { prefs(context).edit().clear().apply(); }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
