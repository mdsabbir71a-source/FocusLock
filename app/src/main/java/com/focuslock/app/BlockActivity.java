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

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!AccessStore.isAllowed(this) || !RemoteConfigStore.appBlockingEnabled(this)) { finish(); return; }
        blockedPackage = getIntent().getStringExtra("blocked_package");
        if (blockedPackage == null || !LockStore.isLocked(this, blockedPackage)) { finish(); return; }
        smoothEntry = getIntent().getBooleanExtra("smooth_entry", false);
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
        BlockOverlay.hide();
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

        TextView top = text("FOCUSLOCK", 11, VIOLET, true);
        top.setLetterSpacing(.14f);
        top.setGravity(Gravity.CENTER);
        root.addView(top, matchWrap());

        Space upper = new Space(this);
        root.addView(upper, new LinearLayout.LayoutParams(1, 0, .9f));

        TextView status = text(appName() + " is paused", 14, MUTED, false);
        status.setGravity(Gravity.CENTER);
        root.addView(status, matchWrap());

        TextView title = text("Take a quiet moment", 28, INK, true);
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
        timerCard.setBackground(circleTimerShape());
        TextView timerLabel = text("UNLOCKS IN", 10, Color.rgb(184, 231, 196), true);
        timerLabel.setLetterSpacing(.12f);
        timerLabel.setGravity(Gravity.CENTER);
        timerCard.addView(timerLabel, matchWrap());
        timerText = new GradientTimerText(this);
        timerText.setText("00:00");
        timerText.setTextSize(42);
        timerText.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
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
        reminderCard.setBackground(shape(Color.WHITE, Color.rgb(220, 233, 220), 24));
        String[] reminders = RemoteConfigStore.reminders(this, REMINDERS);
        TextView quote = text(reminders[LockStore.nextReminderIndex(this, reminders.length)], 16, INK, true);
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
        home.setTextColor(VIOLET);
        home.setPadding(dp(16), dp(13), dp(16), dp(13));
        home.setBackground(shape(Color.rgb(238, 247, 239), Color.rgb(194, 222, 199), 26));
        home.setOnClickListener(v -> goHome());
        root.addView(home, matchWrap());
        TextView active = text("FocusLock is protecting your time", 10, FAINT, false);
        active.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams activeLp = matchWrap(); activeLp.topMargin = dp(10);
        root.addView(active, activeLp);

        FrameLayout scene = new FrameLayout(this);
        scene.setBackgroundColor(Color.rgb(246, 250, 246));
        leafBreeze = new LeafBreezeView(this);
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

    private static final class LeafBreezeView extends View {
        private static final int COUNT = 5;
        private static final long LIFETIME_MS = 11000L;
        private static final long STAGGER_MS = 2200L;
        private static final String[] NATURE = { "🌱", "🌿", "☘️", "🍀", "🍁", "🍂", "🍃", "🌾", "🥬", "🌵", "🌳", "🌲", "🌴", "🌸", "🌺", "🌷", "🌹", "🌻", "🌼", "💐", "🥀", "🍄", "🌰", "🌞", "🌙", "⭐", "🫧", "💧", "✨", "💨" };
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] x = new float[COUNT];
        private final float[] y = new float[COUNT];
        private final long[] bornAt = new long[COUNT];
        private float cardW, cardH;
        private float gatherX, gatherY;
        private float touchEmojiX, touchEmojiY;
        private long gatherUntil;
        private long rippleStarted;
        private long touchEmojiUntil;
        private long lastFrame;

        LeafBreezeView(android.content.Context context) {
            super(context);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            ripplePaint.setStyle(Paint.Style.STROKE);
            ripplePaint.setStrokeWidth(dp(context, 2));
        }

        void setTimerBounds(float x, float y, float w, float h) {
            cardW = w; cardH = h;
            long now = SystemClock.uptimeMillis();
            for (int i = 0; i < COUNT; i++) {
                bornAt[i] = now - i * STAGGER_MS;
                float[] target = breezeTarget(i, lifeProgress(i, now));
                this.x[i] = target[0];
                this.y[i] = target[1];
            }
            invalidate();
        }

        void gatherAt(float x, float y) {
            gatherX = x; gatherY = y;
            long now = SystemClock.uptimeMillis();
            gatherUntil = now + 700L;
            rippleStarted = now;
            touchEmojiX = x; touchEmojiY = y;
            touchEmojiUntil = now + 900L;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (cardW == 0f || cardH == 0f) return;
            long now = SystemClock.uptimeMillis();
            float dt = lastFrame == 0 ? .016f : Math.min(.05f, (now - lastFrame) / 1000f);
            lastFrame = now;
            boolean gathering = now < gatherUntil;
            for (int i = 0; i < COUNT; i++) {
                if (now - bornAt[i] >= LIFETIME_MS) {
                    bornAt[i] = now;
                    float[] start = breezeTarget(i, 0f);
                    x[i] = start[0]; y[i] = start[1];
                }
                float life = lifeProgress(i, now);
                float targetX;
                float targetY;
                if (gathering) {
                    float spreadX = (i % 3 - 1f) * dp(getContext(), 15);
                    float spreadY = (i / 3 - .5f) * dp(getContext(), 15);
                    targetX = gatherX + spreadX;
                    targetY = gatherY + spreadY;
                } else {
                    float[] target = breezeTarget(i, life);
                    targetX = target[0];
                    targetY = target[1];
                }
                float pull = gathering ? .14f : .060f;
                x[i] += (targetX - x[i]) * pull;
                y[i] += (targetY - y[i]) * pull;
                float fade = Math.min(1f, Math.min(life / .18f, (1f - life) / .18f));
                paint.setAlpha((int) (fade * (125 + (i % 3) * 30)));
                paint.setTextSize(dp(getContext(), 20 + (i % 4) * 3));
                int emojiIndex = Math.floorMod((int) (bornAt[i] / STAGGER_MS) + i * 5, NATURE.length);
                String emoji = i == 0 ? "🫧" : NATURE[emojiIndex];
                canvas.drawText(emoji, x[i], y[i], paint);
            }
            long rippleAge = now - rippleStarted;
            if (rippleAge >= 0 && rippleAge < 800L) {
                float fraction = rippleAge / 800f;
                ripplePaint.setColor(Color.rgb(83, 156, 103));
                ripplePaint.setAlpha((int) ((1f - fraction) * 105));
                canvas.drawCircle(gatherX, gatherY, dp(getContext(), 18 + 120 * fraction), ripplePaint);
                ripplePaint.setAlpha((int) ((1f - fraction) * 55));
                canvas.drawCircle(gatherX, gatherY, dp(getContext(), 6 + 76 * fraction), ripplePaint);
            }
            if (now < touchEmojiUntil) {
                float fraction = 1f - (touchEmojiUntil - now) / 900f;
                paint.setAlpha((int) ((1f - fraction) * 255));
                paint.setTextSize(dp(getContext(), 25 + 7 * fraction));
                int touchIndex = Math.floorMod((int) (rippleStarted / STAGGER_MS), NATURE.length);
                canvas.drawText(NATURE[touchIndex], touchEmojiX - dp(getContext(), 12), touchEmojiY + dp(getContext(), 8) - dp(getContext(), 14 * fraction), paint);
            }
            postInvalidateDelayed(16);
        }

        private float lifeProgress(int i, long now) {
            return Math.max(0f, Math.min(1f, (now - bornAt[i]) / (float) LIFETIME_MS));
        }

        private float[] breezeTarget(int i, float life) {
            float w = getWidth(), h = getHeight();
            float wave = (float) Math.sin(life * Math.PI * 2f + i * 1.37f) * dp(getContext(), 18);
            switch (i % 5) {
                case 0: return new float[] { -dp(getContext(), 30) + (w + dp(getContext(), 60)) * life, h * .20f + wave };
                case 1: return new float[] { w + dp(getContext(), 30) - (w + dp(getContext(), 60)) * life, h * .35f - wave };
                case 2: return new float[] { -dp(getContext(), 32) + (w + dp(getContext(), 52)) * life, h * .78f - h * .58f * life + wave };
                case 3: return new float[] { w + dp(getContext(), 32) - (w + dp(getContext(), 52)) * life, h * .72f - h * .55f * life - wave };
                default: return new float[] { w * .50f + (life - .5f) * w * .65f, -dp(getContext(), 28) + (h + dp(getContext(), 56)) * life + wave };
            }
        }

        private static float dp(android.content.Context context, float value) {
            return value * context.getResources().getDisplayMetrics().density;
        }
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
