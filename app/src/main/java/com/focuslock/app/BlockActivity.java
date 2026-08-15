package com.focuslock.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class BlockActivity extends Activity {
    private TextView timer;
    private CountDownTimer countDown;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER); root.setPadding(dp(30), dp(30), dp(30), dp(30));
        root.setBackgroundColor(Color.rgb(30, 24, 42));
        TextView icon = label("🔒", 58); root.addView(icon);
        TextView title = label("This app is locked", 28); title.setTypeface(null, android.graphics.Typeface.BOLD); root.addView(title);
        TextView copy = label("You made a commitment to protect your attention.", 17); copy.setGravity(Gravity.CENTER); root.addView(copy);
        timer = label("", 22); timer.setPadding(0, dp(22), 0, dp(22)); root.addView(timer);
        Button home = new Button(this); home.setText("Go to Home screen"); home.setAllCaps(false);
        home.setOnClickListener(v -> goHome()); root.addView(home);
        setContentView(root); startTimer();
    }

    private void startTimer() {
        long remaining = Math.max(0, LockStore.end(this) - System.currentTimeMillis());
        countDown = new CountDownTimer(remaining, 1000) {
            public void onTick(long ms) { timer.setText(format(ms)); }
            public void onFinish() { finish(); }
        }.start();
    }
    private void goHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home); finish();
    }
    @Override public void onBackPressed() { goHome(); }
    @Override protected void onDestroy() { if (countDown != null) countDown.cancel(); super.onDestroy(); }
    private String format(long ms) { long s = ms / 1000; return String.format("%02d:%02d:%02d remaining", s / 3600, (s % 3600) / 60, s % 60); }
    private TextView label(String value, int size) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(Color.WHITE); t.setPadding(0, dp(8), 0, dp(8)); return t; }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
