package com.focuslock.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class FocusMonitorService extends Service {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastKick;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, "focus_lock")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("FocusLock commitment active")
                .setContentText("Only your selected apps will be blocked")
                .setContentIntent(pending).setOngoing(true).build();
        startForeground(7, notification);
        handler.post(check);
    }

    private final Runnable check = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            if (now >= LockStore.end(FocusMonitorService.this)) { stopSelf(); return; }
            String foreground = foregroundPackage();
            if (foreground != null && LockStore.isLocked(FocusMonitorService.this, foreground) && now - lastKick > 1200) {
                lastKick = now;
                kickOut(foreground);
            }
            handler.postDelayed(this, 350);
        }
    };

    private String foregroundPackage() {
        UsageStatsManager manager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        UsageEvents events = manager.queryEvents(now - 2500, now);
        UsageEvents.Event event = new UsageEvents.Event();
        String latest = null;
        long latestTime = 0;
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            int type = event.getEventType();
            boolean resumed = type == UsageEvents.Event.MOVE_TO_FOREGROUND || (Build.VERSION.SDK_INT >= 29 && type == UsageEvents.Event.ACTIVITY_RESUMED);
            if (resumed && event.getTimeStamp() >= latestTime) {
                latest = event.getPackageName();
                latestTime = event.getTimeStamp();
            }
        }
        return latest;
    }

    private void kickOut(String blockedPackage) {
        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        handler.postDelayed(() -> startActivity(new Intent(this, BlockActivity.class)
                .putExtra("blocked_package", blockedPackage)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)), 120);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel("focus_lock", "FocusLock monitoring", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    @Override public void onDestroy() { handler.removeCallbacks(check); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
