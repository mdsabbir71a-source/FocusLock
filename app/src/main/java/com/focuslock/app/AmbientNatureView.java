package com.focuslock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;

/** Lightweight botanical motion used only while the FocusLock screen is visible. */
public final class AmbientNatureView extends View {
    private static final int LEAF_COUNT = 7;
    private final Paint haze = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint leaf = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path leafPath = new Path();
    private final float[] x = new float[LEAF_COUNT];
    private final float[] y = new float[LEAF_COUNT];
    private final float[] size = new float[LEAF_COUNT];
    private final float[] speed = new float[LEAF_COUNT];
    private final float[] phase = new float[LEAF_COUNT];
    private boolean running;

    public AmbientNatureView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        haze.setStyle(Paint.Style.FILL);
        leaf.setStyle(Paint.Style.FILL);
        leaf.setColor(Color.argb(24, 45, 130, 78));
        for (int i = 0; i < LEAF_COUNT; i++) {
            x[i] = .08f + ((i * .173f) % .84f);
            y[i] = .06f + ((i * .229f) % .88f);
            size[i] = dp(7 + (i % 4) * 2);
            speed[i] = .000035f + i * .000004f;
            phase[i] = i * .9f;
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        postInvalidateOnAnimation();
    }

    @Override protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        haze.setColor(Color.argb(34, 224, 241, 220));
        canvas.drawCircle(w * .92f, h * .12f, Math.min(w, h) * .25f, haze);
        haze.setColor(Color.argb(25, 244, 226, 171));
        canvas.drawCircle(w * .04f, h * .78f, Math.min(w, h) * .22f, haze);

        long now = SystemClock.uptimeMillis();
        for (int i = 0; i < LEAF_COUNT; i++) {
            float drift = (float) Math.sin(now * speed[i] + phase[i]);
            float bob = (float) Math.cos(now * speed[i] * .72f + phase[i]) * dp(7);
            float px = x[i] * w + drift * dp(13);
            float py = y[i] * h + bob;
            drawLeaf(canvas, px, py, size[i], drift * 18f);
        }
        if (running) postInvalidateOnAnimation();
    }

    private void drawLeaf(Canvas canvas, float cx, float cy, float radius, float rotation) {
        canvas.save();
        canvas.rotate(rotation, cx, cy);
        leafPath.reset();
        leafPath.moveTo(cx - radius, cy);
        leafPath.quadTo(cx, cy - radius * .78f, cx + radius, cy);
        leafPath.quadTo(cx, cy + radius * .78f, cx - radius, cy);
        leafPath.close();
        canvas.drawPath(leafPath, leaf);
        canvas.restore();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
