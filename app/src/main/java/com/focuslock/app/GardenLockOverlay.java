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
        final boolean night = isNightTheme(context);
        if (root != null) { refresh(); return; }
        try {
            windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager == null) return;

            FrameLayout scene = new FrameLayout(context);
            scene.setBackgroundColor(night ? Color.rgb(15, 29, 22) : Color.rgb(247, 245, 239));
            GardenBreezeView breeze = new GardenBreezeView(context, night);
            scene.addView(breeze, new FrameLayout.LayoutParams(-1, -1));

            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER_HORIZONTAL);
            content.setPadding(dp(context, 28), dp(context, 25), dp(context, 28), dp(context, 26));

            TextView label = text(context, "FOCUSLOCK", 11, night ? Color.rgb(232, 180, 92) : Color.rgb(23, 83, 46), true);
            label.setLetterSpacing(.14f); label.setGravity(Gravity.CENTER);
            content.addView(label, match());

            View topSpace = new View(context);
            content.addView(topSpace, new LinearLayout.LayoutParams(1, 0, .78f));
            TextView app = text(context, appName(context, packageName) + " is paused", 14, night ? Color.rgb(169, 190, 174) : Color.rgb(91, 107, 95), false);
            app.setGravity(Gravity.CENTER); content.addView(app, match());
            TextView title = text(context, "Take a quiet moment", 26, night ? Color.rgb(239, 234, 217) : Color.rgb(19, 42, 28), true);
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleParams = match(); titleParams.topMargin = dp(context, 12);
            content.addView(title, titleParams);

            FrameLayout timerStage = new FrameLayout(context);
            timerStage.addView(new RingView(context), new FrameLayout.LayoutParams(dp(context, 232), dp(context, 232), Gravity.CENTER));
            LinearLayout timer = new LinearLayout(context);
            timer.setOrientation(LinearLayout.VERTICAL); timer.setGravity(Gravity.CENTER);
            timer.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
            timer.setBackgroundColor(Color.TRANSPARENT);
            TextView unlocks = text(context, "UNLOCKS IN", 10, night ? Color.rgb(169, 190, 174) : Color.rgb(91, 107, 95), true);
            unlocks.setLetterSpacing(.12f); unlocks.setGravity(Gravity.CENTER);
            timer.addView(unlocks, match());
            countdown = text(context, "00:00", 38, night ? Color.rgb(239, 234, 217) : Color.rgb(19, 42, 28), true);
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

            TextView reminder = text(context, "A small pause protects a bigger purpose.", 16, night ? Color.rgb(239, 234, 217) : Color.rgb(19, 42, 28), true);
            reminder.setGravity(Gravity.CENTER); reminder.setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 18));
            reminder.setBackground(shape(context, night ? Color.rgb(15, 29, 22) : Color.argb(235,255,255,255), night ? Color.rgb(71,97,80) : Color.rgb(205,220,205), 22));
            LinearLayout.LayoutParams reminderParams = match(); reminderParams.topMargin = dp(context, 17);
            content.addView(reminder, reminderParams);
            View lowerSpace = new View(context);
            content.addView(lowerSpace, new LinearLayout.LayoutParams(1, 0, .88f));
            Button home = new Button(context);
            home.setText("Return to home"); home.setAllCaps(false); home.setTextSize(13); home.setTextColor(night ? Color.rgb(232,180,92) : Color.rgb(23,83,46));
            home.setBackground(shape(context, night ? Color.argb(36,232,180,92) : Color.argb(31,107,59,30), night ? Color.argb(95,232,180,92) : Color.argb(76,31,107,59), 28));
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

    private static boolean isNightTheme(Context context) {
        int mode = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
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

    /** Same Horizon/Nightfall artwork for the overlay fallback. */
    private static final class GardenBreezeView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path path = new android.graphics.Path();
        private final boolean night;
        GardenBreezeView(Context context, boolean night) {
            super(context); this.night = night;
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        }
        @Override protected void onDraw(Canvas canvas) {
            float w=getWidth(),h=getHeight(); if(w<=0||h<=0)return;
            float t=SystemClock.uptimeMillis()/1000f;
            paint.setStrokeWidth(dp(getContext(), 1.2f));
            if(night) {
                paint.setColor(Color.argb(112,127,191,151));
                float[] xs={.15f,.82f,.11f,.90f,.18f,.85f,.13f,.88f};
                float[] ys={.09f,.15f,.36f,.42f,.62f,.67f,.86f,.91f};
                for(int i=0;i<xs.length;i++){float x=w*xs[i],y=h*ys[i],s=dp(getContext(),3+(i%2));float a=.45f+.55f*(float)((Math.sin(t*.9f+i)+1)/2);paint.setAlpha((int)(125*a));canvas.drawLine(x-s,y,x+s,y,paint);canvas.drawLine(x,y-s,x,y+s,paint);}
                paint.setAlpha(110);float mx=w*.78f,my=h*.10f;path.reset();path.moveTo(mx,my-dp(getContext(),20));path.arcTo(mx-dp(getContext(),24),my-dp(getContext(),24),mx+dp(getContext(),24),my+dp(getContext(),24),-72,285,false);canvas.drawPath(path,paint);
                paint.setAlpha(72);path.reset();path.moveTo(-dp(getContext(),14),h*.80f);path.cubicTo(w*.15f,h*.70f,w*.29f,h*.70f,w*.42f,h*.77f);path.cubicTo(w*.58f,h*.84f,w*.72f,h*.70f,w+dp(getContext(),14),h*.77f);canvas.drawPath(path,paint);
            } else {
                paint.setColor(Color.argb(60,47,122,74));float drift=(float)Math.sin(t*.24f)*dp(getContext(),3);path.reset();path.moveTo(-dp(getContext(),8),dp(getContext(),26)+drift);path.cubicTo(w*.18f,dp(getContext(),39),w*.36f,dp(getContext(),9),w*.5f,dp(getContext(),26));path.cubicTo(w*.68f,dp(getContext(),42),w*.82f,dp(getContext(),33),w+dp(getContext(),8),dp(getContext(),12));canvas.drawPath(path,paint);
                float sx=w*.73f,sy=h*.125f,p=(float)Math.sin(t*1.1f);paint.setColor(Color.argb(74,47,122,74));canvas.drawCircle(sx,sy,dp(getContext(),34)+p*dp(getContext(),2),paint);
                paint.setColor(Color.argb(46,47,122,74));path.reset();path.moveTo(-dp(getContext(),14),h*.74f);path.cubicTo(w*.15f,h*.65f,w*.30f,h*.68f,w*.42f,h*.74f);path.cubicTo(w*.56f,h*.81f,w*.72f,h*.68f,w+dp(getContext(),14),h*.76f);canvas.drawPath(path,paint);
            }
            postInvalidateDelayed(40L);
        }
        private static float dp(Context c,float v){return v*c.getResources().getDisplayMetrics().density;}
    }

}
