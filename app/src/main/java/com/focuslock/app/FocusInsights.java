package com.focuslock.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lightweight, on-device focus metrics. It never affects protection decisions. */
public final class FocusInsights {
    private static final String PREFS = "focuslock_insights";
    private static final String DAY = "day_key";
    private static final String TODAY = "today_screen_ms";
    private static final String PREVIOUS = "previous_screen_ms";
    private static final String PAUSES = "pauses";
    private static final String SAVED = "saved_ms";
    private static final String PAUSE_EVENTS = "pause_events";
    private static final int MAX_PAUSE_EVENTS = 500;

    private FocusInsights() { }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int dayKey() {
        Calendar now = Calendar.getInstance();
        return now.get(Calendar.YEAR) * 1000 + now.get(Calendar.DAY_OF_YEAR);
    }

    private static void rollDay(Context context) {
        SharedPreferences values = prefs(context);
        int today = dayKey();
        if (values.getInt(DAY, 0) == today) return;
        values.edit()
                .putInt(DAY, today)
                .putLong(PREVIOUS, values.getLong(TODAY, 0L))
                .putLong(TODAY, 0L)
                .apply();
    }

    public static void addScreenTime(Context context, long elapsedMs) {
        rollDay(context);
        SharedPreferences values = prefs(context);
        values.edit().putLong(TODAY, values.getLong(TODAY, 0L) + Math.max(0L, elapsedMs)).apply();
    }

    public static void recordPause(Context context, String packageName, long protectedMs) {
        rollDay(context);
        SharedPreferences values = prefs(context);
        long saved = Math.max(0L, protectedMs);
        Set<String> events = new HashSet<>(values.getStringSet(PAUSE_EVENTS, new HashSet<>()));
        events.add(System.currentTimeMillis() + "|" + packageName + "|" + saved);
        if (events.size() > MAX_PAUSE_EVENTS) {
            ArrayList<String> ordered = new ArrayList<>(events);
            java.util.Collections.sort(ordered);
            while (ordered.size() > MAX_PAUSE_EVENTS) ordered.remove(0);
            events = new HashSet<>(ordered);
        }
        values.edit()
                .putInt(PAUSES, values.getInt(PAUSES, 0) + 1)
                .putLong(SAVED, values.getLong(SAVED, 0L) + saved)
                .putStringSet(PAUSE_EVENTS, events)
                .apply();
    }

    /** Recent pause history for local analytics. Each entry is grouped by the protected package. */
    public static List<Pause> pauses(Context context) {
        ArrayList<Pause> result = new ArrayList<>();
        for (String value : prefs(context).getStringSet(PAUSE_EVENTS, new HashSet<>())) {
            String[] parts = value.split("\\|", 3);
            if (parts.length != 3) continue;
            try { result.add(new Pause(Long.parseLong(parts[0]), parts[1], Long.parseLong(parts[2]))); }
            catch (NumberFormatException ignored) { }
        }
        java.util.Collections.sort(result, new Comparator<Pause>() {
            @Override public int compare(Pause left, Pause right) {
                return left.timeMs < right.timeMs ? -1 : left.timeMs == right.timeMs ? 0 : 1;
            }
        });
        return result;
    }

    public static Snapshot snapshot(Context context) {
        rollDay(context);
        SharedPreferences values = prefs(context);
        return new Snapshot(values.getInt(PAUSES, 0), values.getLong(SAVED, 0L),
                values.getLong(TODAY, 0L), values.getLong(PREVIOUS, 0L));
    }

    public static final class Snapshot {
        public final int pauses;
        public final long focusSavedMs;
        public final long todayScreenMs;
        public final long previousScreenMs;
        Snapshot(int pauses, long focusSavedMs, long todayScreenMs, long previousScreenMs) {
            this.pauses = pauses;
            this.focusSavedMs = focusSavedMs;
            this.todayScreenMs = todayScreenMs;
            this.previousScreenMs = previousScreenMs;
        }
    }

    public static final class Pause {
        public final long timeMs;
        public final String packageName;
        public final long savedMs;
        Pause(long timeMs, String packageName, long savedMs) {
            this.timeMs = timeMs;
            this.packageName = packageName;
            this.savedMs = savedMs;
        }
    }
}
