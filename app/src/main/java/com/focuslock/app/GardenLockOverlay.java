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
        final int theme = chooseTheme(context, packageName);
        if (root != null) { refresh(); return; }
        try {
            windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager == null) return;

            FrameLayout scene = new FrameLayout(context);
            scene.setBackgroundColor(Color.rgb(247, 245, 239));
            LockCardArtView breeze = new LockCardArtView(context, theme);
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

    private static int chooseTheme(Context context, String packageName) {
        long session = LockStore.lockedUntil(context, packageName);
        android.content.SharedPreferences p = context.getSharedPreferences("focuslock_lock_card_themes", Context.MODE_PRIVATE);
        String key = "card:" + packageName;
        if (p.getLong(key + ":session", Long.MIN_VALUE) == session) return p.getInt(key + ":theme", 0);
        int next = (p.getInt("last_theme", -1) + 1) % 8;
        p.edit().putLong(key + ":session", session).putInt(key + ":theme", next).putInt("last_theme", next).apply();
        return next;
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

        /** The eight supplied lock-card scenes: Dunes, Grove, Horizon, Nightfall, Rainfall, Ridge, Seedling and Tide. */
    private static final class LockCardArtView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); private final android.graphics.Path q=new android.graphics.Path(); private final int theme;
        LockCardArtView(Context c,int theme){super(c);this.theme=theme;p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setColor(Color.argb(58,47,122,74));}
        @Override protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();if(w<1||h<1)return;switch(theme){case 0:dunes(c,w,h);break;case 1:grove(c,w,h);break;case 2:horizon(c,w,h);break;case 3:nightfall(c,w,h);break;case 4:rain(c,w,h);break;case 5:ridge(c,w,h);break;case 6:seed(c,w,h);break;default:tide(c,w,h);}}
        private void dunes(Canvas c,float w,float h){topWaves(c,w,h,.11f,3);sprout(c,w*.79f,h*.06f);birds(c,w*.15f,h*.06f);bottomWaves(c,w,h,.77f,3);sprout(c,w*.78f,h*.88f);sprout(c,w*.15f,h*.92f);}
        private void grove(Canvas c,float w,float h){branch(c,-5,-5,w*.12f,h*.22f);branch(c,w+5,-5,w*.88f,h*.22f);leaf(c,w*.25f,h*.10f);leaf(c,w*.72f,h*.16f);leaf(c,w*.50f,h*.055f);bottomWaves(c,w,h,.78f,2);for(float x:new float[]{.12f,.28f,.74f,.91f})tree(c,w*x,h*.96f);}
        private void horizon(Canvas c,float w,float h){float x=w*.78f,y=h*.065f;c.drawCircle(x,y,d(27),p);for(int i=0;i<8;i++){double a=Math.PI*2*i/8;float r=d(38);c.drawLine(x+(float)Math.cos(a)*r,y+(float)Math.sin(a)*r,x+(float)Math.cos(a)*(r+d(9)),y+(float)Math.sin(a)*(r+d(9)),p);}topWaves(c,w,h,.15f,2);birds(c,w*.17f,h*.085f);bottomWaves(c,w,h,.74f,3);tree(c,w*.16f,h*.96f);tree(c,w*.86f,h*.98f);}
        private void nightfall(Canvas c,float w,float h){for(float[] a:new float[][]{{.19f,.05f},{.80f,.10f},{.36f,.16f},{.67f,.04f}})star(c,w*a[0],h*a[1]);leaf(c,w*.60f,h*.08f);topWaves(c,w,h,.18f,2);bottomWaves(c,w,h,.76f,3);tree(c,w*.25f,h*.96f);tree(c,w*.82f,h*.98f);}
        private void rain(Canvas c,float w,float h){cloud(c,w*.30f,h*.07f);cloud(c,w*.77f,h*.14f);for(int i=0;i<11;i++)drop(c,w*(.12f+i*.075f),h*(.15f+(i%3)*.025f));topWaves(c,w,h,.72f,1);pond(c,w*.29f,h*.89f);pond(c,w*.74f,h*.94f);tree(c,w*.07f,h*.97f);tree(c,w*.94f,h*.97f);}
        private void ridge(Canvas c,float w,float h){topWaves(c,w,h,.035f,4);birds(c,w*.17f,h*.06f);q.reset();q.moveTo(-d(14),h*.82f);q.lineTo(w*.18f,h*.63f);q.lineTo(w*.30f,h*.73f);q.lineTo(w*.42f,h*.59f);q.lineTo(w*.60f,h*.76f);q.lineTo(w*.73f,h*.67f);q.lineTo(w+d(14),h*.84f);c.drawPath(q,p);topWaves(c,w,h,.93f,1);tree(c,w*.30f,h*.98f);tree(c,w*.81f,h*.98f);}
        private void seed(Canvas c,float w,float h){q.reset();q.moveTo(-d(6),h*.14f);q.cubicTo(w*.10f,h*.02f,w*.28f,h*.01f,w*.5f,h*.01f);q.cubicTo(w*.72f,h*.01f,w*.90f,h*.02f,w+d(6),h*.14f);c.drawPath(q,p);for(int i=1;i<5;i++)c.drawLine(w*i/5f,h*.02f,w*i/5f,h*.14f,p);c.drawLine(w*.07f,h*.085f,w*.93f,h*.085f,p);c.drawLine(w*.11f,h*.05f,w*.89f,h*.05f,p);sprout(c,w*.29f,h*.09f);sprout(c,w*.71f,h*.07f);birds(c,w*.48f,h*.18f);bottomWaves(c,w,h,.72f,3);sprout(c,w*.12f,h*.87f);sprout(c,w*.87f,h*.91f);}
        private void tide(Canvas c,float w,float h){topWaves(c,w,h,.06f,3);birds(c,w*.18f,h*.06f);sprout(c,w*.74f,h*.18f);bottomWaves(c,w,h,.70f,4);boat(c,w*.74f,h*.74f);}
        private void cloud(Canvas c,float x,float y){c.drawCircle(x-d(17),y+d(6),d(13),p);c.drawCircle(x,y,d(20),p);c.drawCircle(x+d(21),y+d(8),d(14),p);c.drawLine(x-d(30),y+d(20),x+d(34),y+d(20),p);}
        private void drop(Canvas c,float x,float y){c.drawLine(x,y,x-d(6),y+d(18),p);}
        private void pond(Canvas c,float x,float y){c.drawOval(x-d(48),y-d(10),x+d(48),y+d(10),p);c.drawOval(x-d(28),y-d(6),x+d(28),y+d(6),p);}
        private void star(Canvas c,float x,float y){float s=d(9);q.reset();q.moveTo(x,y-s);q.lineTo(x+d(3),y-d(3));q.lineTo(x+s,y);q.lineTo(x+d(3),y+d(3));q.lineTo(x,y+s);q.lineTo(x-d(3),y+d(3));q.lineTo(x-s,y);q.lineTo(x-d(3),y-d(3));q.close();c.drawPath(q,p);}
        private void boat(Canvas c,float x,float y){c.drawLine(x-d(25),y,x+d(25),y,p);c.drawLine(x-d(15),y,x-d(8),y+d(12),p);c.drawLine(x+d(15),y,x+d(8),y+d(12),p);c.drawLine(x,y,x,y-d(32),p);q.reset();q.moveTo(x,y-d(28));q.lineTo(x+d(22),y-d(10));q.lineTo(x,y-d(10));q.close();c.drawPath(q,p);}
        private void tree(Canvas c,float x,float y){c.drawLine(x,y,x,y-d(38),p);c.drawLine(x,y-d(24),x-d(15),y-d(42),p);c.drawLine(x,y-d(19),x+d(15),y-d(34),p);}
        private void sprout(Canvas c,float x,float y){c.drawLine(x,y,x,y+d(25),p);leaf(c,x-d(7),y+d(3));leaf(c,x+d(7),y+d(9));}
        private void leaf(Canvas c,float x,float y){float s=d(16);q.reset();q.moveTo(x-s,y);q.quadTo(x,y-s*.65f,x+s,y);q.quadTo(x,y+s*.65f,x-s,y);c.drawPath(q,p);}
        private void birds(Canvas c,float x,float y){bird(c,x,y,1);bird(c,x+d(42),y-d(18),.75f);}
        private void bird(Canvas c,float x,float y,float sc){float s=d(10)*sc;q.reset();q.moveTo(x-s,y);q.quadTo(x-s*.45f,y-s*.7f,x,y);q.quadTo(x+s*.45f,y-s*.7f,x+s,y);c.drawPath(q,p);}
        private void branch(Canvas c,float x1,float y1,float x2,float y2){q.reset();q.moveTo(x1,y1);q.cubicTo(x1+(x2-x1)*.4f,y1+(y2-y1)*.25f,x1+(x2-x1)*.75f,y1+(y2-y1)*.72f,x2,y2);c.drawPath(q,p);}
        private void topWaves(Canvas c,float w,float h,float start,int n){for(int i=0;i<n;i++)wave(c,w,h*(start+i*.05f));}
        private void bottomWaves(Canvas c,float w,float h,float start,int n){for(int i=0;i<n;i++)wave(c,w,h*(start+i*.07f));}
        private void wave(Canvas c,float w,float y){q.reset();q.moveTo(-d(14),y);q.cubicTo(w*.15f,y-d(22),w*.29f,y-d(19),w*.42f,y);q.cubicTo(w*.56f,y+d(18),w*.72f,y+d(14),w+d(14),y-d(2));c.drawPath(q,p);}
        private float d(float v){return v*getResources().getDisplayMetrics().density;}
    }

}