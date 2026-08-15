package com.focuslock.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public final class LockStore {
    private static final String PREFS = "focus_lock";
    private static final String PACKAGES = "packages";
    private static final String LOCK_START = "lock_start";
    private static final String LOCK_END = "lock_end";

    private LockStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Set<String> packages(Context context) {
        return new HashSet<>(prefs(context).getStringSet(PACKAGES, new HashSet<>()));
    }

    public static void saveSelection(Context context, Set<String> packages) {
        prefs(context).edit().putStringSet(PACKAGES, new HashSet<>(packages)).apply();
    }

    public static void schedule(Context context, long start, long end) {
        prefs(context).edit().putLong(LOCK_START, start).putLong(LOCK_END, end).apply();
    }

    public static long start(Context context) { return prefs(context).getLong(LOCK_START, 0); }
    public static long end(Context context) { return prefs(context).getLong(LOCK_END, 0); }

    public static boolean isLocked(Context context, String packageName) {
        long now = System.currentTimeMillis();
        return now >= start(context) && now < end(context) && packages(context).contains(packageName);
    }
}
