package com.focuslock.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/** Read-only check for the user-approved compatibility service. */
public final class CompatibilityAccess {
    private static final String ACTION_ACCESSIBILITY_DETAILS_SETTINGS =
            "android.settings.ACCESSIBILITY_DETAILS_SETTINGS";

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

    /**
     * Opens this service's own system settings page whenever the device
     * supports it. That puts the user directly in front of the one switch
     * they need, instead of making them search the whole Accessibility list.
     */
    public static void openSettings(Context context) {
        ComponentName component = new ComponentName(context, FocusAccessibilityService.class);
        Intent details = new Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS)
                .putExtra(Intent.EXTRA_COMPONENT_NAME, component.flattenToString());
        if (details.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(details);
            return;
        }
        context.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }
}
