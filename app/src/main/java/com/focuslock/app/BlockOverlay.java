package com.focuslock.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Permission-backed blocker drawn directly above the restricted app. */
public final class BlockOverlay {
    private static View view;
    private static String packageName;

    private BlockOverlay() { }

    public static synchronized void show(Context context, String blockedPackage) {
        if (!Settings.canDrawOverlays(context)) return;
        if (view != null && blockedPackage.equals(packageName)) return;
        hide(context);

        Context app = context.getApplicationContext();
        WindowManager windowManager = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        LinearLayout root = new LinearLayout(app);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(app, 28), dp(app, 28), dp(app, 28), dp(app, 28));
        root.setBackgroundColor(Color.rgb(248, 251, 246));

        TextView leaf = label(app, "🍃", 38, Color.rgb(52, 116, 76), true);
        root.addView(leaf, wrap());
        TextView title = label(app, "Let your mind breathe", 27, Color.rgb(17, 24, 39), true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = wrap();
        titleParams.topMargin = dp(app, 16);
        root.addView(title, titleParams);
        TextView message = label(app, "Your time for this app is complete.\nThis boundary will lift automatically.", 15, Color.rgb(75, 85, 99), false);
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(0, 1.25f);
        LinearLayout.LayoutParams messageParams = wrap();
        messageParams.topMargin = dp(app, 14);
        root.addView(message, messageParams);
        TextView status = label(app, "FocusLock is protecting your time", 13, Color.WHITE, true);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(app, 18), dp(app, 11), dp(app, 18), dp(app, 11));
        status.setBackground(round(Color.rgb(17, 24, 39), Color.rgb(17, 24, 39), 28));
        LinearLayout.LayoutParams statusParams = wrap();
        statusParams.topMargin = dp(app, 24);
        root.addView(status, statusParams);
        Button home = new Button(app);
        home.setText("Return to Home");
        home.setAllCaps(false);
        home.setTextSize(14);
        home.setTextColor(Color.rgb(17, 24, 39));
        home.setBackground(round(Color.WHITE, Color.rgb(220, 233, 220), 28));
        home.setOnClickListener(v -> app.startActivity(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        LinearLayout.LayoutParams homeParams = wrap();
        homeParams.topMargin = dp(app, 34);
        root.addView(home, homeParams);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE);
        try {
            windowManager.addView(root, params);
            view = root;
            packageName = blockedPackage;
        } catch (RuntimeException ignored) {
            // The monitor retries on the next foreground check.
        }
    }

    public static synchronized void hide(Context context) {
        if (view == null) return;
        try {
            ((WindowManager) context.getApplicationContext().getSystemService(Context.WINDOW_SERVICE)).removeView(view);
        } catch (RuntimeException ignored) { }
        view = null;
        packageName = null;
    }

    private static TextView label(Context context, String text, int size, int color, boolean bold) {
        TextView result = new TextView(context);
        result.setText(text);
        result.setTextSize(size);
        result.setTextColor(color);
        if (bold) result.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return result;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + .5f);
    }

    private static GradientDrawable round(int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(1, stroke);
        return drawable;
    }
}
