package com.example.nds;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.core.content.res.ResourcesCompat;

public class IntroView extends View {

    private static final long DURATION = 1700;

    private float t = 0f;   // timeline 0..1
    private final float d;
    private final String title;
    private final String tagline;
    private final boolean focus;

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tagPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final RectF body = new RectF();
    private final RectF arch = new RectF();
    private final Path shackle = new Path();
    private final OvershootInterpolator overshoot = new OvershootInterpolator(2.2f);

    // the app intro
    public IntroView(Context ctx) {
        this(ctx, "N.D.S", "Block your Distractions", false);
    }

    // any intro (the Focus intro uses focus = true)
    public IntroView(Context ctx, String title, String tagline, boolean focus) {
        super(ctx);
        this.title = title;
        this.tagline = tagline;
        this.focus = focus;
        d = ctx.getResources().getDisplayMetrics().density;

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
        glowPaint.setShader(new RadialGradient(
                0, 0, 1f, 0x99FF7A1A, 0x00FF7A1A, Shader.TileMode.CLAMP));

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(20 * d);
        textPaint.setLetterSpacing(0.35f);
        Typeface bold = ResourcesCompat.getFont(ctx, R.font.sora_bold);
        if (bold != null) textPaint.setTypeface(bold);

        tagPaint.setTextAlign(Paint.Align.CENTER);
        tagPaint.setTextSize(14 * d);
        tagPaint.setLetterSpacing(0.08f);
        Typeface regular = ResourcesCompat.getFont(ctx, R.font.sora_regular);
        if (regular != null) tagPaint.setTypeface(regular);

        setClickable(true);   // swallow touches while the intro plays
    }

    private static float seg(float t, float a, float b) {
        return Math.max(0f, Math.min(1f, (t - a) / (b - a)));
    }

    private static float easeOut(float x) {
        float k = 1f - x;
        return 1f - k * k * k;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xFF0D0D0F);

        float cx = getWidth() / 2f;
        float cy = getHeight() * 0.44f;
        float r = 52 * d;
        float sw = 8 * d;

        // soft glow growing behind the ring
        float g = easeOut(seg(t, 0.10f, 0.8f));
        float gr = (60 + 130 * g) * d;
        glowPaint.setAlpha((int) (255 * g));
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(gr, gr);
        canvas.drawCircle(0, 0, 1f, glowPaint);
        canvas.restore();

        // ring track
        ringPaint.setStrokeWidth(sw);
        ringPaint.setColor(0x26FFFFFF);
        canvas.drawCircle(cx, cy, r, ringPaint);

        // orange arc draws itself, with a layered glow
        float sweep = 360f * easeOut(seg(t, 0f, 0.45f));
        if (sweep > 0f) {
            oval.set(cx - r, cy - r, cx + r, cy + r);
            float[] widths = {sw + 14 * d, sw + 7 * d, sw};
            int[] alphas = {0x33, 0x66, 0xFF};
            for (int i = 0; i < 3; i++) {
                ringPaint.setStrokeWidth(widths[i]);
                ringPaint.setColor((alphas[i] << 24) | 0x00FF7A1A);
                canvas.drawArc(oval, -90f, sweep, false, ringPaint);
            }
        }

        // centre: the lock (app intro) or a focus dot (Focus intro) pops in
        float lp = seg(t, 0.28f, 0.62f);
        if (lp > 0f) {
            float scale = overshoot.getInterpolation(lp);
            float a = seg(t, 0.28f, 0.45f);

            if (focus) {
                fillPaint.setColor(((int) (255 * a) << 24) | 0x00FF7A1A);
                canvas.drawCircle(cx, cy, 12 * d * scale, fillPaint);
            } else {
                canvas.save();
                canvas.scale(scale, scale, cx, cy);

                fillPaint.setColor(((int) (255 * a) << 24) | 0x00FF7A1A);
                body.set(cx - 13 * d, cy - 4 * d, cx + 13 * d, cy + 17 * d);
                canvas.drawRoundRect(body, 4 * d, 4 * d, fillPaint);

                arch.set(cx - 7 * d, cy - 17 * d, cx + 7 * d, cy - 3 * d);
                shackle.reset();
                shackle.moveTo(cx - 7 * d, cy - 4 * d);
                shackle.lineTo(cx - 7 * d, cy - 10 * d);
                shackle.arcTo(arch, 180f, 180f);
                shackle.lineTo(cx + 7 * d, cy - 4 * d);
                ringPaint.setStrokeWidth(3.5f * d);
                ringPaint.setColor(((int) (255 * a) << 24) | 0x00FFB27A);
                canvas.drawPath(shackle, ringPaint);

                canvas.restore();
            }
        }

        // title fades up
        float ta = easeOut(seg(t, 0.48f, 0.78f));
        float nameY = cy + r + 64 * d + (1f - ta) * 12 * d;
        textPaint.setColor(((int) (255 * ta) << 24) | 0x00F5F5F5);
        canvas.drawText(title, cx, nameY, textPaint);

        // tagline fades up below the title
        float ga = easeOut(seg(t, 0.62f, 0.92f));
        float tagY = nameY + 30 * d + (1f - ga) * 10 * d;
        tagPaint.setColor(((int) (255 * ga) << 24) | 0x00C9C9CF);
        canvas.drawText(tagline, cx, tagY, tagPaint);
    }

    public void start(Runnable onRevealStart) {
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(DURATION);
        a.setInterpolator(new LinearInterpolator());
        a.addUpdateListener(an -> {
            t = (float) an.getAnimatedValue();
            invalidate();
        });
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                exit(onRevealStart);
            }
        });
        a.start();
    }

    private void exit(Runnable onRevealStart) {
        if (onRevealStart != null) onRevealStart.run();
        animate()
                .alpha(0f)
                .scaleX(1.12f)
                .scaleY(1.12f)
                .setDuration(450)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    ViewGroup p = (ViewGroup) getParent();
                    if (p != null) p.removeView(this);
                })
                .start();
    }
}