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
        final boolean roots = (packageName.hashCode() & 1) == 0;
        if (root != null) { refresh(); return; }
        try {
            windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager == null) return;

            FrameLayout scene = new FrameLayout(context);
            scene.setBackgroundColor(Color.rgb(247, 245, 239));
            LockCardArtView breeze = new LockCardArtView(context, roots);
            scene.addView(breeze, new FrameLayout.LayoutParams(-1, -1));

            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER_HORIZONTAL);
            content.setPadding(dp(context, 28), dp(context, 25), dp(context, 28), dp(context, 26));

            TextView label = text(context, "FOCUSLOCK", 11, Color.rgb(23, 83, 46), true);
            label.setLetterSpacing(.14f); label.setGravity(Gravity.CENTER);
            content.addView(label, match());

            View topSpace = new View(context);
            content.addView(topSpace, new LinearLayout.LayoutParams(1, 0, .78f));
            TextView app = text(context, appName(context, packageName) + " is paused", 14, Color.rgb(91, 107, 95), false);
            app.setGravity(Gravity.CENTER); content.addView(app, match());
            TextView title = text(context, "Take a quiet moment", 26, Color.rgb(19, 42, 28), true);
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleParams = match(); titleParams.topMargin = dp(context, 12);
            content.addView(title, titleParams);

            FrameLayout timerStage = new FrameLayout(context);
            timerStage.addView(new RingView(context), new FrameLayout.LayoutParams(dp(context, 232), dp(context, 232), Gravity.CENTER));
            LinearLayout timer = new LinearLayout(context);
            timer.setOrientation(LinearLayout.VERTICAL); timer.setGravity(Gravity.CENTER);
            timer.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
            timer.setBackgroundColor(Color.TRANSPARENT);
            TextView unlocks = text(context, "UNLOCKS IN", 10, Color.rgb(91, 107, 95), true);
            unlocks.setLetterSpacing(.12f); unlocks.setGravity(Gravity.CENTER);
            timer.addView(unlocks, match());
            countdown = text(context, "00:00", 38, Color.rgb(19, 42, 28), true);
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

            TextView reminder = text(context, "A small pause protects a bigger purpose.", 16, Color.rgb(19, 42, 28), true);
            reminder.setGravity(Gravity.CENTER); reminder.setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 18));
            reminder.setBackground(shape(context, Color.argb(235,255,255,255), Color.rgb(205,220,205), 22));
            LinearLayout.LayoutParams reminderParams = match(); reminderParams.topMargin = dp(context, 17);
            content.addView(reminder, reminderParams);
            View lowerSpace = new View(context);
            content.addView(lowerSpace, new LinearLayout.LayoutParams(1, 0, .88f));
            Button home = new Button(context);
            home.setText("Return to home"); home.setAllCaps(false); home.setTextSize(13); home.setTextColor(Color.rgb(23,83,46));
            home.setBackground(shape(context, Color.argb(31,107,59,30), Color.argb(76,31,107,59), 28));
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
    private static float dp(Context context, float value) { return value * context.getResources().getDisplayMetrics().density; }

    private static final class RingView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        RingView(Context context) { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            float c=getWidth()/2f,r=getWidth()/2f-dp(getContext(),14); RectF oval=new RectF(c-r,c-r,c+r,c+r);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeWidth(dp(getContext(),1)); paint.setColor(Color.argb(52,47,122,74)); canvas.drawCircle(c,c,r,paint);
            paint.setStrokeWidth(dp(getContext(),1.6f)); paint.setColor(Color.argb(140,47,122,74)); canvas.drawArc(oval,-90,180,false,paint);
        }
    }

    /** Only the supplied Roots and Horizon illustrations; shared by the fallback overlay. */
    private static final class LockCardArtView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); private final android.graphics.Path path=new android.graphics.Path(); private final boolean roots;
        LockCardArtView(Context context,boolean roots){super(context);this.roots=roots;paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
        @Override protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;if(roots)roots(c,w,h);else horizon(c,w,h);}
        private void roots(Canvas c,float w,float h){paint.setColor(Color.argb(42,47,122,74));paint.setStrokeWidth(dp(getContext(),1.25f));float x=w*.5f;c.drawCircle(x,0,dp(getContext(),70),paint);c.drawCircle(x,0,dp(getContext(),92),paint);c.drawCircle(x,0,dp(getContext(),112),paint);path.reset();path.moveTo(x,0);path.cubicTo(x+2,h*.10f,x-6,h*.16f,x,h*.24f);path.cubicTo(x-4,h*.40f,x,h*.52f,x,h*.58f);c.drawPath(path,paint);branch(c,x,h*.12f,w*.25f,h*.28f);branch(c,x,h*.20f,w*.75f,h*.34f);branch(c,x,h*.35f,w*.23f,h*.48f);branch(c,x,h*.45f,w*.70f,h*.58f);waves(c,w,h*.78f);waves(c,w,h*.85f);}
        private void horizon(Canvas c,float w,float h){paint.setColor(Color.argb(51,47,122,74));paint.setStrokeWidth(dp(getContext(),1f));path.reset();path.moveTo(-6,20);path.cubicTo(w*.18f,34,w*.33f,8,w*.49f,22);path.cubicTo(w*.65f,36,w*.79f,30,w+6,8);c.drawPath(path,paint);float sx=w*.733f,sy=h*.123f;paint.setColor(Color.argb(61,47,122,74));paint.setStrokeWidth(dp(getContext(),1.1f));c.drawCircle(sx,sy,dp(getContext(),34),paint);for(int i=0;i<8;i++){double a=Math.PI*2*i/8d;float r1=dp(getContext(),48),r2=dp(getContext(),60);c.drawLine(sx+(float)Math.cos(a)*r1,sy+(float)Math.sin(a)*r1,sx+(float)Math.cos(a)*r2,sy+(float)Math.sin(a)*r2,paint);}paint.setColor(Color.argb(41,47,122,74));paint.setStrokeWidth(dp(getContext(),1.2f));waves(c,w,h*.66f);waves(c,w,h*.73f);waves(c,w,h*.80f);waves(c,w,h*.87f);}
        private void branch(Canvas c,float x1,float y1,float x2,float y2){path.reset();path.moveTo(x1,y1);path.cubicTo(x1+(x2-x1)*.42f,y1+(y2-y1)*.22f,x1+(x2-x1)*.75f,y1+(y2-y1)*.72f,x2,y2);c.drawPath(path,paint);}
        private void waves(Canvas c,float w,float y){path.reset();path.moveTo(-14,y);path.cubicTo(w*.15f,y-dp(getContext(),28),w*.29f,y-dp(getContext(),26),w*.42f,y);path.cubicTo(w*.56f,y+dp(getContext(),22),w*.72f,y+dp(getContext(),18),w+14,y-2);c.drawPath(path,paint);}
    }
}
