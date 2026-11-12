package com.example.nds;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.PowerManager;

public class Perf {

    // Optimizations mode (the person can change it in Settings)
    public static final int QUALITY = 0;
    public static final int BALANCED = 1;
    public static final int PERFORMANCE = 2;

    // how strong this phone is
    public static final int HIGH = 0;
    public static final int MID = 1;
    public static final int LOW = 2;

    private static volatile int cached = -1;
    private static volatile int tierCached = -1;

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    // ---------- the phone ----------

    public static int tier(Context c) {
        int t = tierCached;
        if (t < 0) {
            t = detectTier(c);
            tierCached = t;
        }
        return t;
    }

    private static int detectTier(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return MID;
        if (am.isLowRamDevice()) return LOW;

        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long mb = mi.totalMem / (1024 * 1024);   // a "3 GB" phone reports about 2.8 GB
        if (mb < 3300) return LOW;                // 3 GB and below
        if (mb < 5000) return MID;                // 4 to 5 GB
        return HIGH;                              // 6 GB and above
    }

    // the mode that suits this phone
    public static int recommended(Context c) {
        int t = tier(c);
        return t == LOW ? PERFORMANCE : (t == MID ? BALANCED : QUALITY);
    }

    // ---------- the choice ----------

    public static int mode(Context c) {
        int m = cached;
        if (m < 0) {
            SharedPreferences p = sp(c);
            m = p.getInt("perf", -1);
            if (m < 0) {   // first run: pick what suits this phone
                m = recommended(c);
                p.edit().putInt("perf", m).apply();
            }
            cached = m;
        }
        return m;
    }

    public static void setMode(Context c, int m) {
        cached = m;
        sp(c).edit().putInt("perf", m).apply();
    }

    // ---------- what each mode allows ----------

    // intro animation (app start and Focus start)
    public static boolean intro(Context c) {
        return mode(c) == QUALITY;
    }

    // page swipes and slides
    public static boolean pageAnim(Context c) {
        return mode(c) != PERFORMANCE;
    }

    // moving background glow, breathing ring glow (also off while Battery saver is on)
    public static boolean ambient(Context c) {
        if (mode(c) != QUALITY) return false;
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm == null || !pm.isPowerSaveMode();
    }

    // animated cards (dialogs)
    public static boolean dialogAnim(Context c) {
        return mode(c) == QUALITY;
    }

    // blur behind cards
    public static boolean blur(Context c) {
        return mode(c) != PERFORMANCE;
    }

    // simple one-layer cards, buttons and sheets (lighter to draw)
    public static boolean lite(Context c) {
        return mode(c) != QUALITY;
    }
}
