package com.focuslock.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Immediate overlay safety net for phones that restrict background activity
 * launches. BlockActivity dismisses this as soon as the regular lock screen is
 * visible, so the existing UI remains unchanged on normal devices.
 */
public final class BlockOverlay {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WindowManager windowManager;
    private static View root;
    private static TextView countdown;
    private static String blockedPackage;
    private static Context appContext;

    private BlockOverlay() {}

    public static void show(Context context, String packageName) {
        if (context == null || packageName == null || !android.provider.Settings.canDrawOverlays(context)) return;
        MAIN.post(() -> showOnMain(context.getApplicationContext(), packageName));
    }

    public static void hide() {
        MAIN.post(BlockOverlay::hideOnMain);
    }

    public static boolean isShowing() {
        return root != null;
    }

    private static void showOnMain(Context context, String packageName) {
        appContext = context;
        blockedPackage = packageName;
        if (root != null) {
            refresh();
            return;
        }
        try {
            windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager == null) return;

            LinearLayout panel = new LinearLayout(context);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setGravity(Gravity.CENTER);
            panel.setPadding(dp(context, 28), dp(context, 28), dp(context, 28), dp(context, 28));
            panel.setBackgroundColor(Color.rgb(246, 250, 246));

            TextView label = text(context, "FOCUSLOCK", 12, Color.rgb(52, 116, 76), true);
            label.setLetterSpacing(.14f);
            label.setGravity(Gravity.CENTER);
            panel.addView(label, match());

            TextView breeze = text(context, "🍃        🫧        🍃", 18, Color.rgb(52, 116, 76), false);
            breeze.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams breezeLp = match();
            breezeLp.topMargin = dp(context, 14);
            panel.addView(breeze, breezeLp);

            TextView app = text(context, appName(context, packageName) + " is paused", 13, Color.rgb(107, 114, 128), false);
            app.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams appLp = match();
            appLp.topMargin = dp(context, 26);
            panel.addView(app, appLp);

            TextView title = text(context, "Take a quiet moment", 27, Color.rgb(17, 24, 39), true);
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleLp = match();
            titleLp.topMargin = dp(context, 10);
            panel.addView(title, titleLp);

            countdown = text(context, "00:00", 50, Color.WHITE, true);
            countdown.setGravity(Gravity.CENTER);
            countdown.setPadding(dp(context, 20), dp(context, 22), dp(context, 20), dp(context, 22));
            countdown.setBackground(shape(context, Color.rgb(17, 24, 39), Color.rgb(52, 116, 76), 30));
            LinearLayout.LayoutParams timeLp = match();
            timeLp.topMargin = dp(context, 30);
            panel.addView(countdown, timeLp);

            TextView note = text(context, "You chose focus. Let this moment pass.", 14, Color.rgb(17, 24, 39), true);
            note.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams noteLp = match();
            noteLp.topMargin = dp(context, 24);
            panel.addView(note, noteLp);

            Button home = new Button(context);
            home.setText("Return to home");
            home.setAllCaps(false);
            home.setTextColor(Color.rgb(52, 116, 76));
            home.setTextSize(14);
            home.setBackground(shape(context, Color.WHITE, Color.rgb(194, 222, 199), 26));
            home.setOnClickListener(v -> {
                hideOnMain();
                Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(intent);
            });
            LinearLayout.LayoutParams homeLp = match();
            homeLp.topMargin = dp(context, 42);
            panel.addView(home, homeLp);

            int type = Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    type,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.CENTER;
            windowManager.addView(panel, params);
            root = panel;
            refresh();
        } catch (RuntimeException error) {
            root = null;
            DiagnosticStore.record(context, "overlay_show_failed", error.getClass().getSimpleName());
        }
    }

    private static void refresh() {
        if (root == null || countdown == null || appContext == null || blockedPackage == null) return;
        if (!LockStore.isLocked(appContext, blockedPackage)) {
            hideOnMain();
            return;
        }
        long remaining = Math.max(0L, LockStore.lockedUntil(appContext, blockedPackage) - System.currentTimeMillis());
        long seconds = (remaining + 999L) / 1000L;
        countdown.setText(String.format(java.util.Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L));
        MAIN.postDelayed(BlockOverlay::refresh, 1_000L);
    }

    private static void hideOnMain() {
        MAIN.removeCallbacksAndMessages(null);
        if (windowManager != null && root != null) {
            try { windowManager.removeViewImmediate(root); } catch (RuntimeException ignored) {}
        }
        root = null;
        countdown = null;
        blockedPackage = null;
        windowManager = null;
        appContext = null;
    }

    private static TextView text(Context context, String value, int size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }

    private static String appName(Context context, String packageName) {
        try {
            return context.getPackageManager().getApplicationLabel(
                    context.getPackageManager().getApplicationInfo(packageName, 0)).toString();
        } catch (Exception ignored) {
            return "Your app";
        }
    }

    private static LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static GradientDrawable shape(Context context, int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(context, 1), stroke);
        drawable.setCornerRadius(dp(context, radius));
        return drawable;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
