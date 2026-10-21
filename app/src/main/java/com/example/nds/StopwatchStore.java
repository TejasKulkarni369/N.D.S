package com.example.nds;

import android.content.Context;
import android.content.SharedPreferences;

public class StopwatchStore {

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("stopwatch", Context.MODE_PRIVATE);
    }

    // total time counted so far, in milliseconds
    public static long elapsedMs(Context c) {
        SharedPreferences sp = sp(c);
        long acc = sp.getLong("acc", 0);
        long since = sp.getLong("since", 0);
        return (since == 0) ? acc : acc + (System.currentTimeMillis() - since);
    }

    public static boolean isRunning(Context c) {
        return sp(c).getLong("since", 0) != 0;
    }

    public static void start(Context c) {
        if (isRunning(c)) return;
        sp(c).edit().putLong("since", System.currentTimeMillis()).apply();
    }

    public static void pause(Context c) {
        long e = elapsedMs(c);
        sp(c).edit().putLong("acc", e).putLong("since", 0).apply();
    }

    public static void reset(Context c) {
        sp(c).edit().putLong("acc", 0).putLong("since", 0).apply();
    }
}