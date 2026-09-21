package com.focuslock.app;

import android.app.Activity;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

public class BlockActivity extends Activity {
    private static volatile boolean visible;
    private static final String[] REMINDERS = {
            "Take a breath. This urge will pass.",
            "A quiet minute can protect your afternoon.",
            "Choose where your attention grows.",
            "Slow down. Let the moment pass.",
            "Your time is worth protecting.",
            "Small pauses build strong habits.",
            "Let your earlier decision support you.",
            "Look away. Relax your shoulders.",
            "Inhale, exhale, begin again.",
            "Spend your attention with intention.",
            "One protected moment can reset your day."
    };
    private static final int INK = Color.rgb(17, 24, 39);
    private static final int MUTED = Color.rgb(107, 114, 128);
    private static final int FAINT = Color.rgb(156, 163, 175);
    private static final int VIOLET = Color.rgb(52, 116, 76);
    private static final int BORDER = Color.rgb(220, 233, 220);
    private String blockedPackage;
    private TextView timerText;
    private TextView lockIcon;
    private ProgressRingView timerRing;
    private long timerDurationMs;
    private CountDownTimer timer;
    private LinearLayout timerCard;
    private LinearLayout reminderCard;
    private LockCardArtView lockCardArt;
    private boolean smoothEntry;
    private boolean nightTheme;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!AccessStore.isAllowed(this) || !RemoteConfigStore.appBlockingEnabled(this)) { finish(); return; }
        blockedPackage = getIntent().getStringExtra("blocked_package");
        if (blockedPackage == null || !LockStore.isLocked(this, blockedPackage)) { finish(); return; }
        smoothEntry = getIntent().getBooleanExtra("smooth_entry", false);
        nightTheme = false;
        setContentView(buildUi());
        startTimer();
        if (!smoothEntry) startAnimations();
    }

    public static boolean isVisible() { return visible; }

    @Override protected void onResume() {
        super.onResume();
        visible = true;
        // The normal lock screen is now visible, so remove the emergency overlay
        // used only on devices that restrict background activity launches.
        GardenLockOverlay.hide();
    }

    @Override protected void onPause() {
        visible = false;
        super.onPause();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && lockCardArt != null) {
            lockCardArt.gatherAt(event.getX(), event.getY());
        }
        return super.dispatchTouchEvent(event);
    }

    private ViewGroup buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(22), dp(24), dp(24));
        root.setBackgroundColor(Color.TRANSPARENT);
        if (!smoothEntry) {
            root.setAlpha(0f);
            root.animate().alpha(1f).setDuration(450).start();
        }

        TextView top = text("FOCUSLOCK", 11, nightTheme ? Color.rgb(232, 180, 92) : Color.rgb(23, 83, 46), true);
        top.setLetterSpacing(.14f);
        top.setGravity(Gravity.CENTER);
        root.addView(top, matchWrap());

        Space upper = new Space(this);
        root.addView(upper, new LinearLayout.LayoutParams(1, 0, .9f));

        TextView status = text(appName() + " is paused", 14, nightTheme ? Color.rgb(169, 190, 174) : Color.rgb(91, 107, 95), false);
        status.setGravity(Gravity.CENTER);
        root.addView(status, matchWrap());

        TextView title = text("Take a quiet moment", 26, nightTheme ? Color.rgb(239, 234, 217) : Color.rgb(19, 42, 28), true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = matchWrap(); titleLp.topMargin = dp(12);
        root.addView(title, titleLp);

        FrameLayout timerStage = new FrameLayout(this);
        timerRing = new ProgressRingView(this);
        FrameLayout.LayoutParams ringLp = new FrameLayout.LayoutParams(dp(232), dp(232), Gravity.CENTER);
        timerStage.addView(timerRing, ringLp);

        timerCard = new LinearLayout(this);
        timerCard.setOrientation(LinearLayout.VERTICAL);
        timerCard.setGravity(Gravity.CENTER);
        timerCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        timerCard.setBackgroundColor(Color.TRANSPARENT);
        TextView timerLabel = text("UNLOCKS IN", 10, nightTheme ? Color.rgb(169, 190, 174) : Color.rgb(91, 107, 95), true);
        timerLabel.setLetterSpacing(.12f);
        timerLabel.setGravity(Gravity.CENTER);
        timerCard.addView(timerLabel, matchWrap());
        timerText = text("00:00", 38, nightTheme ? Color.rgb(239, 234, 217) : Color.rgb(19, 42, 28), true);
        timerText.setGravity(Gravity.CENTER);
        timerText.setIncludeFontPadding(false);
        LinearLayout.LayoutParams timerValueLp = matchWrap(); timerValueLp.topMargin = dp(8);
        timerCard.addView(timerText, timerValueLp);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(dp(196), dp(196), Gravity.CENTER);
        timerStage.addView(timerCard, cardLp);
        LinearLayout.LayoutParams timerStageLp = matchWrap(); timerStageLp.height = dp(236); timerStageLp.topMargin = dp(12);
        root.addView(timerStage, timerStageLp);

        reminderCard = new LinearLayout(this);
        reminderCard.setOrientation(LinearLayout.VERTICAL);
        reminderCard.setPadding(dp(20), dp(18), dp(20), dp(18));
        reminderCard.setBackground(shape(nightTheme ? Color.rgb(15, 29, 22) : Color.argb(235, 255, 255, 255), nightTheme ? Color.rgb(71, 97, 80) : Color.rgb(205, 220, 205), 22));
        String[] reminders = RemoteConfigStore.reminders(this, REMINDERS);
        TextView quote = text(reminders[LockStore.nextReminderIndex(this, reminders.length)], 16, nightTheme ? Color.rgb(239, 234, 217) : Color.rgb(19, 42, 28), true);
        quote.setGravity(Gravity.CENTER);
        quote.setLineSpacing(0, 1.24f);
        LinearLayout.LayoutParams quoteLp = matchWrap();
        reminderCard.addView(quote, quoteLp);
        LinearLayout.LayoutParams reminderLp = matchWrap(); reminderLp.topMargin = dp(18);
        root.addView(reminderCard, reminderLp);

        Space lower = new Space(this);
        root.addView(lower, new LinearLayout.LayoutParams(1, 0, 1f));
        Button home = new Button(this);
        home.setText("Return to home");
        home.setAllCaps(false);
        home.setTextSize(13);
        home.setTextColor(nightTheme ? Color.rgb(232, 180, 92) : Color.rgb(23, 83, 46));
        home.setPadding(dp(16), dp(13), dp(16), dp(13));
        home.setBackground(shape(nightTheme ? Color.argb(36, 232, 180, 92) : Color.argb(31, 107, 59, 30), nightTheme ? Color.argb(95, 232, 180, 92) : Color.argb(76, 31, 107, 59), 28));
        home.setOnClickListener(v -> goHome());
        root.addView(home, matchWrap());
        TextView active = text("FocusLock is protecting your time", 10, nightTheme ? Color.rgb(143, 166, 151) : Color.rgb(91, 107, 95), false);
        active.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams activeLp = matchWrap(); activeLp.topMargin = dp(10);
        root.addView(active, activeLp);

        FrameLayout scene = new FrameLayout(this);
        scene.setBackgroundColor(nightTheme ? Color.rgb(15, 29, 22) : Color.rgb(247, 245, 239));
        lockCardArt = new LockCardArtView(this, chooseTheme());
        lockCardArt.setClickable(false);
        scene.addView(lockCardArt, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scene.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return scene;
    }

    private void startAnimations() {
        if (timerCard != null) {
            ObjectAnimator x = ObjectAnimator.ofFloat(timerCard, "scaleX", 1f, 1.018f);
            ObjectAnimator y = ObjectAnimator.ofFloat(timerCard, "scaleY", 1f, 1.018f);
            x.setDuration(1800); y.setDuration(1800);
            x.setRepeatCount(ObjectAnimator.INFINITE); y.setRepeatCount(ObjectAnimator.INFINITE);
            x.setRepeatMode(ObjectAnimator.REVERSE); y.setRepeatMode(ObjectAnimator.REVERSE);
            x.setInterpolator(new AccelerateDecelerateInterpolator());
            y.setInterpolator(new AccelerateDecelerateInterpolator());
            AnimatorSet breathing = new AnimatorSet();
            breathing.playTogether(x, y);
            breathing.start();
        }
        if (reminderCard != null) {
            reminderCard.setAlpha(0f);
            reminderCard.setTranslationY(dp(16));
            reminderCard.animate().alpha(1f).translationY(0f).setStartDelay(180).setDuration(420).start();
        }
        if (timerText != null) {
            ObjectAnimator pulse = ObjectAnimator.ofFloat(timerText, "alpha", 1f, .86f);
            pulse.setDuration(1400);
            pulse.setRepeatCount(ObjectAnimator.INFINITE);
            pulse.setRepeatMode(ObjectAnimator.REVERSE);
            pulse.setInterpolator(new AccelerateDecelerateInterpolator());
            pulse.start();
        }
        if (lockIcon != null) {
            ObjectAnimator bounceY = ObjectAnimator.ofFloat(lockIcon, "translationY", 0f, dp(-7), 0f);
            ObjectAnimator bounceX = ObjectAnimator.ofFloat(lockIcon, "scaleX", 1f, 1.10f, 1f);
            ObjectAnimator bounceScaleY = ObjectAnimator.ofFloat(lockIcon, "scaleY", 1f, 1.10f, 1f);
            bounceY.setDuration(2500); bounceX.setDuration(2500); bounceScaleY.setDuration(2500);
            bounceY.setRepeatCount(ObjectAnimator.INFINITE); bounceX.setRepeatCount(ObjectAnimator.INFINITE); bounceScaleY.setRepeatCount(ObjectAnimator.INFINITE);
            AnimatorSet bounce = new AnimatorSet();
            bounce.playTogether(bounceY, bounceX, bounceScaleY);
            bounce.setInterpolator(new AccelerateDecelerateInterpolator());
            bounce.start();
        }
        if (timerRing != null) {
            ObjectAnimator ringPulse = ObjectAnimator.ofFloat(timerRing, "scaleX", 1f, 1.035f, 1f);
            ObjectAnimator ringPulseY = ObjectAnimator.ofFloat(timerRing, "scaleY", 1f, 1.035f, 1f);
            ringPulse.setDuration(2800); ringPulseY.setDuration(2800);
            ringPulse.setRepeatCount(ObjectAnimator.INFINITE); ringPulseY.setRepeatCount(ObjectAnimator.INFINITE);
            AnimatorSet ringBreath = new AnimatorSet();
            ringBreath.playTogether(ringPulse, ringPulseY);
            ringBreath.setInterpolator(new AccelerateDecelerateInterpolator());
            ringBreath.start();
        }

    }

    private static final class ProgressRingView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); private float progress=1f;
        ProgressRingView(android.content.Context context){super(context);}
        void setProgress(float value){progress=value;invalidate();}
        @Override protected void onDraw(Canvas canvas) {
            float c=getWidth()/2f,r=Math.min(getWidth(),getHeight())/2f-dp(getContext(),6); RectF oval=new RectF(c-r,c-r,c+r,c+r);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeWidth(dp(getContext(),1)); paint.setColor(Color.argb(52,47,122,74)); canvas.drawCircle(c,c,r,paint);
            paint.setStrokeWidth(dp(getContext(),1.6f));paint.setColor(Color.argb(140,47,122,74));canvas.drawArc(oval,-90f,360f*progress,false,paint);
            paint.setStrokeWidth(dp(getContext(),1));paint.setColor(Color.argb(76,47,122,74));for(int i=0;i<8;i++){double a=Math.PI*2*i/8d;float x1=c+(float)Math.cos(a)*r,y1=c+(float)Math.sin(a)*r,x2=c+(float)Math.cos(a)*(r-dp(getContext(),9)),y2=c+(float)Math.sin(a)*(r-dp(getContext(),9));canvas.drawLine(x1,y1,x2,y2,paint);}
        }
        private static float dp(android.content.Context context,float value){return value*context.getResources().getDisplayMetrics().density;}
    }

    /** The eight supplied lock-card scenes: Dunes, Grove, Horizon, Nightfall, Rainfall, Ridge, Seedling and Tide. */
    private static final class LockCardArtView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); private final android.graphics.Path q=new android.graphics.Path(); private final int theme;
        LockCardArtView(android.content.Context c,int theme){super(c);this.theme=theme;p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setColor(Color.argb(58,47,122,74));}
        void gatherAt(float x,float y) { /* artwork remains still during touches */ }
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

    private int chooseTheme() {
        long session = LockStore.lockedUntil(this, blockedPackage);
        android.content.SharedPreferences p = getSharedPreferences("focuslock_lock_card_themes", MODE_PRIVATE);
        String key = "card:" + blockedPackage;
        if (p.getLong(key + ":session", Long.MIN_VALUE) == session) return p.getInt(key + ":theme", 0);
        int next = (p.getInt("last_theme", -1) + 1) % 8;
        p.edit().putLong(key + ":session", session).putInt(key + ":theme", next).putInt("last_theme", next).apply();
        return next;
    }

    private void startTimer() {
        long remaining = Math.max(0, LockStore.lockedUntil(this, blockedPackage) - System.currentTimeMillis());
        timerDurationMs = Math.max(1L, remaining);
        updateTimer(remaining);
        timer = new CountDownTimer(remaining, 1000) {
            @Override public void onTick(long left) { updateTimer(left); }
            @Override public void onFinish() { goHome(); }
        }.start();
    }

    private void updateTimer(long remaining) {
        if (timerText != null) timerText.setText(format(remaining));
        if (timerRing != null) timerRing.setProgress(Math.max(0f, Math.min(1f, remaining / (float) timerDurationMs)));
    }

    private String appName() {
        try { return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(blockedPackage, 0)).toString(); }
        catch (PackageManager.NameNotFoundException e) { return "this app"; }
    }

    private String format(long ms) {
        long seconds = Math.max(0, (ms + 999) / 1000);
        return String.format(java.util.Locale.US, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private void goHome() {
        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    @Override public void onBackPressed() { goHome(); }
    @Override protected void onDestroy() { visible = false; if (timer != null) timer.cancel(); super.onDestroy(); }
    private TextView text(String value, int size, int color, boolean bold) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD); return v; }
    private GradientDrawable shape(int fill, int stroke, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), stroke); return d; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams weighted() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); p.setMargins(dp(3), 0, dp(3), 0); return p; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
