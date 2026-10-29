package com.example.nds;

import android.content.Context;
import android.content.SharedPreferences;

public class Perf {

    public static final int QUALITY = 0;
    public static final int BALANCED = 1;
    public static final int PERFORMANCE = 2;

    private static volatile int cached = -1;

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public static int mode(Context c) {
        int m = cached;
        if (m < 0) {
            m = sp(c).getInt("perf", QUALITY);
            cached = m;
        }
        return m;
    }

    public static void setMode(Context c, int m) {
        cached = m;
        sp(c).edit().putInt("perf", m).apply();
    }

    // intro animation (app start and Focus start)
    public static boolean intro(Context c) {
        return mode(c) == QUALITY;
    }

    // page swipes and slides
    public static boolean pageAnim(Context c) {
        return mode(c) != PERFORMANCE;
    }

    // moving background glow, breathing ring glow
    public static boolean ambient(Context c) {
        return mode(c) == QUALITY;
    }

    // animated cards (dialogs)
    public static boolean dialogAnim(Context c) {
        return mode(c) == QUALITY;
    }

    // blur behind cards
    public static boolean blur(Context c) {
        return mode(c) != PERFORMANCE;
    }
}