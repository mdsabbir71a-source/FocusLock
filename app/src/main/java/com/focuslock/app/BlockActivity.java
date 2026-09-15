package com.focuslock.app;

import android.app.Activity;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
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
    private CountDownTimer timer;
    private LinearLayout timerCard;
    private LinearLayout reminderCard;
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
    }

    @Override protected void onPause() {
        visible = false;
        super.onPause();
    }

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(22), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(248, 251, 246));
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

        timerCard = new LinearLayout(this);
        timerCard.setOrientation(LinearLayout.VERTICAL);
        timerCard.setGravity(Gravity.CENTER);
        timerCard.setPadding(dp(24), dp(18), dp(24), dp(18));
        timerCard.setBackground(shape(INK, INK, 30));
        TextView timerLabel = text("UNLOCKS IN", 10, Color.rgb(206, 235, 214), true);
        timerLabel.setLetterSpacing(.12f);
        timerLabel.setGravity(Gravity.CENTER);
        timerCard.addView(timerLabel, matchWrap());
        timerText = text("00:00", 52, Color.WHITE, true);
        timerText.setGravity(Gravity.CENTER);
        timerText.setIncludeFontPadding(false);
        LinearLayout.LayoutParams timerValueLp = matchWrap(); timerValueLp.topMargin = dp(8);
        timerCard.addView(timerText, timerValueLp);
        LinearLayout.LayoutParams timerCardLp = matchWrap(); timerCardLp.topMargin = dp(24);
        root.addView(timerCard, timerCardLp);

        reminderCard = new LinearLayout(this);
        reminderCard.setOrientation(LinearLayout.VERTICAL);
        reminderCard.setPadding(dp(20), dp(18), dp(20), dp(18));
        reminderCard.setBackground(shape(Color.WHITE, Color.rgb(220, 233, 220), 24));
        reminderCard.addView(text("REMINDER", 10, VIOLET, true));
        String[] reminders = RemoteConfigStore.reminders(this, REMINDERS);
        TextView quote = text(reminders[LockStore.nextReminderIndex(this, reminders.length)], 16, INK, true);
        quote.setGravity(Gravity.CENTER);
        quote.setLineSpacing(0, 1.24f);
        LinearLayout.LayoutParams quoteLp = matchWrap(); quoteLp.topMargin = dp(10);
        reminderCard.addView(quote, quoteLp);
        LinearLayout.LayoutParams reminderLp = matchWrap(); reminderLp.topMargin = dp(22);
        root.addView(reminderCard, reminderLp);

        TextView boundary = text("Protection is active", 11, FAINT, false);
        boundary.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams boundaryLp = matchWrap(); boundaryLp.topMargin = dp(18);
        root.addView(boundary, boundaryLp);

        Space lower = new Space(this);
        root.addView(lower, new LinearLayout.LayoutParams(1, 0, 1f));
        Button home = new Button(this);
        home.setText("Return to home");
        home.setAllCaps(false);
        home.setTextSize(13);
        home.setTextColor(INK);
        home.setPadding(dp(16), dp(13), dp(16), dp(13));
        home.setBackground(shape(Color.WHITE, BORDER, 26));
        home.setOnClickListener(v -> goHome());
        root.addView(home, matchWrap());
        TextView active = text("FocusLock is protecting your time", 10, FAINT, false);
        active.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams activeLp = matchWrap(); activeLp.topMargin = dp(10);
        root.addView(active, activeLp);
        return root;
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

    private void startTimer() {
        long remaining = Math.max(0, LockStore.lockedUntil(this, blockedPackage) - System.currentTimeMillis());
        timer = new CountDownTimer(remaining, 1000) {
            @Override public void onTick(long left) { timerText.setText(format(left) + " left"); }
            @Override public void onFinish() { goHome(); }
        }.start();
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
