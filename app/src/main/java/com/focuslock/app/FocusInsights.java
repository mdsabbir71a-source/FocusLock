package com.focuslock.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Calendar;

/** Lightweight, on-device focus metrics. It never affects protection decisions. */
public final class FocusInsights {
    private static final String PREFS = "focuslock_insights";
    private static final String DAY = "day_key";
    private static final String TODAY = "today_screen_ms";
    private static final String PREVIOUS = "previous_screen_ms";
    private static final String PAUSES = "pauses";
    private static final String SAVED = "saved_ms";

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

    public static void recordPause(Context context, long protectedMs) {
        rollDay(context);
        SharedPreferences values = prefs(context);
        values.edit()
                .putInt(PAUSES, values.getInt(PAUSES, 0) + 1)
                .putLong(SAVED, values.getLong(SAVED, 0L) + Math.max(0L, protectedMs))
                .apply();
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
}