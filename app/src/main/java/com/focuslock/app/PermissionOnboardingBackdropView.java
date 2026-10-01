package com.focuslock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;

/** The two original ridge SVG illustrations, excluding the clock and lock-card UI. */
public final class PermissionOnboardingBackdropView extends View {
    private final Drawable top;
    private final Drawable bottom;

    public PermissionOnboardingBackdropView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        top = context.getDrawable(R.drawable.permission_ridge_top);
        bottom = context.getDrawable(R.drawable.permission_ridge_bottom);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int topHeight = Math.round(width * 190f / 330f);
        int bottomHeight = Math.round(width * 200f / 330f);
        canvas.drawColor(Color.rgb(247, 245, 239));
        top.setBounds(0, 0, width, topHeight);
        top.draw(canvas);
        bottom.setBounds(0, getHeight() - bottomHeight, width, getHeight());
        bottom.draw(canvas);
    }
}
