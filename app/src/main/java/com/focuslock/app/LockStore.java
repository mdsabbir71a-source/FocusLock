package com.focuslock.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public final class LockStore {
    private static final String PREFS = "focus_lock";
    private static final String PACKAGES = "packages";
    private static final String ALLOWANCE = "allowance_ms";
    private static final String LOCK_DURATION = "lock_duration_ms";

    private LockStore() {}
    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    private static String usageKey(String pkg) { return "usage_" + pkg; }
    private static String lockedKey(String pkg) { return "locked_until_" + pkg; }

    public static Set<String> packages(Context context) { return new HashSet<>(prefs(context).getStringSet(PACKAGES, new HashSet<>())); }
    public static boolean isSelected(Context context, String pkg) { return packages(context).contains(pkg); }

    public static void configure(Context context, Set<String> packages, long allowanceMs, long lockDurationMs) {
        SharedPreferences.Editor edit = prefs(context).edit()
                .putStringSet(PACKAGES, new HashSet<>(packages))
                .putLong(ALLOWANCE, allowanceMs)
                .putLong(LOCK_DURATION, lockDurationMs);
        for (String pkg : packages) edit.putLong(usageKey(pkg), 0).putLong(lockedKey(pkg), 0);
        edit.apply();
    }

    public static long allowance(Context context) { return prefs(context).getLong(ALLOWANCE, 60_000L); }
    public static long lockDuration(Context context) { return prefs(context).getLong(LOCK_DURATION, 3_600_000L); }
    public static long usage(Context context, String pkg) { return prefs(context).getLong(usageKey(pkg), 0); }
    public static long lockedUntil(Context context, String pkg) { return prefs(context).getLong(lockedKey(pkg), 0); }
    public static boolean isLocked(Context context, String pkg) { return isSelected(context, pkg) && System.currentTimeMillis() < lockedUntil(context, pkg); }
    public static long remainingAllowance(Context context, String pkg) { return Math.max(0, allowance(context) - usage(context, pkg)); }

    public static boolean addUsage(Context context, String pkg, long elapsedMs) {
        if (!isSelected(context, pkg) || isLocked(context, pkg)) return false;
        long total = usage(context, pkg) + Math.max(0, Math.min(elapsedMs, 1500));
        if (total >= allowance(context)) {
            prefs(context).edit().putLong(usageKey(pkg), 0).putLong(lockedKey(pkg), System.currentTimeMillis() + lockDuration(context)).apply();
            return true;
        }
        prefs(context).edit().putLong(usageKey(pkg), total).apply();
        return false;
    }
}
