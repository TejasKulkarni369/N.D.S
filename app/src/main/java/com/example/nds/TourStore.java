package com.example.nds;

import android.content.Context;
import android.content.SharedPreferences;

public class TourStore {

    // raise this by 1 whenever you add feature cards to TourView (see allSteps)
    public static final int CURRENT = 2;

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("tour", Context.MODE_PRIVATE);
    }

    // the tour version this phone has seen: 0 = never.
    // A phone that finished the first tour before versions existed counts as version 1.
    public static int seenVersion(Context c) {
        SharedPreferences p = sp(c);
        return p.getInt("seen", p.getBoolean("done", false) ? 1 : 0);
    }

    // true when there is nothing new to show
    public static boolean isDone(Context c) {
        return seenVersion(c) >= CURRENT;
    }

    public static void markDone(Context c) {
        sp(c).edit().putInt("seen", CURRENT).putBoolean("done", true).apply();
    }

    // show the whole tour again
    public static void reset(Context c) {
        sp(c).edit().putInt("seen", 0).putBoolean("done", false).apply();
    }
}
