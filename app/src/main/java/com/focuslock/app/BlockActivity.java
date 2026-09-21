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
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.os.SystemClock;
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
    private LeafBreezeView leafBreeze;
    private boolean smoothEntry;
    private boolean nightTheme;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!AccessStore.isAllowed(this) || !RemoteConfigStore.appBlockingEnabled(this)) { finish(); return; }
        blockedPackage = getIntent().getStringExtra("blocked_package");
        if (blockedPackage == null || !LockStore.isLocked(this, blockedPackage)) { finish(); return; }
        smoothEntry = getIntent().getBooleanExtra("smooth_entry", false);
        nightTheme = isNightTheme();
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
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && leafBreeze != null) {
            leafBreeze.gatherAt(event.getX(), event.getY());
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
        leafBreeze = new LeafBreezeView(this, nightTheme);
        leafBreeze.setClickable(false);
        scene.addView(leafBreeze, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scene.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scene.post(() -> {
            int[] card = new int[2];
            int[] overlay = new int[2];
            timerCard.getLocationInWindow(card);
            leafBreeze.getLocationInWindow(overlay);
            leafBreeze.setTimerBounds(card[0] - overlay[0], card[1] - overlay[1], timerCard.getWidth(), timerCard.getHeight());
        });
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

    private LinearLayout breathStep(String label, String value) {
        LinearLayout step = new LinearLayout(this);
        step.setOrientation(LinearLayout.VERTICAL);
        step.setGravity(Gravity.CENTER);
        step.setPadding(dp(5), dp(9), dp(5), dp(9));
        step.setBackground(shape(Color.rgb(249, 250, 251), Color.rgb(243, 244, 246), 14));
        step.addView(text(label, 9, FAINT, false));
        step.addView(text(value, 14, INK, true));
        return step;
    }

    private static final class GradientTimerText extends TextView {
        private final Paint shaderPaint = new Paint();
        GradientTimerText(android.content.Context context) { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            shaderPaint.set(getPaint());
            shaderPaint.setShader(new LinearGradient(0, 0, getWidth(), 0,
                    Color.WHITE, Color.rgb(188, 235, 200), Shader.TileMode.CLAMP));
            getPaint().setShader(shaderPaint.getShader());
            super.onDraw(canvas);
            getPaint().setShader(null);
        }
    }

    private static final class ProgressRingView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress = 1f;
        ProgressRingView(android.content.Context context) { super(context); }
        void setProgress(float value) { progress = value; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            float centerX = getWidth() / 2f, centerY = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) / 2f - dp(getContext(), 12);
            RectF oval = new RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(getContext(), 13));
            paint.setColor(Color.argb(32, 52, 116, 76));
            canvas.drawArc(oval, -90f, 360f, false, paint);
            paint.setStrokeWidth(dp(getContext(), 22));
            paint.setColor(Color.argb(30, 79, 172, 104));
            canvas.drawArc(oval, -90f, 360f * progress, false, paint);
            paint.setStrokeWidth(dp(getContext(), 7));
            paint.setShader(new LinearGradient(0, 0, getWidth(), getHeight(),
                    new int[] { Color.rgb(76, 157, 98), Color.rgb(188, 235, 200), Color.rgb(52, 116, 76) },
                    null, Shader.TileMode.CLAMP));
            canvas.drawArc(oval, -90f, 360f * progress, false, paint);
            paint.setShader(null);
        }
        private static float dp(android.content.Context context, float value) {
            return value * context.getResources().getDisplayMetrics().density;
        }
    }

    /** The two supplied lock-card scenes: Horizon in light mode, Nightfall in dark mode. */
    private static final class LeafBreezeView extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path path = new android.graphics.Path();
        private final boolean night;

        LeafBreezeView(android.content.Context context, boolean night) {
            super(context);
            this.night = night;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
        }

        void setTimerBounds(float x, float y, float w, float h) { invalidate(); }
        void gatherAt(float x, float y) { /* artwork remains calm while the card is touched */ }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float time = SystemClock.uptimeMillis() / 1000f;
            if (night) drawNightfall(canvas, w, h, time);
            else drawHorizon(canvas, w, h, time);
            postInvalidateDelayed(40L);
        }

        private void drawHorizon(Canvas canvas, float w, float h, float time) {
            line.setStrokeWidth(dp(getContext(), 1.1f));
            line.setColor(Color.argb(64, 47, 122, 74));
            float drift = (float) Math.sin(time * .25f) * dp(getContext(), 3);
            path.reset();
            path.moveTo(-dp(getContext(), 8), dp(getContext(), 28) + drift);
            path.cubicTo(w*.18f, dp(getContext(), 40), w*.36f, dp(getContext(), 8), w*.50f, dp(getContext(), 26));
            path.cubicTo(w*.68f, dp(getContext(), 42), w*.82f, dp(getContext(), 33), w+dp(getContext(), 8), dp(getContext(), 12));
            canvas.drawPath(path, line);

            float sx=w*.73f, sy=h*.125f, pulse=(float)Math.sin(time*1.1f);
            line.setColor(Color.argb(72, 47, 122, 74));
            canvas.drawCircle(sx, sy, dp(getContext(), 34)+pulse*dp(getContext(), 2), line);
            for(int i=0;i<8;i++){ double a=Math.PI*2*i/8d; float r1=dp(getContext(),48),r2=dp(getContext(),60)+pulse*dp(getContext(),2); canvas.drawLine(sx+(float)Math.cos(a)*r1,sy+(float)Math.sin(a)*r1,sx+(float)Math.cos(a)*r2,sy+(float)Math.sin(a)*r2,line); }

            line.setColor(Color.argb(48, 47, 122, 74));
            line.setStrokeWidth(dp(getContext(), 1.25f));
            float sway=(float)Math.sin(time*.22f)*dp(getContext(),4);
            wave(canvas,w,h*.66f+sway,w*.75f);
            wave(canvas,w,h*.73f-sway,w*.82f);
            wave(canvas,w,h*.80f+sway,w*.89f);
            wave(canvas,w,h*.87f-sway,w*.96f);
            bird(canvas,w*.17f,h*.16f+(float)Math.sin(time*.55f)*dp(getContext(),3),1f);
            bird(canvas,w*.29f,h*.105f+(float)Math.cos(time*.48f)*dp(getContext(),3),.72f);
        }

        private void drawNightfall(Canvas canvas, float w, float h, float time) {
            line.setColor(Color.argb(118, 127, 191, 151));
            line.setStrokeWidth(dp(getContext(), 1f));
            float[] xs={.15f,.82f,.11f,.90f,.18f,.85f,.13f,.88f};
            float[] ys={.09f,.15f,.36f,.42f,.62f,.67f,.86f,.91f};
            for(int i=0;i<xs.length;i++){
                float x=w*xs[i],y=h*ys[i],s=dp(getContext(),3+(i%2));
                float glow=.45f+.55f*(float)((Math.sin(time*.9f+i)+1)/2);
                line.setAlpha((int)(130*glow));
                canvas.drawLine(x-s,y,x+s,y,line); canvas.drawLine(x,y-s,x,y+s,line);
            }
            line.setAlpha(115); line.setStrokeWidth(dp(getContext(),1.3f));
            float mx=w*.78f,my=h*.10f;
            path.reset(); path.moveTo(mx,my-dp(getContext(),20)); path.arcTo(mx-dp(getContext(),24),my-dp(getContext(),24),mx+dp(getContext(),24),my+dp(getContext(),24),-72,285,false); canvas.drawPath(path,line);
            line.setColor(Color.argb(74,127,191,151)); line.setStrokeWidth(dp(getContext(),1.3f));
            path.reset(); path.moveTo(-dp(getContext(),14),h*.79f); path.cubicTo(w*.12f,h*.70f,w*.22f,h*.67f,w*.33f,h*.72f); path.cubicTo(w*.47f,h*.78f,w*.57f,h*.67f,w*.70f,h*.71f); path.cubicTo(w*.81f,h*.75f,w*.90f,h*.76f,w+dp(getContext(),14),h*.73f); canvas.drawPath(path,line);
            path.reset(); path.moveTo(-dp(getContext(),14),h*.87f); path.cubicTo(w*.17f,h*.79f,w*.28f,h*.80f,w*.42f,h*.86f); path.cubicTo(w*.58f,h*.91f,w*.72f,h*.80f,w+dp(getContext(),14),h*.86f); canvas.drawPath(path,line);
        }

        private void wave(Canvas c,float w,float y,float controlY) {
            path.reset(); path.moveTo(-dp(getContext(),14),y);
            path.cubicTo(w*.15f,controlY,w*.29f,y-dp(getContext(),18),w*.42f,y);
            path.cubicTo(w*.55f,y+dp(getContext(),20),w*.70f,y-dp(getContext(),14),w+dp(getContext(),14),y+dp(getContext(),12));
            c.drawPath(path,line);
        }
        private void bird(Canvas c,float x,float y,float scale){
            float s=dp(getContext(),10)*scale; path.reset(); path.moveTo(x-s,y); path.quadTo(x-s*.45f,y-s*.7f,x,y); path.quadTo(x+s*.45f,y-s*.7f,x+s,y); c.drawPath(path,line);
        }
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

    private boolean isNightTheme() {
        int mode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
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
    private GradientDrawable circleTimerShape() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] { Color.rgb(9, 13, 18), INK, Color.rgb(17, 31, 25) });
        d.setShape(GradientDrawable.OVAL);
        d.setStroke(dp(1), Color.rgb(65, 139, 86));
        return d;
    }
    private GradientDrawable shape(int fill, int stroke, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), stroke); return d; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams weighted() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); p.setMargins(dp(3), 0, dp(3), 0); return p; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
