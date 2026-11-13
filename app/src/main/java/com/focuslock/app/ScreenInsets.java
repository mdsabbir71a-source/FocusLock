package com.focuslock.app;

import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;

/** Keeps activity and full-screen dialog content clear of system bars and cutouts. */
final class ScreenInsets {
    private ScreenInsets() {}

    static void apply(Window window) {
        if (window == null || Build.VERSION.SDK_INT < 35) return;
        View content = window.getDecorView().findViewById(android.R.id.content);
        if (content == null) return;
        final int left = content.getPaddingLeft();
        final int top = content.getPaddingTop();
        final int right = content.getPaddingRight();
        final int bottom = content.getPaddingBottom();
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(left + safe.left, top + safe.top, right + safe.right, bottom + safe.bottom);
            return insets;
        });
        content.requestApplyInsets();
    }
}
