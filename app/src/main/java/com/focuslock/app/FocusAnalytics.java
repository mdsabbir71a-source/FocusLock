package com.focuslock.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import com.google.firebase.analytics.FirebaseAnalytics;

/** Optional app analytics. Never send selected app names or device identifiers. */
final class FocusAnalytics {
    private static final String PREFS = "focuslock_analytics_choice";
    private static final String CHOSEN = "chosen";
    private static final String ENABLED = "enabled";
    private FocusAnalytics() {}
    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    static boolean hasChoice(Context c) { return prefs(c).getBoolean(CHOSEN, false); }
    static boolean enabled(Context c) { return prefs(c).getBoolean(ENABLED, false); }
    static void setEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(CHOSEN, true).putBoolean(ENABLED, enabled).apply();
        try {
            FirebaseAnalytics analytics = FirebaseAnalytics.getInstance(c.getApplicationContext());
            analytics.setAnalyticsCollectionEnabled(enabled);
            if (!enabled) analytics.resetAnalyticsData();
        } catch (RuntimeException ignored) { /* Analytics must never block protection. */ }
    }
    static void log(Context c, String event) {
        if (!enabled(c)) return;
        try { FirebaseAnalytics.getInstance(c.getApplicationContext()).logEvent(event, new Bundle()); }
        catch (RuntimeException ignored) { /* App functionality takes priority. */ }
    }
}
