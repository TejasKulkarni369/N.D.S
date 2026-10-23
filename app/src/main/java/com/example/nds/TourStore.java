package com.example.nds;

import android.content.Context;
import android.content.SharedPreferences;

public class TourStore {

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("tour", Context.MODE_PRIVATE);
    }

    public static boolean isDone(Context c) {
        return sp(c).getBoolean("done", false);
    }

    public static void markDone(Context c) {
        sp(c).edit().putBoolean("done", true).apply();
    }

    public static void reset(Context c) {
        sp(c).edit().putBoolean("done", false).apply();
    }
}