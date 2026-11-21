package com.example.nds;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;

/**
 * Dropdown for a section: it slides open and closed while it fades, and the arrow turns.
 * With the Performance mode it just appears and disappears.
 */
public class Expander {

    private static final long BASE_MS = 340;

    public static boolean isOpen(View body) {
        Object o = body.getTag(R.id.expandOpen);
        return o != null ? (Boolean) o : body.getVisibility() == View.VISIBLE;
    }

    public static void toggle(View body, View arrow) {
        if (isOpen(body)) close(body, arrow); else open(body, arrow);
    }

    public static void open(View body, View arrow) {
        stop(body);
        body.setTag(R.id.expandOpen, Boolean.TRUE);
        boolean animate = Perf.pageAnim(body.getContext());
        turn(arrow, 90f, animate);

        if (!animate) {
            body.setVisibility(View.VISIBLE);
            body.setAlpha(1f);
            return;
        }

        // how tall will it be?
        ViewGroup parent = (ViewGroup) body.getParent();
        int w = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
        body.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int target = body.getMeasuredHeight();

        ViewGroup.LayoutParams lp = body.getLayoutParams();
        lp.height = 0;
        body.setAlpha(0f);
        body.setVisibility(View.VISIBLE);

        ValueAnimator a = ValueAnimator.ofInt(0, target);
        a.setDuration(Motion.ms(body.getContext(), BASE_MS));
        a.setInterpolator(Motion.STANDARD);
        body.setLayerType(View.LAYER_TYPE_HARDWARE, null);   // the content moves as one picture
        a.addUpdateListener(an -> {
            lp.height = (int) an.getAnimatedValue();
            body.setAlpha(Math.min(1f, an.getAnimatedFraction() * 1.6f));   // shows up a little early
            body.setLayoutParams(lp);
        });
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator an) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;   // follows the content again
                body.setAlpha(1f);
                body.setLayerType(View.LAYER_TYPE_NONE, null);
                body.setLayoutParams(lp);
                body.setTag(R.id.expandAnim, null);
            }
        });
        body.setTag(R.id.expandAnim, a);
        a.start();
    }

    public static void close(View body, View arrow) {
        stop(body);
        body.setTag(R.id.expandOpen, Boolean.FALSE);
        boolean animate = Perf.pageAnim(body.getContext());
        turn(arrow, 0f, animate);

        if (!animate || body.getVisibility() != View.VISIBLE) {
            body.setVisibility(View.GONE);
            return;
        }

        ViewGroup.LayoutParams lp = body.getLayoutParams();
        int start = body.getHeight();

        ValueAnimator a = ValueAnimator.ofInt(start, 0);
        a.setDuration(Motion.ms(body.getContext(), BASE_MS));
        a.setInterpolator(Motion.STANDARD);
        body.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        a.addUpdateListener(an -> {
            lp.height = (int) an.getAnimatedValue();
            body.setAlpha(Math.max(0f, 1f - an.getAnimatedFraction() * 1.6f));   // gone a little early
            body.setLayoutParams(lp);
        });
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator an) {
                body.setVisibility(View.GONE);
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                body.setAlpha(1f);
                body.setLayerType(View.LAYER_TYPE_NONE, null);
                body.setLayoutParams(lp);
                body.setTag(R.id.expandAnim, null);
            }
        });
        body.setTag(R.id.expandAnim, a);
        a.start();
    }

    // an animation that is still running is stopped where it is
    private static void stop(View body) {
        Object o = body.getTag(R.id.expandAnim);
        if (o instanceof ValueAnimator) {
            ((ValueAnimator) o).removeAllListeners();
            ((ValueAnimator) o).cancel();
        }
        body.setTag(R.id.expandAnim, null);
        body.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    private static void turn(View arrow, float degrees, boolean animate) {
        if (arrow == null) return;
        if (animate) {
            arrow.animate().rotation(degrees)
                    .setDuration(Motion.ms(arrow.getContext(), BASE_MS))
                    .setInterpolator(Motion.STANDARD).start();
        } else {
            arrow.setRotation(degrees);
        }
    }
}
