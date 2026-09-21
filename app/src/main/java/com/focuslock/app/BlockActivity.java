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
        lockCardArt = new LockCardArtView(this, (blockedPackage.hashCode() & 1) == 0);
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

    /** Only the two current supplied lock-card illustrations. */
    private static final class LockCardArtView extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path path = new android.graphics.Path();
        private final boolean roots;

        LockCardArtView(android.content.Context context, boolean roots) {
            super(context);
            this.roots = roots;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
            line.setColor(Color.argb(58, 47, 122, 74));
        }
        void gatherAt(float x, float y) { /* the supplied designs are intentionally still */ }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            if (roots) drawRoots(canvas, w, h); else drawHorizon(canvas, w, h);
        }
        private void drawRoots(Canvas c, float w, float h) {
            line.setColor(Color.argb(36, 47, 122, 74)); line.setStrokeWidth(dp(getContext(), 1.3f));
            float cx=w*.5f;
            c.drawCircle(cx, 0, dp(getContext(),70), line); c.drawCircle(cx,0,dp(getContext(),92),line); c.drawCircle(cx,0,dp(getContext(),112),line);
            path.reset(); path.moveTo(cx,0); path.cubicTo(cx+dp(getContext(),2),h*.10f,cx-dp(getContext(),6),h*.15f,cx+dp(getContext(),1),h*.22f); path.cubicTo(cx-dp(getContext(),4),h*.36f,cx,h*.48f,cx+dp(getContext(),1),h*.57f); c.drawPath(path,line);
            branch(c,cx,h*.12f,w*.26f,h*.27f); branch(c,w*.36f,h*.27f,w*.30f,h*.33f); branch(c,cx,h*.29f,w*.22f,h*.45f); branch(c,w*.36f,h*.45f,w*.30f,h*.51f); branch(c,cx,h*.50f,w*.34f,h*.64f);
            branch(c,cx,h*.18f,w*.75f,h*.32f); branch(c,w*.85f,h*.32f,w*.91f,h*.38f); branch(c,cx,h*.36f,w*.68f,h*.52f); branch(c,w*.78f,h*.52f,w*.84f,h*.58f); branch(c,cx,h*.55f,w*.63f,h*.67f);
            line.setColor(Color.argb(52,47,122,74)); line.setStrokeWidth(dp(getContext(),1f));
            wave(c,w,h*.78f); wave(c,w,h*.85f);
            leaf(c,w*.16f,h*.09f,1); leaf(c,w*.84f,h*.16f,-1);
        }
        private void drawHorizon(Canvas c, float w, float h) {
            line.setColor(Color.argb(51,47,122,74)); line.setStrokeWidth(dp(getContext(),1f));
            path.reset();path.moveTo(-dp(getContext(),6),dp(getContext(),20));path.cubicTo(w*.18f,dp(getContext(),34),w*.33f,dp(getContext(),8),w*.49f,dp(getContext(),22));path.cubicTo(w*.65f,dp(getContext(),36),w*.79f,dp(getContext(),30),w+dp(getContext(),6),dp(getContext(),8));c.drawPath(path,line);
            float sx=w*.733f,sy=h*.123f;line.setColor(Color.argb(61,47,122,74));line.setStrokeWidth(dp(getContext(),1.1f));c.drawCircle(sx,sy,dp(getContext(),34),line);
            for(int i=0;i<8;i++){double a=Math.PI*2*i/8d;float r1=dp(getContext(),48),r2=dp(getContext(),60);c.drawLine(sx+(float)Math.cos(a)*r1,sy+(float)Math.sin(a)*r1,sx+(float)Math.cos(a)*r2,sy+(float)Math.sin(a)*r2,line);}
            line.setColor(Color.argb(41,47,122,74));line.setStrokeWidth(dp(getContext(),1.2f));wave(c,w,h*.66f);wave(c,w,h*.72f);wave(c,w,h*.79f);wave(c,w,h*.86f);bird(c,w*.17f,h*.15f,1);bird(c,w*.31f,h*.095f,.72f);
        }
        private void branch(Canvas c,float x1,float y1,float x2,float y2){path.reset();path.moveTo(x1,y1);path.cubicTo(x1+(x2-x1)*.42f,y1+(y2-y1)*.22f,x1+(x2-x1)*.75f,y1+(y2-y1)*.72f,x2,y2);c.drawPath(path,line);}
        private void wave(Canvas c,float w,float y){path.reset();path.moveTo(-dp(getContext(),14),y);path.cubicTo(w*.15f,y-dp(getContext(),28),w*.29f,y-dp(getContext(),26),w*.42f,y);path.cubicTo(w*.56f,y+dp(getContext(),22),w*.72f,y+dp(getContext(),18),w+dp(getContext(),14),y-dp(getContext(),2));c.drawPath(path,line);}
        private void leaf(Canvas c,float x,float y,int d){float s=dp(getContext(),20);path.reset();path.moveTo(x-d*s,y);path.quadTo(x,y-s*.7f,x+d*s,y-dp(getContext(),2));path.quadTo(x,y+s*.7f,x-d*s,y);c.drawPath(path,line);c.drawLine(x-d*s*.7f,y,x+d*s*.6f,y+dp(getContext(),3),line);}
        private void bird(Canvas c,float x,float y,float scale){float s=dp(getContext(),10)*scale;path.reset();path.moveTo(x-s,y);path.quadTo(x-s*.45f,y-s*.7f,x,y);path.quadTo(x+s*.45f,y-s*.7f,x+s,y);c.drawPath(path,line);}
        private static float dp(android.content.Context c,float v){return v*c.getResources().getDisplayMetrics().density;}
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
