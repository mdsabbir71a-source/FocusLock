package com.focuslock.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

/**
 * Opens the small number of manufacturer settings that Android intentionally
 * does not let normal apps change. It never enables a setting silently.
 */
public final class OemReliability {
    private OemReliability() { }

    public static boolean needsXiaomiAutoStartHelp() {
        String maker = (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase(java.util.Locale.US);
        return maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco");
    }

    /** Returns true when a Xiaomi/Redmi/POCO Auto-start screen was opened. */
    public static boolean openAutoStartSettings(Context context) {
        if (context == null) return false;
        Intent[] choices = new Intent[] {
                new Intent().setComponent(new ComponentName("com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                new Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.parse("package:" + context.getPackageName()))
        };
        for (Intent choice : choices) {
            try {
                choice.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(choice);
                return true;
            } catch (RuntimeException ignored) {
                // Xiaomi changes the component name between MIUI and HyperOS.
            }
        }
        return false;
    }
}
