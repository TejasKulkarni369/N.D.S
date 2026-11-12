package com.example.nds;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;
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

    private static boolean systemOn = true;
    private static long systemCheckedAt = 0;

    // the time of an animation: full in Quality, a quarter shorter in Balanced
    public static long ms(Context c, long base) {
        return Perf.mode(c) == Perf.QUALITY ? base : (long) (base * 0.75f);
    }

    // false in Performance, or when the person switched animations off in Android
    // (Settings > Accessibility > Remove animations)
    public static boolean on(Context c) {
        return Perf.pageAnim(c) && systemAnimationsOn(c);
    }

    private static boolean systemAnimationsOn(Context c) {
        long now = SystemClock.uptimeMillis();
        if (now - systemCheckedAt > 3000) {   // asked at most every 3 seconds
            systemCheckedAt = now;
            try {
                systemOn = Settings.Global.getFloat(c.getContentResolver(),
                        Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
            } catch (Exception e) {
                systemOn = true;
            }
        }
        return systemOn;
    }

    // how long to wait between two frames of a slow, endless animation (the glow).
    // It moves so slowly that 60 frames a second is wasted work on every phone.
    public static long frameDelay(Context c) {
        int tier = Perf.tier(c);
        return tier == Perf.HIGH ? 22 : (tier == Perf.MID ? 30 : 40);   // about 45 / 33 / 25 fps
    }
}
