package com.focuslock.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** A short, non-blocking leaf burst that confirms the plan was saved. */
public final class SuccessBurstView extends View {
    private static final int COUNT = 18;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private float progress;

    public SuccessBurstView(Context context) {
        super(context);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void start(Runnable onFinished) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1250);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> {
            progress = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (onFinished != null) onFinished.run();
            }
        });
        animator.start();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float originX = getWidth() / 2f;
        float originY = getHeight() - dp(72);
        float fade = progress < .68f ? 1f : 1f - (progress - .68f) / .32f;
        for (int i = 0; i < COUNT; i++) {
            double angle = Math.toRadians(205 + (i * 130 / (float) (COUNT - 1)));
            float distance = dp(42 + (i % 5) * 17) * progress;
            float x = originX + (float) Math.cos(angle) * distance;
            float y = originY + (float) Math.sin(angle) * distance - dp(36) * progress * (1f - progress);
            float leafSize = dp(4 + i % 3);
            int color = i % 3 == 0 ? Color.rgb(244, 226, 171)
                    : (i % 2 == 0 ? Color.rgb(45, 130, 78) : Color.rgb(160, 205, 166));
            paint.setColor(color);
            paint.setAlpha(Math.max(0, Math.min(255, (int) (255 * fade))));
            drawLeaf(canvas, x, y, leafSize, i * 31f + progress * 120f);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.rgb(45, 130, 78));
        paint.setAlpha((int) (130 * fade));
        canvas.drawCircle(originX, originY, dp(18 + progress * 35), paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawLeaf(Canvas canvas, float cx, float cy, float radius, float rotation) {
        canvas.save();
        canvas.rotate(rotation, cx, cy);
        path.reset();
        path.moveTo(cx - radius, cy);
        path.quadTo(cx, cy - radius, cx + radius, cy);
        path.quadTo(cx, cy + radius, cx - radius, cy);
        path.close();
        canvas.drawPath(path, paint);
        canvas.restore();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
