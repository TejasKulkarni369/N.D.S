package com.example.nds;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

public class GlowRingView extends View {

    // one slow glow takes this long; make it bigger for slower, smaller for faster
    private static final long PERIOD_MS = 4000;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private float target = 0f;       // 0..1 of the ring that should be lit
    private float shown = -1f;       // what is drawn right now (glides to the target)
    private long lastFrame = 0;
    private boolean pulsing = false;

    public GlowRingView(Context context) {
        super(context);
        init();
    }

    public GlowRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        trackPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setProgress(float p) {
        target = Math.max(0f, Math.min(1f, p));
        // the first value, a big jump (new day) or Performance mode: no gliding
        if (shown < 0f || Math.abs(target - shown) > 0.08f || !Motion.on(getContext())) {
            shown = target;
        }
        invalidate();
    }

    public void startPulse() {
        pulsing = true;
        postInvalidateOnAnimation();
    }

    public void stopPulse() {
        pulsing = false;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastFrame = 0;
        if (pulsing) postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // time between two frames (capped, so one slow frame never makes a jump)
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;

        // glide: closer to the target every frame, slower and slower
        boolean settling = false;
        if (shown >= 0f && shown != target) {
            shown += (target - shown) * (1f - (float) Math.exp(-dt * 7f));
            if (Math.abs(target - shown) < 0.0004f) shown = target;
            else settling = true;
        }

        float d = getResources().getDisplayMetrics().density;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float stroke = 12 * d;
        float r = 60 * d - stroke / 2f;          // 120dp outer diameter
        oval.set(cx - r, cy - r, cx + r, cy + r);

        // track
        trackPaint.setStrokeWidth(stroke);
        trackPaint.setColor(0x2EFFFFFF);
        canvas.drawCircle(cx, cy, r, trackPaint);

        // slow, smooth breathing glow: dim -> bright -> dim, no sharp beat
        float k = 0.4f;
        if (pulsing) {
            float phase = (SystemClock.uptimeMillis() % PERIOD_MS) / (float) PERIOD_MS;
            float breath = 0.5f - 0.5f * (float) Math.cos(2 * Math.PI * phase);   // 0..1..0
            k = 0.30f + 0.55f * breath;
        }

        float sweep = 360f * Math.max(0f, shown);
        if (sweep > 0f) {
            float[] widths = {stroke + 22 * d * k, stroke + 11 * d * k, stroke};
            int[] alphas = {(int) (0x48 * k), (int) (0x88 * k), 0xFF};
            for (int i = 0; i < 3; i++) {
                arcPaint.setStrokeWidth(widths[i]);
                arcPaint.setColor((alphas[i] << 24) | 0x00FF7A1A);
                canvas.drawArc(oval, -90f, sweep, false, arcPaint);
            }
        }

        if (pulsing || settling) {
            postInvalidateOnAnimation();
        } else {
            lastFrame = 0;   // idle: the next change starts with a clean time step
        }
    }
}
