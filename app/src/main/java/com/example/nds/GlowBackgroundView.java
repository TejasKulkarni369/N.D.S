package com.example.nds;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

public class GlowBackgroundView extends View {

    // how far each glow circles around its resting spot (fraction of screen width)
    private static final float ORBIT_MIN = 0.16f;
    private static final float ORBIT_MAX = 0.34f;

    // how fast they circle (radians per second; 0.5 = one lap in about 12 seconds)
    private static final float SPEED_MIN = 0.30f;
    private static final float SPEED_MAX = 0.80f;

    private static class Blob {
        float cx, cy;            // resting position (fraction of screen)
        float radiusFrac;        // glow radius (fraction of screen width)
        float radius;
        float orbit, targetOrbit;
        float angle, speed, targetSpeed;
        float nextChange;        // seconds until a new random pattern
        int color;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    private final Blob[] blobs = new Blob[2];
    private final Random rnd = new Random();
    private long lastNanos = 0;
    private boolean running = false;
    private boolean animated = true;   // Quality mode only

    public GlowBackgroundView(Context context) {
        super(context);
        init();
    }

    public GlowBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // {x, y, radius}: top-left orange glow, right-middle red-orange glow
        float[][] cfg = {{0.15f, 0.08f, 0.97f}, {0.95f, 0.55f, 0.76f}};
        int[] colors = {0x8CFF7A1A, 0x59FF5000};
        for (int i = 0; i < blobs.length; i++) {
            Blob b = new Blob();
            b.cx = cfg[i][0];
            b.cy = cfg[i][1];
            b.radiusFrac = cfg[i][2];
            b.color = colors[i];
            b.angle = rnd.nextFloat() * 6.2832f;
            b.orbit = randomOrbit();
            b.targetOrbit = b.orbit;
            b.speed = randomSpeed();
            b.targetSpeed = b.speed;
            b.nextChange = 3 + rnd.nextFloat() * 4;
            blobs[i] = b;
        }
        // the two circles start by turning in opposite directions
        if (blobs[0].speed * blobs[1].speed > 0) blobs[1].speed = -blobs[1].speed;
        blobs[1].targetSpeed = blobs[1].speed;
    }

    private float randomOrbit() {
        return ORBIT_MIN + rnd.nextFloat() * (ORBIT_MAX - ORBIT_MIN);
    }

    // random speed and random direction
    private float randomSpeed() {
        float s = SPEED_MIN + rnd.nextFloat() * (SPEED_MAX - SPEED_MIN);
        return rnd.nextBoolean() ? s : -s;
    }

    // called when the Optimizations mode changes
    public void refreshMode() {
        animated = Perf.ambient(getContext());
        lastNanos = 0;
        if (animated) postInvalidateOnAnimation();
        else invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        for (Blob b : blobs) {
            b.radius = b.radiusFrac * w;
            b.paint.setShader(new RadialGradient(
                    0, 0, b.radius, b.color, b.color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.nanoTime();
        float dt = (lastNanos == 0) ? 0f : Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;

        canvas.drawColor(0xFF0D0D0F);
        int w = getWidth();
        int h = getHeight();

        for (Blob b : blobs) {
            float x;
            float y;

            if (animated) {
                b.nextChange -= dt;
                if (b.nextChange <= 0) {            // pick a new random pattern
                    b.targetSpeed = randomSpeed();
                    b.targetOrbit = randomOrbit();
                    b.nextChange = 4 + rnd.nextFloat() * 5;
                }
                float ease = Math.min(1f, dt * 0.5f);
                b.speed += (b.targetSpeed - b.speed) * ease;
                b.orbit += (b.targetOrbit - b.orbit) * ease;
                b.angle += b.speed * dt;

                x = w * b.cx + (float) Math.cos(b.angle) * b.orbit * w;
                y = h * b.cy + (float) Math.sin(b.angle) * b.orbit * w;
            } else {
                x = w * b.cx;       // still: exactly where the design has them
                y = h * b.cy;
            }

            canvas.save();
            canvas.translate(x, y);
            canvas.drawCircle(0, 0, b.radius, b.paint);
            canvas.restore();
        }

        if (running && animated) postInvalidateOnAnimation();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        animated = Perf.ambient(getContext());
        lastNanos = 0;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        running = (visibility == VISIBLE);
        animated = Perf.ambient(getContext());
        if (running) {
            lastNanos = 0;
            postInvalidateOnAnimation();
        }
    }
}