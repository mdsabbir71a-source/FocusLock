package com.focuslock.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Keeps the small amount of account display information needed by the local Account screen. */
public final class AccountStore {
    private static final String PREFS = "focuslock_account_profile";

    private AccountStore() {}

    public static void save(Context context, String email, String provider) {
        prefs(context).edit()
                .putString("email", email == null ? "" : email.trim())
                .putString("provider", provider == null ? "" : provider.trim())
                .apply();
    }

    public static String email(Context context) {
        return prefs(context).getString("email", "");
    }

    public static String provider(Context context) {
        return prefs(context).getString("provider", "");
    }

    public static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
