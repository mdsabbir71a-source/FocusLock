package com.focuslock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/** Short first-run spotlight: tap the highlighted control to continue. */
public final class CoachMarkOverlay extends View {
    private final View target;
    private final String message;
    private final Runnable action;
    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubble = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pulseRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF spotlight = new RectF();
    private final long startedAt = SystemClock.uptimeMillis();

    public CoachMarkOverlay(Context context, View target, String message, Runnable action) {
        super(context);
        this.target = target;
        this.message = message;
        this.action = action;
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setClickable(true);
        dim.setColor(Color.argb(178, 8, 15, 11));
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        bubble.setColor(Color.rgb(244, 226, 171));
        label.setColor(Color.rgb(17, 24, 39));
        label.setTextSize(dp(17));
        label.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        ring.setColor(Color.rgb(244, 226, 171));
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(dp(3));
        pulseRing.setColor(Color.rgb(244, 226, 171));
        pulseRing.setStyle(Paint.Style.STROKE);
        pulseRing.setStrokeWidth(dp(2));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setAlpha(0f);
        animate().alpha(1f).setDuration(220).start();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateSpotlight();
        canvas.drawRect(0, 0, getWidth(), getHeight(), dim);
        canvas.drawRoundRect(spotlight, dp(20), dp(20), clear);
        canvas.drawRoundRect(spotlight, dp(20), dp(20), ring);
        float cycle = (SystemClock.uptimeMillis() - startedAt) / 700f;
        float pulse = ((float) Math.sin(cycle) + 1f) / 2f;
        RectF halo = new RectF(spotlight);
        float haloSize = dp(3) + pulse * dp(7);
        halo.inset(-haloSize, -haloSize);
        pulseRing.setAlpha((int) (155 * (1f - pulse * .65f)));
        canvas.drawRoundRect(halo, dp(23), dp(23), pulseRing);

        String[] lines = splitMessage(message, getWidth() - dp(76));
        float textWidth = 0;
        for (String line : lines) textWidth = Math.max(textWidth, label.measureText(line));
        float bubbleWidth = Math.min(getWidth() - dp(34), textWidth + dp(34));
        float bubbleHeight = lines.length == 1 ? dp(58) : dp(78);
        float left = Math.max(dp(17), Math.min(getWidth() - bubbleWidth - dp(17),
                spotlight.centerX() - bubbleWidth / 2f));
        boolean below = spotlight.bottom + dp(84) < getHeight();
        float bob = (float) Math.sin(cycle * .72f) * dp(2);
        float top = (below ? spotlight.bottom + dp(18) : spotlight.top - bubbleHeight - dp(18)) + bob;
        RectF messageBox = new RectF(left, top, left + bubbleWidth, top + bubbleHeight);
        Path arrow = new Path();
        if (below) {
            arrow.moveTo(spotlight.centerX(), spotlight.bottom + dp(3));
            arrow.lineTo(spotlight.centerX() - dp(10), messageBox.top + dp(2));
            arrow.lineTo(spotlight.centerX() + dp(10), messageBox.top + dp(2));
        } else {
            arrow.moveTo(spotlight.centerX(), spotlight.top - dp(3));
            arrow.lineTo(spotlight.centerX() - dp(10), messageBox.bottom - dp(2));
            arrow.lineTo(spotlight.centerX() + dp(10), messageBox.bottom - dp(2));
        }
        arrow.close();
        canvas.drawPath(arrow, bubble);
        canvas.drawRoundRect(messageBox, dp(19), dp(19), bubble);

        Paint.FontMetrics fm = label.getFontMetrics();
        float lineHeight = dp(22);
        float firstBaseline = messageBox.centerY() - (lines.length - 1) * lineHeight / 2f
                - (fm.ascent + fm.descent) / 2f;
        for (int i = 0; i < lines.length; i++) {
            float width = label.measureText(lines[i]);
            canvas.drawText(lines[i], messageBox.centerX() - width / 2f,
                    firstBaseline + i * lineHeight, label);
        }
        postInvalidateOnAnimation();
    }

    private String[] splitMessage(String value, float maxWidth) {
        if (label.measureText(value) <= maxWidth) return new String[]{value};
        int middle = value.length() / 2;
        int split = value.lastIndexOf(' ', middle);
        if (split < 1) split = value.indexOf(' ', middle);
        if (split < 1) return new String[]{value};
        return new String[]{value.substring(0, split).trim(), value.substring(split + 1).trim()};
    }

    private void updateSpotlight() {
        int[] targetLocation = new int[2];
        int[] overlayLocation = new int[2];
        target.getLocationOnScreen(targetLocation);
        getLocationOnScreen(overlayLocation);
        float pad = dp(9);
        spotlight.set(targetLocation[0] - overlayLocation[0] - pad,
                targetLocation[1] - overlayLocation[1] - pad,
                targetLocation[0] - overlayLocation[0] + target.getWidth() + pad,
                targetLocation[1] - overlayLocation[1] + target.getHeight() + pad);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) return true;
        updateSpotlight();
        if (!spotlight.contains(event.getX(), event.getY())) return true;
        ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) parent.removeView(this);
        if (action != null) action.run();
        return true;
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
