package com.focuslock.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

/** Public support identifier, not a credential and never sufficient for authorization. */
public final class InstallationId {
    private static final String PREFS = "focuslock_install_identity";
    private static final String KEY = "support_id";

    private InstallationId() {}

    public static synchronized String get(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String value = prefs.getString(KEY, null);
        if (value == null || value.isEmpty()) {
            value = UUID.randomUUID().toString();
            if (!prefs.edit().putString(KEY, value).commit()) {
                throw new IllegalStateException("Could not save the FocusLock ID");
            }
        }
        return value;
    }
}
