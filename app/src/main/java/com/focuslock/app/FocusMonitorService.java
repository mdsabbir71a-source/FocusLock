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
import android.os.SystemClock;

public class FocusMonitorService extends Service {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastKick;
    private long lastEventQuery;
    private long lastTick;
    private long lastHeartbeat;
    private long lastDeviceSync;
    private String currentPackage;
    private String ownPackage;

    @Override public void onCreate() {
        super.onCreate();
        if (!AccessStore.isAllowed(this) || !RemoteConfigStore.appBlockingEnabled(this)) { stopSelf(); return; }
        // Start from the moment the service is created. Looking back over a day
        // can mistake a stale Chrome (or another selected app) event for the
        // app that is actually on screen and immediately kick the user out of
        // the save screen. The next foreground event will identify the real app.
        ownPackage = getPackageName();
        currentPackage = ownPackage;
        lastEventQuery = System.currentTimeMillis();
        lastTick = SystemClock.elapsedRealtime();
        createChannel();
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, "focus_lock")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("FocusLock commitment active")
                .setContentText("Only your selected apps will be blocked")
                .setContentIntent(pending).setOngoing(true).build();
        startForeground(7, notification);
        MonitorHealthStore.heartbeat(this);
        lastDeviceSync = System.currentTimeMillis();
        SupabaseApi.syncDeviceState(this);
        ProtectionRestarter.schedule(this, 15 * 60_000L);
        handler.post(check);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!ProtectionRestarter.shouldMonitor(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        MonitorHealthStore.heartbeat(this);
        return START_STICKY;
    }

    private final Runnable check = new Runnable() {
        @Override public void run() {
            if (!AccessStore.isAllowed(FocusMonitorService.this)
                    || !RemoteConfigStore.appBlockingEnabled(FocusMonitorService.this)
                    || !LockStore.isEnabled(FocusMonitorService.this)) { stopSelf(); return; }
            long now = System.currentTimeMillis();
            updateForegroundPackage(now);
            long elapsedNow = SystemClock.elapsedRealtime();
            long elapsed = elapsedNow - lastTick;
            lastTick = elapsedNow;
            if (now - lastHeartbeat >= 5_000L) {
                lastHeartbeat = now;
                MonitorHealthStore.heartbeat(FocusMonitorService.this);
            }
            if (now - lastDeviceSync >= 15 * 60_000L) {
                lastDeviceSync = now;
                SupabaseApi.syncDeviceState(FocusMonitorService.this);
            }
            if (MainActivity.isVisible()
                    && LockStore.isLocked(FocusMonitorService.this, ownPackage)
                    && !BlockActivity.isVisible()
                    && now - lastKick > 1200) {
                // Usage events can report the just-closed app for one cycle.
                // Prioritize FocusLock's visible screen so it never flashes Home.
                lastKick = now;
                kickOut(ownPackage);
            } else if (currentPackage != null && LockStore.isSelected(FocusMonitorService.this, currentPackage)) {
                boolean newlyLocked = LockStore.addUsage(FocusMonitorService.this, currentPackage, elapsed);
                if ((newlyLocked || LockStore.isLocked(FocusMonitorService.this, currentPackage)) && now - lastKick > 1200) {
                    lastKick = now;
                    kickOut(currentPackage);
                }
            } else if (ownPackage.equals(currentPackage)
                    && LockStore.isLocked(FocusMonitorService.this, ownPackage)
                    && !BlockActivity.isVisible()
                    && now - lastKick > 1200) {
                // Optional self-lock: FocusLock stays unavailable only while one
                // of the selected apps is still in its active pause period.
                lastKick = now;
                kickOut(ownPackage);
            }
            handler.postDelayed(this, 350);
        }
    };

    private void updateForegroundPackage(long now) {
        UsageStatsManager manager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        UsageEvents events = manager.queryEvents(Math.min(lastEventQuery, now), now);
        lastEventQuery = now;
        if (events == null) return;
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            int type = event.getEventType();
            boolean resumed = type == UsageEvents.Event.MOVE_TO_FOREGROUND || (Build.VERSION.SDK_INT >= 29 && type == UsageEvents.Event.ACTIVITY_RESUMED);
            boolean paused = type == UsageEvents.Event.MOVE_TO_BACKGROUND || (Build.VERSION.SDK_INT >= 29 && type == UsageEvents.Event.ACTIVITY_PAUSED);
            if (resumed) currentPackage = event.getPackageName();
            else if (paused && event.getPackageName().equals(currentPackage)) currentPackage = null;
        }
    }

    private void kickOut(String blockedPackage) {
        Intent block = new Intent(this, BlockActivity.class)
                .putExtra("blocked_package", blockedPackage)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (ownPackage.equals(blockedPackage)) {
            // FocusLock is already on screen: replace it directly with the
            // existing pause screen instead of flashing Home first.
            startActivity(block.putExtra("smooth_entry", true));
            return;
        }
        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        handler.postDelayed(() -> startActivity(block), 120);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel("focus_lock", "FocusLock monitoring", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        if (ProtectionRestarter.shouldMonitor(this)) ProtectionRestarter.schedule(this, 5_000L);
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(check);
        MonitorHealthStore.clear(this);
        if (ProtectionRestarter.shouldMonitor(this)) ProtectionRestarter.schedule(this, 5_000L);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
