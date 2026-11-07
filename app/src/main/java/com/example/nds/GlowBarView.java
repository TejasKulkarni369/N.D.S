package com.example.nds;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

public class GlowBarView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private float target = 0f;
    private float shown = -1f;       // what is drawn (glides to the target)
    private long lastFrame = 0;
    private int color = 0xFFFF7A1A;

    public GlowBarView(Context context) {
        super(context);
    }

    public GlowBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setProgress(float p) {
        target = Math.max(0f, Math.min(1f, p));
        // a small change (the live tick) glides. A big one (a list row got another app) jumps.
        if (shown < 0f || Math.abs(target - shown) > 0.08f || !Motion.on(getContext())) {
            shown = target;
        }
        invalidate();
    }

    public void setBarColor(int c) {
        color = c;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;

        boolean settling = false;
        if (shown >= 0f && shown != target) {
            shown += (target - shown) * (1f - (float) Math.exp(-dt * 8f));
            if (Math.abs(target - shown) < 0.0004f) shown = target;
            else settling = true;
        }

        float d = getResources().getDisplayMetrics().density;
        float w = getWidth();
        float h = getHeight();
        float r = h / 2f;

        // track
        trackPaint.setColor(0x1FFFFFFF);
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, trackPaint);

        float fw = w * Math.max(0f, shown);
        if (fw > 0f) {
            int rgb = color & 0x00FFFFFF;

            // glow: three wider, fainter bars behind the real one
            float[] grow = {5 * d, 3 * d, 1.5f * d};
            int[] alpha = {0x22, 0x38, 0x66};
            for (int i = 0; i < 3; i++) {
                rect.set(-grow[i], -grow[i], fw + grow[i], h + grow[i]);
                fillPaint.setColor((alpha[i] << 24) | rgb);
                canvas.drawRoundRect(rect, r + grow[i], r + grow[i], fillPaint);
            }

            // the bar itself
            rect.set(0, 0, fw, h);
            fillPaint.setColor(0xFF000000 | rgb);
            canvas.drawRoundRect(rect, r, r, fillPaint);
        }

        if (settling) postInvalidateOnAnimation();
        else lastFrame = 0;
    }
}
