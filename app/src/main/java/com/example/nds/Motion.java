package com.example.nds;

import android.content.Context;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/**
 * The one set of curves and times every animation in N.D.S uses, so they all feel the same.
 *  STANDARD: starts fast and lands very softly (moving things)
 *  DECEL:    things that appear
 *  ACCEL:    things that leave
 */
public class Motion {

    public static final Interpolator STANDARD = new PathInterpolator(0.2f, 0f, 0f, 1f);
    public static final Interpolator DECEL = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    public static final Interpolator ACCEL = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);

    // the time of an animation: full in Quality, a quarter shorter in Balanced
    public static long ms(Context c, long base) {
        return Perf.mode(c) == Perf.QUALITY ? base : (long) (base * 0.75f);
    }

    // false in Performance: nothing is animated
    public static boolean on(Context c) {
        return Perf.pageAnim(c);
    }
}
