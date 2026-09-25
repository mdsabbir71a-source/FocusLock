package com.focuslock.app;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;

/** Read-only check for the user-approved compatibility service. */
public final class CompatibilityAccess {
    private CompatibilityAccess() {}

    public static boolean isEnabled(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null || enabled.isEmpty()) return false;
        String component = new ComponentName(context, FocusAccessibilityService.class).flattenToString();
        for (String entry : enabled.split(":")) {
            if (component.equalsIgnoreCase(entry)) return true;
        }
        return false;
    }

    /** Opens Android's stable, system-owned Accessibility settings screen. */
    public static void openSettings(Context context) {
        context.startActivity(new android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }
}
