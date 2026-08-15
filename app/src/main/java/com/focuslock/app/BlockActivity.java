package com.focuslock.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

public class BlockActivity extends Activity {
    private static final int INK = Color.rgb(17, 24, 39);
    private static final int MUTED = Color.rgb(107, 114, 128);
    private static final int FAINT = Color.rgb(156, 163, 175);
    private static final int VIOLET = Color.rgb(139, 92, 246);
    private static final int BORDER = Color.rgb(237, 233, 254);
    private String blockedPackage;
    private TextView timerText;
    private CountDownTimer timer;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        blockedPackage = getIntent().getStringExtra("blocked_package");
        if (blockedPackage == null || !LockStore.isLocked(this, blockedPackage)) { finish(); return; }
        setContentView(buildUi());
        startTimer();
    }

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(22), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(254, 254, 252));

        TextView top = text("◑  Taking a break from " + appName(), 11, MUTED, false);
        top.setGravity(Gravity.CENTER);
        root.addView(top, matchWrap());
        Space upper = new Space(this);
        root.addView(upper, new LinearLayout.LayoutParams(1, 0, .8f));

        LinearLayout logoCard = new LinearLayout(this);
        logoCard.setGravity(Gravity.CENTER);
        logoCard.setBackground(shape(Color.WHITE, BORDER, 28));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.focuslock_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logoCard.addView(logo, new LinearLayout.LayoutParams(dp(58), dp(58)));
        root.addView(logoCard, new LinearLayout.LayoutParams(dp(92), dp(92)));

        TextView title = text("Taking a pause", 30, INK, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = matchWrap(); titleLp.topMargin = dp(24);
        root.addView(title, titleLp);

        timerText = text("Breathe  •  00:00 left", 13, Color.WHITE, true);
        timerText.setGravity(Gravity.CENTER);
        timerText.setPadding(dp(18), dp(10), dp(18), dp(10));
        timerText.setBackground(shape(INK, INK, 24));
        LinearLayout.LayoutParams timerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timerLp.topMargin = dp(14);
        root.addView(timerText, timerLp);

        LinearLayout reminder = new LinearLayout(this);
        reminder.setOrientation(LinearLayout.VERTICAL);
        reminder.setPadding(dp(18), dp(18), dp(18), dp(18));
        reminder.setBackground(shape(Color.WHITE, BORDER, 24));
        reminder.addView(text("✦  A GENTLE REMINDER", 10, VIOLET, true));
        TextView quote = text("This boundary is doing what you asked. Take a breath — the app will return when the pause is complete.", 14, INK, true);
        quote.setLineSpacing(0, 1.22f);
        LinearLayout.LayoutParams quoteLp = matchWrap(); quoteLp.topMargin = dp(12);
        reminder.addView(quote, quoteLp);
        LinearLayout breath = new LinearLayout(this);
        breath.setOrientation(LinearLayout.HORIZONTAL);
        breath.setGravity(Gravity.CENTER);
        breath.addView(breathStep("INHALE", "4s"), weighted());
        breath.addView(breathStep("HOLD", "4s"), weighted());
        breath.addView(breathStep("EXHALE", "6s"), weighted());
        LinearLayout.LayoutParams breathLp = matchWrap(); breathLp.topMargin = dp(18);
        reminder.addView(breath, breathLp);
        LinearLayout.LayoutParams reminderLp = matchWrap(); reminderLp.topMargin = dp(30);
        root.addView(reminder, reminderLp);

        TextView boundary = text("kind boundary  •  no override needed", 10, FAINT, false);
        boundary.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams boundaryLp = matchWrap(); boundaryLp.topMargin = dp(18);
        root.addView(boundary, boundaryLp);

        Space lower = new Space(this);
        root.addView(lower, new LinearLayout.LayoutParams(1, 0, 1f));
        Button home = new Button(this);
        home.setText("Return to Home");
        home.setAllCaps(false);
        home.setTextSize(13);
        home.setTextColor(INK);
        home.setPadding(dp(16), dp(13), dp(16), dp(13));
        home.setBackground(shape(Color.WHITE, BORDER, 26));
        home.setOnClickListener(v -> goHome());
        root.addView(home, matchWrap());
        TextView active = text("🌿  Boundary active • we’ll let you know when it’s time", 10, FAINT, false);
        active.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams activeLp = matchWrap(); activeLp.topMargin = dp(10);
        root.addView(active, activeLp);
        return root;
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
            @Override public void onTick(long left) { timerText.setText("Breathe  •  " + format(left) + " left"); }
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
    @Override protected void onDestroy() { if (timer != null) timer.cancel(); super.onDestroy(); }
    private TextView text(String value, int size, int color, boolean bold) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD); return v; }
    private GradientDrawable shape(int fill, int stroke, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), stroke); return d; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams weighted() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); p.setMargins(dp(3), 0, dp(3), 0); return p; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
