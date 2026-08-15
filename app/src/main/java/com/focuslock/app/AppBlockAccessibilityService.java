package com.focuslock.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

public class AppBlockAccessibilityService extends AccessibilityService {
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;
        String packageName = event.getPackageName().toString();
        if (LockStore.isLocked(this, packageName)) {
            Intent block = new Intent(this, BlockActivity.class);
            block.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            block.putExtra("blocked_package", packageName);
            startActivity(block);
        }
    }
    @Override public void onInterrupt() {}
}
