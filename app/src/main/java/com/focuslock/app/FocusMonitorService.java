package com.focuslock.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

public class FocusMonitorService extends Service {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private View blocker;
    private TextView timer;

    @Override public void onCreate() {
        super.onCreate();
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        createChannel();
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, "focus_lock")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("FocusLock commitment active")
                .setContentText("Monitoring the selected apps")
                .setContentIntent(pending).setOngoing(true).build();
        startForeground(7, notification);
        handler.post(check);
    }

    private final Runnable check = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            if (now >= LockStore.end(FocusMonitorService.this)) {
                hideBlocker(); stopSelf(); return;
            }
            String foreground = foregroundPackage();
            if (foreground != null && LockStore.isLocked(FocusMonitorService.this, foreground)) showBlocker();
            else hideBlocker();
            if (timer != null) timer.setText(format(LockStore.end(FocusMonitorService.this) - now));
            handler.postDelayed(this, 500);
        }
    };

    private String foregroundPackage() {
        UsageStatsManager manager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        List<UsageStats> stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 10_000, now);
        UsageStats latest = null;
        for (UsageStats item : stats) if (latest == null || item.getLastTimeUsed() > latest.getLastTimeUsed()) latest = item;
        return latest == null ? null : latest.getPackageName();
    }

    private void showBlocker() {
        if (blocker != null || !Settings.canDrawOverlays(this)) return;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER); root.setPadding(60, 60, 60, 60);
        root.setBackgroundColor(Color.rgb(30, 24, 42));
        TextView title = label("App locked", 30); title.setTypeface(null, android.graphics.Typeface.BOLD); root.addView(title);
        TextView copy = label("You made a commitment to protect your attention.", 18); copy.setGravity(Gravity.CENTER); root.addView(copy);
        timer = label("", 22); timer.setPadding(0, 35, 0, 35); root.addView(timer);
        Button home = new Button(this); home.setText("Go to Home screen"); home.setAllCaps(false);
        home.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); hideBlocker(); });
        root.addView(home);
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -1, type, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.OPAQUE);
        windows.addView(root, params); blocker = root;
    }

    private void hideBlocker() {
        if (blocker != null) { windows.removeView(blocker); blocker = null; timer = null; }
    }
    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel("focus_lock", "FocusLock monitoring", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }
    private TextView label(String text, int size) { TextView t = new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(Color.WHITE); t.setPadding(0, 12, 0, 12); return t; }
    private String format(long ms) { long s = Math.max(0, ms / 1000); return String.format("%02d:%02d:%02d remaining", s / 3600, (s % 3600) / 60, s % 60); }
    @Override public void onDestroy() { handler.removeCallbacks(check); hideBlocker(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
