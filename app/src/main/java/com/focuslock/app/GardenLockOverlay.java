package com.focuslock.app;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The same garden presentation used as a safety net only when Android refuses
 * to open BlockActivity. It contains no legacy lock-screen layout or behavior.
 */
public final class GardenLockOverlay {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WindowManager windowManager;
    private static View root;
    private static TextView countdown;
    private static String blockedPackage;
    private static Context appContext;

    private GardenLockOverlay() { }

    public static void show(Context context, String packageName) {
        if (context == null || packageName == null || !android.provider.Settings.canDrawOverlays(context)) return;
        MAIN.post(() -> showOnMain(context.getApplicationContext(), packageName));
    }

    public static void hide() { MAIN.post(GardenLockOverlay::hideOnMain); }
    public static boolean isShowing() { return root != null; }

    private static void showOnMain(Context context, String packageName) {
        appContext = context;
        blockedPackage = packageName;
        if (root != null) { refresh(); return; }
        try {
            windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager == null) return;

            FrameLayout scene = new FrameLayout(context);
            scene.setBackgroundColor(Color.rgb(246, 250, 246));
            GardenBreezeView breeze = new GardenBreezeView(context);
            scene.addView(breeze, new FrameLayout.LayoutParams(-1, -1));

            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER_HORIZONTAL);
            content.setPadding(dp(context, 28), dp(context, 25), dp(context, 28), dp(context, 26));

            TextView label = text(context, "FOCUSLOCK", 11, Color.rgb(52, 116, 76), true);
            label.setLetterSpacing(.14f); label.setGravity(Gravity.CENTER);
            content.addView(label, match());

            View topSpace = new View(context);
            content.addView(topSpace, new LinearLayout.LayoutParams(1, 0, .78f));
            TextView app = text(context, appName(context, packageName) + " is paused", 14, Color.rgb(107, 114, 128), false);
            app.setGravity(Gravity.CENTER); content.addView(app, match());
            TextView title = text(context, "Take a quiet moment", 28, Color.rgb(17, 24, 39), true);
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleParams = match(); titleParams.topMargin = dp(context, 12);
            content.addView(title, titleParams);

            FrameLayout timerStage = new FrameLayout(context);
            timerStage.addView(new RingView(context), new FrameLayout.LayoutParams(dp(context, 232), dp(context, 232), Gravity.CENTER));
            LinearLayout timer = new LinearLayout(context);
            timer.setOrientation(LinearLayout.VERTICAL); timer.setGravity(Gravity.CENTER);
            timer.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
            timer.setBackground(timerShape(context));
            TextView unlocks = text(context, "UNLOCKS IN", 10, Color.rgb(184, 231, 196), true);
            unlocks.setLetterSpacing(.12f); unlocks.setGravity(Gravity.CENTER);
            timer.addView(unlocks, match());
            countdown = text(context, "00:00", 42, Color.WHITE, true);
            countdown.setGravity(Gravity.CENTER); countdown.setIncludeFontPadding(false);
            LinearLayout.LayoutParams countParams = match(); countParams.topMargin = dp(context, 8);
            timer.addView(countdown, countParams);
            timerStage.addView(timer, new FrameLayout.LayoutParams(dp(context, 196), dp(context, 196), Gravity.CENTER));
            LinearLayout.LayoutParams stageParams = match(); stageParams.height = dp(context, 236); stageParams.topMargin = dp(context, 12);
            content.addView(timerStage, stageParams);
            ObjectAnimator pulse = ObjectAnimator.ofFloat(timer, "scaleX", 1f, 1.02f);
            pulse.setDuration(1_800L); pulse.setRepeatCount(ObjectAnimator.INFINITE); pulse.setRepeatMode(ObjectAnimator.REVERSE);
            pulse.setInterpolator(new AccelerateDecelerateInterpolator()); pulse.start();
            ObjectAnimator pulseY = ObjectAnimator.ofFloat(timer, "scaleY", 1f, 1.02f);
            pulseY.setDuration(1_800L); pulseY.setRepeatCount(ObjectAnimator.INFINITE); pulseY.setRepeatMode(ObjectAnimator.REVERSE);
            pulseY.setInterpolator(new AccelerateDecelerateInterpolator()); pulseY.start();

            TextView reminder = text(context, "You chose focus. Let this moment pass.", 16, Color.rgb(17, 24, 39), true);
            reminder.setGravity(Gravity.CENTER); reminder.setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 18));
            reminder.setBackground(shape(context, Color.WHITE, Color.rgb(220, 233, 220), 24));
            LinearLayout.LayoutParams reminderParams = match(); reminderParams.topMargin = dp(context, 17);
            content.addView(reminder, reminderParams);
            View lowerSpace = new View(context);
            content.addView(lowerSpace, new LinearLayout.LayoutParams(1, 0, .88f));
            Button home = new Button(context);
            home.setText("Return to home"); home.setAllCaps(false); home.setTextSize(13); home.setTextColor(Color.rgb(52, 116, 76));
            home.setBackground(shape(context, Color.rgb(238, 247, 239), Color.rgb(194, 222, 199), 26));
            home.setOnClickListener(v -> goHome(context));
            content.addView(home, match());
            scene.addView(content, new FrameLayout.LayoutParams(-1, -1));

            int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -1, type,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, android.graphics.PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.CENTER;
            windowManager.addView(scene, params);
            root = scene;
            refresh();
        } catch (RuntimeException error) {
            root = null;
            DiagnosticStore.record(context, "garden_overlay_show_failed", error.getClass().getSimpleName());
        }
    }

    private static void refresh() {
        if (root == null || countdown == null || appContext == null || blockedPackage == null) return;
        if (!LockStore.isLocked(appContext, blockedPackage)) { hideOnMain(); return; }
        long remaining = Math.max(0L, LockStore.lockedUntil(appContext, blockedPackage) - System.currentTimeMillis());
        long seconds = (remaining + 999L) / 1_000L;
        countdown.setText(String.format(java.util.Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L));
        MAIN.postDelayed(GardenLockOverlay::refresh, 1_000L);
    }

    private static void goHome(Context context) {
        hideOnMain();
        context.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

    private static void hideOnMain() {
        MAIN.removeCallbacksAndMessages(null);
        if (windowManager != null && root != null) try { windowManager.removeViewImmediate(root); } catch (RuntimeException ignored) { }
        root = null; countdown = null; blockedPackage = null; windowManager = null; appContext = null;
    }

    private static String appName(Context context, String packageName) {
        try { return context.getPackageManager().getApplicationLabel(context.getPackageManager().getApplicationInfo(packageName, 0)).toString(); }
        catch (Exception ignored) { return "Your app"; }
    }
    private static TextView text(Context context, String value, int size, int color, boolean bold) {
        TextView view = new TextView(context); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD); return view;
    }
    private static LinearLayout.LayoutParams match() { return new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT); }
    private static GradientDrawable shape(Context context, int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setStroke(dp(context, 1), stroke); d.setCornerRadius(dp(context, radius)); return d;
    }
    private static GradientDrawable timerShape(Context context) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] { Color.rgb(9, 13, 18), Color.rgb(17, 24, 39), Color.rgb(17, 31, 25) });
        d.setShape(GradientDrawable.OVAL); d.setStroke(dp(context, 1), Color.rgb(65, 139, 86)); return d;
    }
    private static int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    private static final class RingView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        RingView(Context context) { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            float c = getWidth() / 2f, r = getWidth() / 2f - dp(getContext(), 14);
            RectF oval = new RectF(c - r, c - r, c + r, c + r);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(getContext(), 13)); paint.setColor(Color.argb(32, 52, 116, 76)); canvas.drawArc(oval, -90, 360, false, paint);
            paint.setStrokeWidth(dp(getContext(), 7)); paint.setColor(Color.rgb(76, 157, 98)); canvas.drawArc(oval, -90, 360, false, paint);
        }
    }

    private static final class GardenBreezeView extends View {
        private static final String[] EMOJIS = { "🍃", "🫧", "🌿", "🌱", "🌸" };
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        GardenBreezeView(Context context) { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            long now = SystemClock.uptimeMillis(); float w = getWidth(), h = getHeight();
            for (int i = 0; i < EMOJIS.length; i++) {
                float life = ((now % 11_000L) + i * 2_200L) % 11_000L / 11_000f;
                float x = i % 2 == 0 ? -32 + (w + 64) * life : w + 32 - (w + 64) * life;
                float y = h * (.16f + i * .15f) + (float) Math.sin(life * 6.28 + i) * 20;
                paint.setTextSize(dp(getContext(), 22 + i % 3 * 3)); paint.setAlpha((int) (120 * Math.min(1f, Math.min(life / .18f, (1f - life) / .18f))));
                canvas.drawText(EMOJIS[i], x, y, paint);
            }
            postInvalidateDelayed(16);
        }
    }
}
