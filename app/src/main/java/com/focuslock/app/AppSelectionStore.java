package com.focuslock.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores local selection frequency so often-used apps are easier to find. */
public final class AppSelectionStore {
    private static final String PREFS = "focuslock_app_selection";
    private static final String COUNT_PREFIX = "count_";

    private AppSelectionStore() {}

    public static int count(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return 0;
        return prefs(context).getInt(COUNT_PREFIX + packageName, 0);
    }

    public static void record(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        int next = Math.min(999, count(context, packageName) + 1);
        prefs(context).edit().putInt(COUNT_PREFIX + packageName, next).apply();
    }

    public static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
