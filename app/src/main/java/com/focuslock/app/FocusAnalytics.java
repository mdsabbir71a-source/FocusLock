package com.focuslock.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

/** Optional app analytics. Custom events do not include selected app names. */
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
    static void restoreChoice(Context c) {
        if (!hasChoice(c)) { setEnabled(c, true); return; }
        try { FirebaseAnalytics.getInstance(c.getApplicationContext()).setAnalyticsCollectionEnabled(enabled(c)); }
        catch (RuntimeException error) { Log.w("FocusAnalytics", "Could not restore analytics choice", error); }
        if (enabled(c)) sendPendingCrashReports(); else discardPendingCrashReports();
    }
    private static void sendPendingCrashReports() {
        try { FirebaseCrashlytics.getInstance().sendUnsentReports(); }
        catch (RuntimeException error) { Log.w("FocusAnalytics", "Could not send crash reports", error); }
    }
    private static void discardPendingCrashReports() {
        try { FirebaseCrashlytics.getInstance().deleteUnsentReports(); }
        catch (RuntimeException error) { Log.w("FocusAnalytics", "Could not remove local crash reports", error); }
    }
    static void setEnabled(Context c, boolean enabled) {
        boolean wasEnabled = enabled(c);
        prefs(c).edit().putBoolean(CHOSEN, true).putBoolean(ENABLED, enabled).apply();
        try {
            FirebaseAnalytics analytics = FirebaseAnalytics.getInstance(c.getApplicationContext());
            analytics.setAnalyticsCollectionEnabled(enabled);
            if (!enabled) analytics.resetAnalyticsData();
        } catch (RuntimeException error) { Log.w("FocusAnalytics", "Could not update analytics collection", error); }
        if (!enabled || !wasEnabled) discardPendingCrashReports();
    }
    static boolean log(Context c, String event) {
        if (!enabled(c)) return false;
        try { FirebaseAnalytics.getInstance(c.getApplicationContext()).logEvent(event, new Bundle()); return true; }
        catch (RuntimeException error) { Log.w("FocusAnalytics", "Could not queue an analytics event", error); return false; }
    }
}
