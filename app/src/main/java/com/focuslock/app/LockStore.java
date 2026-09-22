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
    private static final String LOCK_FOCUSLOCK = "lock_focuslock_with_apps";
    private static final String ENABLED = "enabled";
    private static final String REMINDER_INDEX = "reminder_index";
    private static final String LAST_CARD_INDEX = "last_lock_card_index";
    private static final String[] LOCK_CARDS = {"dunes","grove","horizon","nightfall","rainfall","ridge","seedling","tide"};

    private LockStore() {}
    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    private static String usageKey(String pkg) { return "usage_" + pkg; }
    private static String lockedKey(String pkg) { return "locked_until_" + pkg; }
    private static String cardKey(String pkg) { return "lock_card_" + pkg; }

    public static Set<String> packages(Context context) { return new HashSet<>(prefs(context).getStringSet(PACKAGES, new HashSet<>())); }
    public static boolean isSelected(Context context, String pkg) { return packages(context).contains(pkg); }
    public static boolean isEnabled(Context context) { return prefs(context).getBoolean(ENABLED, true); }
    // Persist the master state before services are started/stopped so every
    // protection component observes the same value immediately.
    public static void setEnabled(Context context, boolean enabled) { prefs(context).edit().putBoolean(ENABLED, enabled).commit(); }
    public static void clear(Context context) { prefs(context).edit().clear().apply(); }

    public static int nextReminderIndex(Context context, int count) {
        int current = prefs(context).getInt(REMINDER_INDEX, 0);
        prefs(context).edit().putInt(REMINDER_INDEX, (current + 1) % Math.max(1, count)).apply();
        return current % Math.max(1, count);
    }

    public static void configure(Context context, Set<String> packages, long allowanceMs, long lockDurationMs) {
        SharedPreferences.Editor edit = prefs(context).edit()
                .putStringSet(PACKAGES, new HashSet<>(packages))
                .putLong(ALLOWANCE, allowanceMs)
                .putLong(LOCK_DURATION, lockDurationMs);
        for (String pkg : packages) edit.putLong(usageKey(pkg), 0).putLong(lockedKey(pkg), 0);
        edit.apply();
    }

    public static long allowance(Context context) { return prefs(context).getLong(ALLOWANCE, 60_000L); }
    public static long lockDuration(Context context) { return prefs(context).getLong(LOCK_DURATION, 600_000L); }
    public static long usage(Context context, String pkg) { return prefs(context).getLong(usageKey(pkg), 0); }
    public static boolean lockFocusLock(Context context) { return prefs(context).getBoolean(LOCK_FOCUSLOCK, false); }
    public static void setLockFocusLock(Context context, boolean enabled) { prefs(context).edit().putBoolean(LOCK_FOCUSLOCK, enabled).apply(); }

    public static long lockedUntil(Context context, String pkg) {
        if (context.getPackageName().equals(pkg) && lockFocusLock(context)) return latestSelectedLockEnd(context);
        return prefs(context).getLong(lockedKey(pkg), 0);
    }

    public static String lockCardName(Context context, String pkg) {
        String target = pkg;
        if (context.getPackageName().equals(pkg) && lockFocusLock(context)) {
            long latest = 0L;
            for (String selected : packages(context)) {
                long until = prefs(context).getLong(lockedKey(selected), 0L);
                if (until > latest) { latest = until; target = selected; }
            }
        }
        int index = Math.max(0, prefs(context).getInt(cardKey(target), 0)) % LOCK_CARDS.length;
        return LOCK_CARDS[index];
    }

    public static boolean isLocked(Context context, String pkg) {
        if (!isEnabled(context)) return false;
        if (context.getPackageName().equals(pkg)) return lockFocusLock(context) && latestSelectedLockEnd(context) > System.currentTimeMillis();
        return isSelected(context, pkg) && System.currentTimeMillis() < lockedUntil(context, pkg);
    }

    private static long latestSelectedLockEnd(Context context) {
        long latest = 0;
        for (String selected : packages(context)) latest = Math.max(latest, prefs(context).getLong(lockedKey(selected), 0));
        return latest;
    }
    public static long remainingAllowance(Context context, String pkg) { return Math.max(0, allowance(context) - usage(context, pkg)); }

    public static boolean addUsage(Context context, String pkg, long elapsedMs) {
        if (!isEnabled(context) || !isSelected(context, pkg) || isLocked(context, pkg)) return false;
        long counted = Math.max(0, Math.min(elapsedMs, 1500));
        FocusInsights.addScreenTime(context, counted);
        long total = usage(context, pkg) + counted;
        if (total >= allowance(context)) {
            int cardIndex = (prefs(context).getInt(LAST_CARD_INDEX, -1) + 1) % LOCK_CARDS.length;
            prefs(context).edit().putLong(usageKey(pkg), 0).putLong(lockedKey(pkg), System.currentTimeMillis() + lockDuration(context)).putInt(cardKey(pkg), cardIndex).putInt(LAST_CARD_INDEX, cardIndex).apply();
            FocusInsights.recordPause(context, lockDuration(context));
            return true;
        }
        prefs(context).edit().putLong(usageKey(pkg), total).apply();
        return false;
    }

}
