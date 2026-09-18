package com.focuslock.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * User-enabled compatibility monitor. It receives only window/app change events,
 * never reads text, passwords, or screen content. This is a direct foreground
 * signal for phones whose Usage Access reports are delayed or incomplete.
 */
public final class FocusAccessibilityService extends AccessibilityService {
    private static final long TICK_MS = 500L;
    private static final long BLOCK_COOLDOWN_MS = 1_200L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String foregroundPackage;
    private long lastTick;
    private long lastBlock;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            try {
                monitorForeground();
            } catch (Throwable error) {
                DiagnosticStore.record(FocusAccessibilityService.this,
                        "compatibility_monitor_recovered", error.getClass().getSimpleName());
            } finally {
                handler.postDelayed(this, TICK_MS);
            }
        }
    };

    @Override protected void onServiceConnected() {
        AccessibilityServiceInfo info = getServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 60;
        info.flags = 0;
        setServiceInfo(info);
        lastTick = SystemClock.elapsedRealtime();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        SupabaseApi.syncDeviceState(this);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;
        CharSequence packageName = event.getPackageName();
        if (packageName == null || packageName.length() == 0) return;
        updateForegroundPackage(packageName.toString());
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override public void onInterrupt() { }

    @Override public boolean onUnbind(Intent intent) {
        handler.removeCallbacks(ticker);
        foregroundPackage = null;
        return super.onUnbind(intent);
    }

    private void monitorForeground() {
        long now = SystemClock.elapsedRealtime();
        long elapsed = Math.max(0L, Math.min(1_500L, now - lastTick));
        lastTick = now;
        if (!AccessStore.isAllowed(this) || !LockStore.isEnabled(this)
                || !RemoteConfigStore.appBlockingEnabled(this)) return;
        String activeWindow = activeWindowPackage();
        if (activeWindow != null) updateForegroundPackage(activeWindow);
        String target = foregroundPackage;
        if (target == null || target.isEmpty()) return;
        String own = getPackageName();
        // A lock for another app must never cover FocusLock when optional
        // self-lock is off.
        if (own.equals(target) && !LockStore.isLocked(this, own)) {
            BlockOverlay.hide();
            return;
        }
        if (LockStore.isSelected(this, target)) {
            boolean newlyLocked = LockStore.addUsage(this, target, elapsed);
            if (newlyLocked || LockStore.isLocked(this, target)) block(target, own);
        } else if (own.equals(target) && LockStore.isLocked(this, own) && !BlockActivity.isVisible()) {
            block(own, own);
        } else if (BlockOverlay.isShowing() && !LockStore.isLocked(this, target)) {
            BlockOverlay.hide();
        }
    }

    private void block(String target, String own) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastBlock < BLOCK_COOLDOWN_MS) return;
        if (own.equals(target) && BlockActivity.isVisible()) return;
        lastBlock = now;
        if (!own.equals(target)) {
            performGlobalAction(GLOBAL_ACTION_HOME);
        }
        Intent block = new Intent(this, BlockActivity.class)
                .putExtra("blocked_package", target)
                .putExtra("smooth_entry", own.equals(target))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(block);
        } catch (RuntimeException error) {
            DiagnosticStore.record(this, "compatibility_block_restricted",
                    error.getClass().getSimpleName());
        }
        if (!own.equals(target)) {
            // On normal phones the regular, branded lock screen is now visible.
            // Use the simpler overlay only if Android actually rejects that launch.
            handler.postDelayed(() -> {
                if (LockStore.isLocked(FocusAccessibilityService.this, target)
                        && !BlockActivity.isVisible()) {
                    BlockOverlay.show(FocusAccessibilityService.this, target);
                }
            }, 500L);
        }
    }

    private void updateForegroundPackage(String packageName) {
        if (packageName == null || packageName.equals(foregroundPackage)) return;
        foregroundPackage = packageName;
        // Never charge time that elapsed while a different app was in front.
        lastTick = SystemClock.elapsedRealtime();
    }

    private String activeWindowPackage() {
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            CharSequence packageName = root == null ? null : root.getPackageName();
            return packageName == null || packageName.length() == 0 ? null : packageName.toString();
        } catch (RuntimeException ignored) {
            return null;
        } finally {
            if (root != null) root.recycle();
        }
    }
}
