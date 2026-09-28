package com.focuslock.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
    private static String allowanceKey(String pkg) { return "allowance_" + pkg; }
    private static String lockDurationKey(String pkg) { return "lock_duration_" + pkg; }

    public static Set<String> packages(Context context) { return new HashSet<>(prefs(context).getStringSet(PACKAGES, new HashSet<>())); }
    public static boolean isSelected(Context context, String pkg) { return packages(context).contains(pkg); }
    public static boolean isEnabled(Context context) { return prefs(context).getBoolean(ENABLED, true); }
    // Persist the master state before services are started/stopped so every
    // protection component observes the same value immediately.
    public static void setEnabled(Context context, boolean enabled) { prefs(context).edit().putBoolean(ENABLED, enabled).commit(); }
    public static void clear(Context context) { prefs(context).edit().clear().apply(); }

    /** Remove packages Android no longer has installed, without waiting for a
     * manual Save tap. This prevents a removed app from inflating the selected
     * count or continuing to exist in the monitor's saved target set. */
    public static boolean pruneUninstalled(Context context) {
        SharedPreferences preferences = prefs(context);
        Set<String> previous = new HashSet<>(preferences.getStringSet(PACKAGES, new HashSet<>()));
        Set<String> kept = new HashSet<>();
        SharedPreferences.Editor edit = preferences.edit();
        boolean changed = false;
        PackageManager manager = context.getPackageManager();
        for (String pkg : previous) {
            try {
                manager.getApplicationInfo(pkg, 0);
                kept.add(pkg);
            } catch (PackageManager.NameNotFoundException missing) {
                changed = true;
                edit.remove(usageKey(pkg)).remove(lockedKey(pkg)).remove(cardKey(pkg))
                        .remove(allowanceKey(pkg)).remove(lockDurationKey(pkg));
            }
        }
        if (changed) edit.putStringSet(PACKAGES, kept).apply();
        return changed;
    }

    public static int nextReminderIndex(Context context, int count) {
        int current = prefs(context).getInt(REMINDER_INDEX, 0);
        prefs(context).edit().putInt(REMINDER_INDEX, (current + 1) % Math.max(1, count)).apply();
        return current % Math.max(1, count);
    }

    /**
     * Each time a lock card is presented, move to the next reminder. Unlike a
     * lock-card design, a reminder is intentionally not pinned to the lock
     * session: returning to a paused app should feel fresh and encouraging.
     */
    public static String nextLockReminder(Context context, String[] reminders) {
        if (reminders == null || reminders.length == 0) return "Take a breath. This urge will pass.";
        return reminders[nextReminderIndex(context, reminders.length)];
    }

    public static void configure(Context context, Set<String> packages, long allowanceMs, long lockDurationMs) {
        SharedPreferences preferences = prefs(context);
        Set<String> previous = new HashSet<>(preferences.getStringSet(PACKAGES, new HashSet<>()));
        SharedPreferences.Editor edit = preferences.edit()
                .putStringSet(PACKAGES, new HashSet<>(packages))
                .putLong(ALLOWANCE, allowanceMs)
                .putLong(LOCK_DURATION, lockDurationMs);
        // Remove state for deselected apps. This prevents an old lock or usage
        // value from reappearing if an app is removed and later selected again.
        for (String pkg : previous) {
            if (!packages.contains(pkg)) {
                edit.remove(usageKey(pkg)).remove(lockedKey(pkg)).remove(cardKey(pkg))
                        .remove(allowanceKey(pkg)).remove(lockDurationKey(pkg));
            }
        }
        for (String pkg : packages) edit.putLong(usageKey(pkg), 0).putLong(lockedKey(pkg), 0);
        edit.apply();
    }

    public static long allowance(Context context) { return prefs(context).getLong(ALLOWANCE, 60_000L); }
    public static long lockDuration(Context context) { return prefs(context).getLong(LOCK_DURATION, 600_000L); }
    public static boolean hasCustomTiming(Context context, String pkg) {
        return prefs(context).contains(allowanceKey(pkg)) || prefs(context).contains(lockDurationKey(pkg));
    }
    public static long allowance(Context context, String pkg) {
        return prefs(context).getLong(allowanceKey(pkg), allowance(context));
    }
    public static long lockDuration(Context context, String pkg) {
        return prefs(context).getLong(lockDurationKey(pkg), lockDuration(context));
    }
    public static void setPackageTiming(Context context, String pkg, long allowanceMs, long lockDurationMs) {
        if (pkg == null || pkg.isEmpty()) return;
        prefs(context).edit().putLong(allowanceKey(pkg), Math.max(1L, allowanceMs))
                .putLong(lockDurationKey(pkg), Math.max(1L, lockDurationMs)).apply();
    }
    public static void clearPackageTiming(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty()) return;
        prefs(context).edit().remove(allowanceKey(pkg)).remove(lockDurationKey(pkg)).apply();
    }
    public static long usage(Context context, String pkg) { return prefs(context).getLong(usageKey(pkg), 0); }
    // A new FocusLock setup should protect the app itself during a selected
    // app's pause. Existing users retain the choice they already saved.
    public static boolean lockFocusLock(Context context) { return prefs(context).getBoolean(LOCK_FOCUSLOCK, true); }
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
    public static long remainingAllowance(Context context, String pkg) { return Math.max(0, allowance(context, pkg) - usage(context, pkg)); }

    public static boolean addUsage(Context context, String pkg, long elapsedMs) {
        if (!isEnabled(context) || !isSelected(context, pkg) || isLocked(context, pkg)) return false;
        long counted = Math.max(0, Math.min(elapsedMs, 1500));
        FocusInsights.addScreenTime(context, counted);
        long total = usage(context, pkg) + counted;
        if (total >= allowance(context, pkg)) {
            // Rotate the approved card gallery once per new lock session. The
            // result is stored on the package so revisiting a still-locked app
            // keeps its countdown/card stable instead of flickering.
            int cardIndex = (prefs(context).getInt(LAST_CARD_INDEX, -1) + 1) % LOCK_CARDS.length;
            long duration = lockDuration(context, pkg);
            prefs(context).edit().putLong(usageKey(pkg), 0).putLong(lockedKey(pkg), System.currentTimeMillis() + duration).putInt(cardKey(pkg), cardIndex).putInt(LAST_CARD_INDEX, cardIndex).apply();
            FocusInsights.recordPause(context, pkg, duration);
            return true;
        }
        prefs(context).edit().putLong(usageKey(pkg), total).apply();
        return false;
    }

}
