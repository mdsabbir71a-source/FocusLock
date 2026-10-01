package com.focuslock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;

/** Native animated version of the supplied "Designed to distract" line-art card. */
public final class DistractionOnboardingArtView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private boolean running;

    public DistractionOnboardingArtView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); running = true; postInvalidateOnAnimation(); }
    @Override protected void onDetachedFromWindow() { running = false; super.onDetachedFromWindow(); }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE && running) postInvalidateOnAnimation();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float t = SystemClock.uptimeMillis() / 1000f;
        float s = Math.min(w / 330f, h / 300f);
        float ox = (w - 330f * s) / 2f;
        float oy = (h - 300f * s) / 2f;
        c.save(); c.translate(ox, oy); c.scale(s, s);

        line.setStrokeWidth(1f); line.setColor(Color.argb(34, 243, 240, 230));
        float breathe = (float) Math.sin(t * .75f) * 2f;
        c.drawCircle(150, 150, 118 + breathe, line);
        c.drawCircle(150, 150, 140 - breathe, line);

        line.setStrokeWidth(1.3f); line.setColor(Color.argb(130, 243, 240, 230));
        for (int i = 0; i < 4; i++) c.drawLine(10, 70 + i * 26, i == 1 ? 100 : (i == 3 ? 80 : 130), 70 + i * 26, line);

        c.save();
        c.rotate((float) Math.sin(t * .65f) * 1.5f, 150, 160);
        c.translate(0, (float) Math.sin(t * .9f) * 3f);
        line.setStrokeWidth(1.8f); line.setColor(Color.argb(220, 243, 240, 230));
        c.drawRoundRect(108, 40, 196, 212, 16, 16, line);
        c.drawLine(136, 198, 168, 198, line);
        path.reset(); path.moveTo(80, 190); path.cubicTo(90, 172, 106, 162, 120, 160); path.moveTo(80, 190); path.cubicTo(74, 200, 74, 210, 82, 218); path.cubicTo(92, 228, 106, 228, 116, 220); c.drawPath(path, line);
        path.reset(); path.moveTo(70, 222); path.cubicTo(66, 242, 72, 260, 88, 270); path.cubicTo(106, 282, 128, 282, 144, 268); path.cubicTo(156, 258, 162, 242, 162, 222); path.moveTo(86, 236); path.quadTo(98, 246, 108, 242); path.moveTo(118, 246); path.quadTo(130, 252, 138, 246); c.drawPath(path, line);
        path.reset(); path.moveTo(230, 198); path.quadTo(246, 204, 250, 220); path.moveTo(224, 180); path.quadTo(242, 182, 244, 200); c.drawPath(path, line);

        c.restore();
        line.setStrokeWidth(2f); line.setColor(Color.rgb(232, 180, 92));
        float drift = (float) Math.sin(t * 1.1f) * 4f;
        for (int i = 0; i < 4; i++) {
            float y = 70 + i * 28;
            path.reset(); path.moveTo(196, y); path.cubicTo(210, y - 10 + drift, 220, y - 10 - drift, 230, y); path.cubicTo(240, y + 10 + drift, 250, y + 10 - drift, 262, y); path.cubicTo(274, y - 10 + drift, 286, y - 9 - drift, 300, y + 2); c.drawPath(path, line);
        }
        c.restore();
        if (running && getWindowVisibility() == VISIBLE) postInvalidateOnAnimation();
    }
}
