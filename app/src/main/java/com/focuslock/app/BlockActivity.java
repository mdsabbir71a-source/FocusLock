package com.focuslock.app;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class BlockActivity extends Activity {
    private TextView timer;
    private CountDownTimer countDown;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String blockedPackage = getIntent().getStringExtra("blocked_package");
        if (blockedPackage == null || !LockStore.isLocked(this, blockedPackage)) { finish(); return; }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER); root.setPadding(dp(30), dp(30), dp(30), dp(30));
        root.setBackgroundColor(Color.rgb(14, 11, 22));
        ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.focuslock_logo); root.addView(logo, new LinearLayout.LayoutParams(dp(132), dp(132)));
        TextView eyebrow = label("FOCUS MODE ACTIVE", 13); eyebrow.setTextColor(Color.rgb(255, 126, 101)); eyebrow.setLetterSpacing(.14f); root.addView(eyebrow);
        TextView title = label("Not now. Stay focused.", 29); title.setTypeface(null, android.graphics.Typeface.BOLD); root.addView(title);
        String appName = blockedPackage;
        try { appName = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(blockedPackage, 0)).toString(); } catch (Exception ignored) {}
        root.addView(label(appName + " is locked because you made a commitment to protect your attention.", 17));
        timer = label("", 22); timer.setPadding(0, dp(22), 0, dp(22)); root.addView(timer);
        Button home = new Button(this); home.setText("Return to Home"); home.setAllCaps(false); home.setTextSize(17); home.setTextColor(Color.WHITE);
        home.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(124, 82, 240))); home.setOnClickListener(v -> goHome()); root.addView(home);
        setContentView(root); startTimer();
    }

    private void startTimer() {
        String pkg = getIntent().getStringExtra("blocked_package");
        countDown = new CountDownTimer(Math.max(0, LockStore.lockedUntil(this, pkg) - System.currentTimeMillis()), 1000) {
            public void onTick(long ms) { timer.setText(format(ms)); }
            public void onFinish() { finish(); }
        }.start();
    }
    private void goHome() { startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); finish(); }
    @Override public void onBackPressed() { goHome(); }
    @Override protected void onDestroy() { if (countDown != null) countDown.cancel(); super.onDestroy(); }
    private String format(long ms) { long s = Math.max(0, ms / 1000); return String.format("%02d:%02d:%02d remaining", s / 3600, (s % 3600) / 60, s % 60); }
    private TextView label(String value, int size) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(Color.WHITE); t.setPadding(0, dp(8), 0, dp(8)); t.setGravity(Gravity.CENTER); return t; }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
