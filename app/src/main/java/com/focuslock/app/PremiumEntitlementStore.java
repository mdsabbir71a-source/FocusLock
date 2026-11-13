package com.focuslock.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

/** Cached premium status; callers must refresh it from verified Google data. */
final class PremiumEntitlementStore {
    private static final String PREFS = "focuslock_premium_entitlement";
    private static final long MAX_OFFLINE_MS = 24L * 60L * 60L * 1000L;
    private static final long CLOCK_TOLERANCE_MS = 60_000L;

    static void recordVerified(Context context, long expiryMillis) {
        long now = System.currentTimeMillis();
        if (expiryMillis <= now) { clear(context); return; }
        prefs(context).edit().putLong("expires_at", expiryMillis)
                .putLong("checked_wall", now)
                .putLong("checked_elapsed", SystemClock.elapsedRealtime()).apply();
    }
    static boolean hasStoredAccess(Context context) { return prefs(context).getLong("expires_at", 0) > 0; }
    static boolean hasVerifiedAccess(Context context) {
        SharedPreferences p = prefs(context);
        long wall = System.currentTimeMillis();
        long elapsed = SystemClock.elapsedRealtime();
        long checkedWall = p.getLong("checked_wall", 0);
        long checkedElapsed = p.getLong("checked_elapsed", -1);
        long age = elapsed - checkedElapsed;
        if (checkedWall <= 0 || checkedElapsed < 0 || age < 0 || age > MAX_OFFLINE_MS) return false;
        if (wall + CLOCK_TOLERANCE_MS < checkedWall + age) return false;
        return wall < p.getLong("expires_at", 0) && checkedWall + age < p.getLong("expires_at", 0);
    }
    static void clear(Context context) { prefs(context).edit().clear().apply(); }
    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
