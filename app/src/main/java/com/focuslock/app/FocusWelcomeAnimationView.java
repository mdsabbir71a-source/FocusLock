package com.focuslock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;

/**
 * The quiet horizon artwork shared by FocusLock onboarding and account entry.
 * It intentionally draws behind content only; none of its animation affects
 * authentication, protection, or the app-locking flow.
 */
public final class FocusWelcomeAnimationView extends View {
    private final Paint paper = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final boolean fullPage;
    private boolean running;

    public FocusWelcomeAnimationView(Context context) {
        this(context, false);
    }

    public FocusWelcomeAnimationView(Context context, boolean fullPage) {
        super(context);
        this.fullPage = fullPage;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
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
        float time = SystemClock.uptimeMillis() / 1000f;

        if (fullPage) {
            paper.setColor(Color.rgb(247, 245, 239));
            canvas.drawRect(0, 0, w, h, paper);
        }
        drawUpperBreeze(canvas, w, h, time);
        if (fullPage) drawHorizon(canvas, w, h, time);
        if (running) postInvalidateDelayed(40L);
    }

    private void drawUpperBreeze(Canvas canvas, float w, float h, float time) {
        float base = fullPage ? h * .075f : h * .16f;
        float drift = (float) Math.sin(time * .24f) * dp(3);
        line.setStrokeWidth(dp(1));
        line.setColor(Color.argb(58, 47, 122, 74));
        for (int i = 0; i < 3; i++) {
            float y = base + dp(i * 19) + drift;
            path.reset();
            path.moveTo(-dp(8), y);
            path.cubicTo(w * .18f, y + dp(12), w * .35f, y - dp(13),
                    w * .50f, y + dp(2));
            path.cubicTo(w * .67f, y + dp(15), w * .82f, y + dp(8),
                    w + dp(10), y - dp(11));
            canvas.drawPath(path, line);
        }
        drawLeafOutline(canvas, w * .16f, base + dp(63), 1f);
        drawLeafOutline(canvas, w * .84f, base + dp(12), -1f);
    }

    private void drawLeafOutline(Canvas canvas, float cx, float cy, float direction) {
        line.setStrokeWidth(dp(1.2f));
        line.setColor(Color.argb(86, 47, 122, 74));
        float s = dp(19);
        path.reset();
        path.moveTo(cx - s * direction, cy);
        path.cubicTo(cx - s * .22f * direction, cy - s * .8f,
                cx + s * .80f * direction, cy - s * .66f, cx + s * direction, cy);
        path.cubicTo(cx + s * .20f * direction, cy + s * .72f,
                cx - s * .76f * direction, cy + s * .76f, cx - s * direction, cy);
        canvas.drawPath(path, line);
        canvas.drawLine(cx - s * .70f * direction, cy, cx + s * .66f * direction, cy + s * .25f, line);
    }

    private void drawHorizon(Canvas canvas, float w, float h, float time) {
        float horizon = h * .51f;
        float sway = (float) Math.sin(time * .22f) * dp(4);

        // Gentle sun and rays in the open right-hand sky.
        line.setColor(Color.argb(88, 47, 122, 74));
        line.setStrokeWidth(dp(1.25f));
        float sx = w * .77f, sy = horizon + dp(28);
        canvas.drawCircle(sx, sy, dp(39), line);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8d;
            float x1 = sx + (float) Math.cos(angle) * dp(55);
            float y1 = sy + (float) Math.sin(angle) * dp(55);
            float x2 = sx + (float) Math.cos(angle) * dp(67);
            float y2 = sy + (float) Math.sin(angle) * dp(67);
            canvas.drawLine(x1, y1, x2, y2, line);
        }

        line.setColor(Color.argb(108, 47, 122, 74));
        line.setStrokeWidth(dp(1.45f));
        path.reset();
        path.moveTo(-dp(14), h * .76f + sway);
        path.cubicTo(w * .14f, h * .68f, w * .29f, h * .69f, w * .42f, h * .75f);
        path.cubicTo(w * .53f, h * .80f, w * .62f, h * .80f, w * .70f, h * .74f);
        path.cubicTo(w * .80f, h * .67f, w * .91f, h * .70f, w + dp(14), h * .78f);
        canvas.drawPath(path, line);
        path.reset();
        path.moveTo(-dp(14), h * .86f - sway);
        path.cubicTo(w * .16f, h * .79f, w * .29f, h * .81f, w * .41f, h * .86f);
        path.cubicTo(w * .53f, h * .91f, w * .65f, h * .90f, w * .75f, h * .84f);
        path.cubicTo(w * .84f, h * .79f, w * .93f, h * .80f, w + dp(14), h * .87f);
        canvas.drawPath(path, line);

        line.setColor(Color.argb(50, 47, 122, 74));
        line.setStrokeWidth(dp(1));
        drawBird(canvas, w * .18f, horizon + dp(15), 1f);
        drawBird(canvas, w * .30f, horizon - dp(25), .72f);
        drawGrass(canvas, w * .14f, h * .98f, 1f);
        drawGrass(canvas, w * .87f, h * .99f, .82f);
    }

    private void drawBird(Canvas canvas, float x, float y, float scale) {
        float s = dp(10) * scale;
        path.reset();
        path.moveTo(x - s, y);
        path.quadTo(x - s * .45f, y - s * .7f, x, y);
        path.quadTo(x + s * .45f, y - s * .7f, x + s, y);
        canvas.drawPath(path, line);
    }

    private void drawGrass(Canvas canvas, float x, float y, float scale) {
        float s = dp(28) * scale;
        canvas.drawLine(x, y, x + s * .25f, y - s * 1.65f, line);
        canvas.drawLine(x, y, x - s * .78f, y - s * 1.22f, line);
        canvas.drawLine(x, y, x + s * 1.00f, y - s * .92f, line);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
