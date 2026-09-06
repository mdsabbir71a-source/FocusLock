package com.focuslock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;

/** Calm, lightweight welcome animation drawn locally in the FocusLock visual language. */
public final class FocusWelcomeAnimationView extends View {
    private static final int LEAF_COUNT = 6;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint leaf = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float[] leafX = {.10f, .24f, .73f, .88f, .18f, .82f};
    private final float[] leafY = {.26f, .69f, .21f, .59f, .44f, .39f};
    private final float[] phase = {0f, .8f, 1.7f, 2.6f, 3.4f, 4.1f};
    private boolean running;

    public FocusWelcomeAnimationView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        leaf.setStyle(Paint.Style.FILL);
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

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        running = visibility == VISIBLE;
        if (running) postInvalidateOnAnimation();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float t = SystemClock.uptimeMillis() / 1000f;
        float pulse = (float) Math.sin(t * 1.35f);

        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.rgb(248, 251, 246));
        canvas.drawRect(0, 0, w, h, fill);

        fill.setColor(Color.argb(125, 248, 225, 157));
        canvas.drawCircle(w * .86f, h * .13f, dp(44) + pulse * dp(2), fill);
        fill.setColor(Color.argb(150, 228, 242, 224));
        canvas.drawCircle(w * .10f, h * .66f, dp(60), fill);

        drawContourLines(canvas, w, h, t);

        float cx = w * .5f;
        float cy = h * .43f;
        line.setStrokeWidth(dp(1.4f));
        for (int i = 0; i < 3; i++) {
            int alpha = 55 - i * 12;
            line.setColor(Color.argb(alpha, 43, 126, 77));
            float radius = dp(51 + i * 15) + pulse * dp(2.5f + i);
            canvas.drawCircle(cx, cy, radius, line);
        }

        drawGrowingPath(canvas, w, h, t);
        for (int i = 0; i < LEAF_COUNT; i++) drawFloatingLeaf(canvas, w, h, i, t);

        if (running) postInvalidateDelayed(32L);
    }

    private void drawContourLines(Canvas canvas, float w, float h, float t) {
        line.setStrokeWidth(dp(1f));
        line.setColor(Color.argb(33, 39, 91, 59));
        float drift = (float) Math.sin(t * .24f) * dp(5);
        for (int i = 0; i < 6; i++) {
            float y = h * (.58f + i * .065f);
            path.reset();
            path.moveTo(-dp(20), y + drift);
            path.cubicTo(w * .22f, y - dp(25), w * .42f, y + dp(28), w * .63f, y - dp(8));
            path.cubicTo(w * .79f, y - dp(31), w * .92f, y + dp(11), w + dp(20), y - dp(18));
            canvas.drawPath(path, line);
        }
    }

    private void drawGrowingPath(Canvas canvas, float w, float h, float t) {
        float sway = (float) Math.sin(t * .8f) * dp(3);
        line.setStrokeWidth(dp(2.2f));
        line.setColor(Color.argb(105, 39, 91, 59));
        path.reset();
        path.moveTo(w * .5f, h * .92f);
        path.cubicTo(w * .46f, h * .78f, w * .57f, h * .69f, w * .5f + sway, h * .58f);
        canvas.drawPath(path, line);
        drawLeaf(canvas, w * .49f + sway * .4f, h * .71f, dp(10), -32 + sway);
        drawLeaf(canvas, w * .53f + sway * .7f, h * .64f, dp(9), 26 + sway);
    }

    private void drawFloatingLeaf(Canvas canvas, float w, float h, int index, float t) {
        float dx = (float) Math.sin(t * (.34f + index * .025f) + phase[index]) * dp(11);
        float dy = (float) Math.cos(t * (.28f + index * .02f) + phase[index]) * dp(7);
        float size = dp(5.5f + (index % 3) * 1.4f);
        float rotation = (float) Math.sin(t * .45f + phase[index]) * 24f;
        drawLeaf(canvas, leafX[index] * w + dx, leafY[index] * h + dy, size, rotation);
    }

    private void drawLeaf(Canvas canvas, float cx, float cy, float radius, float rotation) {
        canvas.save();
        canvas.rotate(rotation, cx, cy);
        path.reset();
        path.moveTo(cx - radius, cy);
        path.quadTo(cx, cy - radius * .72f, cx + radius, cy);
        path.quadTo(cx, cy + radius * .72f, cx - radius, cy);
        path.close();
        leaf.setColor(Color.argb(70, 45, 130, 78));
        canvas.drawPath(path, leaf);
        line.setStrokeWidth(dp(.7f));
        line.setColor(Color.argb(70, 39, 91, 59));
        canvas.drawLine(cx - radius * .72f, cy, cx + radius * .72f, cy, line);
        canvas.restore();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
