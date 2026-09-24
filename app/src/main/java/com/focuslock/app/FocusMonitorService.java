package com.focuslock.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import java.util.List;

/**
 * Foreground monitor for the apps explicitly chosen by the user.
 * Every monitor pass is isolated so a device-specific UsageStats failure can never
 * silently stop protection.
 */
public class FocusMonitorService extends Service {
    // Fast enough to catch rapid app switches while still leaving almost all
    // CPU time to the foreground app. This is the v1.0.88 reliability pass.
    private static final long LOOP_MS = 200L;
    private static final long EVENT_OVERLAP_MS = 1_500L;
    private static final long FALLBACK_QUERY_MS = 1_250L;
    private static final long FOREGROUND_STALE_MS = 3_000L;
    // A slow device can take more than a second to bring the full lock activity
    // to the front. Give it time before showing the emergency overlay so users
    // never see two lock surfaces flash in sequence.
    private static final long OVERLAY_FALLBACK_DELAY_MS = 3_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastKick;
    private long lastEventQuery;
    private long lastTick;
    private long lastHeartbeat;
    private long lastDeviceSync;
    private long lastForegroundEvent;
    private long lastFallbackQuery;
    private String currentPackage;
    private String ownPackage;

    @Override public void onCreate() {
        super.onCreate();
        if (!ProtectionRestarter.shouldMonitor(this)) {
            GardenLockOverlay.hide();
            stopSelf();
            return;
        }
        ownPackage = getPackageName();
        currentPackage = ownPackage;
        lastEventQuery = System.currentTimeMillis();
        lastForegroundEvent = lastEventQuery;
        lastTick = SystemClock.elapsedRealtime();
        createChannel();
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder notificationBuilder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "focus_lock")
                : new Notification.Builder(this);
        Notification notification = notificationBuilder
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("FocusLock commitment active")
                .setContentText("Only your selected apps will be blocked")
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
        startForeground(7, notification);
        MonitorHealthStore.heartbeat(this);
        lastDeviceSync = System.currentTimeMillis();
        SupabaseApi.syncDeviceState(this);
        ProtectionRestarter.schedule(this, 5 * 60_000L);
        handler.post(check);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!ProtectionRestarter.shouldMonitor(this)) {
            GardenLockOverlay.hide();
            stopSelf();
            return START_NOT_STICKY;
        }
        MonitorHealthStore.heartbeat(this);
        return START_STICKY;
    }

    private final Runnable check = new Runnable() {
        @Override public void run() {
            try {
                monitorOnce();
            } catch (Throwable error) {
                // Some OEM UsageStats implementations occasionally throw during
                // background transitions. Keep the service alive and try again.
                DiagnosticStore.record(FocusMonitorService.this, "monitor_pass_recovered",
                        error.getClass().getSimpleName());
                MonitorHealthStore.heartbeat(FocusMonitorService.this);
            } finally {
                handler.postDelayed(this, LOOP_MS);
            }
        }
    };

    private void monitorOnce() {
        if (!ProtectionRestarter.shouldMonitor(this)) {
            GardenLockOverlay.hide();
            stopSelf();
            return;
        }
        long now = System.currentTimeMillis();
        updateForegroundPackage(now);

        long elapsedNow = SystemClock.elapsedRealtime();
        long elapsed = Math.max(0L, Math.min(1_500L, elapsedNow - lastTick));
        lastTick = elapsedNow;

        if (now - lastHeartbeat >= 5_000L) {
            lastHeartbeat = now;
            MonitorHealthStore.heartbeat(this);
        }
        if (now - lastDeviceSync >= 15 * 60_000L) {
            lastDeviceSync = now;
            SupabaseApi.syncDeviceState(this);
        }

        // Compatibility Mode supplies a direct, user-approved foreground signal.
        // Do not count in both monitors or a limit would expire twice as fast.
        if (CompatibilityAccess.isEnabled(this)) {
            if (GardenLockOverlay.isShowing()
                    && (currentPackage == null || !LockStore.isLocked(this, currentPackage))) {
                GardenLockOverlay.hide();
            }
            return;
        }

        if (MainActivity.isVisible()
                && LockStore.isLocked(this, ownPackage)
                && !BlockActivity.isVisible()
                && now - lastKick > 1_200L) {
            lastKick = now;
            kickOut(ownPackage);
            return;
        }

        if (currentPackage != null && LockStore.isSelected(this, currentPackage)) {
            boolean newlyLocked = LockStore.addUsage(this, currentPackage, elapsed);
            if ((newlyLocked || LockStore.isLocked(this, currentPackage)) && now - lastKick > 1_200L) {
                lastKick = now;
                kickOut(currentPackage);
            }
        } else if (ownPackage.equals(currentPackage)
                && LockStore.isLocked(this, ownPackage)
                && !BlockActivity.isVisible()
                && now - lastKick > 1_200L) {
            lastKick = now;
            kickOut(ownPackage);
        } else if (GardenLockOverlay.isShowing()
                && (currentPackage == null || !LockStore.isLocked(this, currentPackage))) {
            GardenLockOverlay.hide();
        }
    }

    private void updateForegroundPackage(long now) {
        UsageStatsManager manager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) return;

        long from = Math.max(0L, lastEventQuery - EVENT_OVERLAP_MS);
        UsageEvents events = manager.queryEvents(from, now);
        lastEventQuery = now;
        boolean sawForeground = false;
        if (events != null) {
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                int type = event.getEventType();
                boolean resumed = type == UsageEvents.Event.MOVE_TO_FOREGROUND
                        || (Build.VERSION.SDK_INT >= 29 && type == UsageEvents.Event.ACTIVITY_RESUMED);
                boolean paused = type == UsageEvents.Event.MOVE_TO_BACKGROUND
                        || (Build.VERSION.SDK_INT >= 29 && type == UsageEvents.Event.ACTIVITY_PAUSED);
                String packageName = event.getPackageName();
                if (packageName == null || packageName.isEmpty()) continue;
                if (resumed) {
                    currentPackage = packageName;
                    lastForegroundEvent = now;
                    sawForeground = true;
                } else if (paused && packageName.equals(currentPackage)) {
                    currentPackage = null;
                }
            }
        }

        // Some devices omit a foreground event during fast app switches. Use a
        // very narrow recent-usage fallback only for FocusLock or selected apps,
        // preventing a stale unrelated app from being treated as foreground.
        if (!sawForeground && now - lastFallbackQuery >= FALLBACK_QUERY_MS) {
            lastFallbackQuery = now;
            String fallback = recentlyUsedRelevantPackage(manager, now);
            if (fallback != null) {
                currentPackage = fallback;
                lastForegroundEvent = now;
            }
        }
        if (now - lastForegroundEvent > FOREGROUND_STALE_MS) currentPackage = null;
    }

    private String recentlyUsedRelevantPackage(UsageStatsManager manager, long now) {
        List<UsageStats> stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 5_000L, now);
        if (stats == null) return null;
        String candidate = null;
        long mostRecent = 0L;
        for (UsageStats stat : stats) {
            String packageName = stat.getPackageName();
            if (packageName == null) continue;
            if (!ownPackage.equals(packageName) && !LockStore.isSelected(this, packageName)) continue;
            long used = stat.getLastTimeUsed();
            if (used >= now - 3_000L && used > mostRecent) {
                candidate = packageName;
                mostRecent = used;
            }
        }
        return candidate;
    }

    private void kickOut(String blockedPackage) {
        Intent block = new Intent(this, BlockActivity.class)
                .putExtra("blocked_package", blockedPackage)
                .putExtra("smooth_entry", ownPackage.equals(blockedPackage))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(block);
        } catch (RuntimeException error) {
            DiagnosticStore.record(this, "block_activity_restricted", error.getClass().getSimpleName());
        }
        // The full garden activity is always attempted first. This secondary
        // garden surface is used only if Android genuinely rejects it.
        if (!ownPackage.equals(blockedPackage)) {
            handler.postDelayed(() -> {
                if (LockStore.isLocked(FocusMonitorService.this, blockedPackage)
                        && !BlockActivity.isVisible()) {
                    try { startActivity(block); } catch (RuntimeException ignored) { }
                }
            }, 450L);
            handler.postDelayed(() -> {
                if (LockStore.isLocked(FocusMonitorService.this, blockedPackage)
                        && !BlockActivity.isVisible()) {
                    GardenLockOverlay.show(FocusMonitorService.this, blockedPackage);
                }
            }, OVERLAY_FALLBACK_DELAY_MS);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel("focus_lock", "FocusLock monitoring",
                    NotificationManager.IMPORTANCE_LOW);
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
        if (ProtectionRestarter.shouldMonitor(this)) {
            ProtectionRestarter.schedule(this, 5_000L);
        } else {
            GardenLockOverlay.hide();
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
