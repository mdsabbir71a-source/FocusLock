package com.focuslock.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores one redacted diagnostic event locally until it can be uploaded. */
public final class DiagnosticStore {
    private static final String PREFS = "focuslock_diagnostics";

    public static final class Event {
        public final String code;
        public final String detail;
        public final long occurredAt;
        Event(String code, String detail, long occurredAt) {
            this.code = code;
            this.detail = detail;
            this.occurredAt = occurredAt;
        }
    }

    private DiagnosticStore() {}

    public static boolean enabled(Context context) {
        return prefs(context).getBoolean("enabled", false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        SharedPreferences.Editor editor = prefs(context).edit().putBoolean("enabled", enabled);
        if (!enabled) editor.remove("code").remove("detail").remove("occurred_at");
        editor.apply();
    }

    public static void record(Context context, String code, String detail) {
        if (!enabled(context)) return;
        prefs(context).edit()
                .putString("code", safe(code, 80))
                .putString("detail", safe(detail, 180))
                .putLong("occurred_at", System.currentTimeMillis())
                .apply();
    }

    public static void recordCrash(Context context, Throwable error) {
        if (!enabled(context) || error == null) return;
        String detail = error.getClass().getSimpleName();
        StackTraceElement[] stack = error.getStackTrace();
        if (stack != null && stack.length > 0) {
            StackTraceElement top = stack[0];
            detail += " at " + top.getClassName() + ":" + Math.max(0, top.getLineNumber());
        }
        record(context, "uncaught_exception", detail);
    }

    public static Event pending(Context context) {
        SharedPreferences p = prefs(context);
        String code = p.getString("code", "");
        if (code == null || code.isEmpty()) return null;
        return new Event(code, p.getString("detail", ""), p.getLong("occurred_at", System.currentTimeMillis()));
    }

    public static void clearPending(Context context) {
        prefs(context).edit().remove("code").remove("detail").remove("occurred_at").apply();
    }

    public static void clearAll(Context context) { prefs(context).edit().clear().apply(); }

    private static String safe(String value, int max) {
        if (value == null) return "";
        String cleaned = value.replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[email]")
                .replaceAll("https?://\\S+", "[url]")
                .replaceAll("[\\r\\n\\t]+", " ");
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
