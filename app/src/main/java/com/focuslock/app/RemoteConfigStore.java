package com.focuslock.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Cached server-controlled settings. Safe defaults keep protection available while offline. */
public final class RemoteConfigStore {
    private static final String PREFS = "focuslock_remote_config";
    private static final long UPDATE_REMINDER_INTERVAL_MS = 24L * 60L * 60L * 1000L;
    private static final int DEFAULT_USE_SECONDS = 60;
    private static final int DEFAULT_LOCK_SECONDS = 600;
    private static final int PREVIOUS_DEFAULT_LOCK_SECONDS = 3600;

    private RemoteConfigStore() {}

    public static void save(Context context, JSONObject row) {
        JSONArray reminders = row.optJSONArray("reminder_messages");
        int defaultLockSeconds = row.optInt("default_lock_seconds", DEFAULT_LOCK_SECONDS);
        // Treat the old one-hour server default as legacy so fresh setups use
        // the new, less intimidating ten-minute starting point.
        if (defaultLockSeconds == PREVIOUS_DEFAULT_LOCK_SECONDS) {
            defaultLockSeconds = DEFAULT_LOCK_SECONDS;
        }
        prefs(context).edit()
                .putBoolean("app_blocking_enabled", row.optBoolean("app_blocking_enabled", true))
                .putBoolean("announcement_enabled", row.optBoolean("announcement_enabled", false))
                .putString("announcement_title", row.optString("announcement_title", "A note from FocusLock"))
                .putString("announcement_body", row.optString("announcement_body", ""))
                .putString("reminder_messages", reminders == null ? "[]" : reminders.toString())
                .putInt("default_use_seconds", Math.max(1, row.optInt("default_use_seconds", DEFAULT_USE_SECONDS)))
                .putInt("default_lock_seconds", Math.max(1, defaultLockSeconds))
                .putInt("latest_version_code", Math.max(1, row.optInt("latest_version_code", BuildConfig.VERSION_CODE)))
                .putString("latest_version_name", row.optString("latest_version_name", BuildConfig.VERSION_NAME))
                .putInt("minimum_version_code", Math.max(1, row.optInt("minimum_version_code", 1)))
                .putString("update_url", row.optString("update_url", ""))
                .putString("updated_at", row.optString("updated_at", ""))
                .putLong("fetched_at", System.currentTimeMillis())
                .apply();
    }

    public static boolean appBlockingEnabled(Context context) { return prefs(context).getBoolean("app_blocking_enabled", true); }
    public static int defaultUseSeconds(Context context) { return prefs(context).getInt("default_use_seconds", DEFAULT_USE_SECONDS); }
    public static int defaultLockSeconds(Context context) { return prefs(context).getInt("default_lock_seconds", DEFAULT_LOCK_SECONDS); }
    public static int latestVersionCode(Context context) { return prefs(context).getInt("latest_version_code", BuildConfig.VERSION_CODE); }
    public static int minimumVersionCode(Context context) { return prefs(context).getInt("minimum_version_code", 1); }
    public static String latestVersionName(Context context) { return prefs(context).getString("latest_version_name", BuildConfig.VERSION_NAME); }
    public static String updateUrl(Context context) { return prefs(context).getString("update_url", ""); }

    public static String[] reminders(Context context, String[] fallback) {
        try {
            JSONArray array = new JSONArray(prefs(context).getString("reminder_messages", "[]"));
            List<String> values = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "").trim();
                if (!value.isEmpty()) values.add(value);
            }
            return values.isEmpty() ? fallback : values.toArray(new String[0]);
        } catch (Exception ignored) { return fallback; }
    }

    public static boolean shouldShowAnnouncement(Context context) {
        SharedPreferences p = prefs(context);
        String updatedAt = p.getString("updated_at", "");
        return p.getBoolean("announcement_enabled", false)
                && !p.getString("announcement_body", "").trim().isEmpty()
                && !updatedAt.isEmpty()
                && !updatedAt.equals(p.getString("announcement_seen_at", ""));
    }

    public static String announcementTitle(Context context) { return prefs(context).getString("announcement_title", "A note from FocusLock"); }
    public static String announcementBody(Context context) { return prefs(context).getString("announcement_body", ""); }
    public static void markAnnouncementSeen(Context context) { prefs(context).edit().putString("announcement_seen_at", prefs(context).getString("updated_at", "")).apply(); }

    public static boolean shouldPromptForUpdate(Context context) {
        int latest = latestVersionCode(context);
        if (latest <= BuildConfig.VERSION_CODE || updateUrl(context).isEmpty()) return false;
        if (BuildConfig.VERSION_CODE < minimumVersionCode(context)) return true;
        SharedPreferences p = prefs(context);
        return p.getInt("prompted_version", 0) != latest
                || System.currentTimeMillis() - p.getLong("prompted_at", 0) >= UPDATE_REMINDER_INTERVAL_MS;
    }

    public static boolean updateRequired(Context context) { return BuildConfig.VERSION_CODE < minimumVersionCode(context); }
    public static void markUpdatePrompted(Context context) { prefs(context).edit().putInt("prompted_version", latestVersionCode(context)).putLong("prompted_at", System.currentTimeMillis()).apply(); }
    public static void clear(Context context) { prefs(context).edit().clear().apply(); }

    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
}
