package com.example.nds;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Goals (a softer thing than a limit: N.D.S warns, it does not lock).
 *  - a screen time goal for each day of the week (minutes, 0 = no goal)
 *  - an "opens per day" goal for any app (0 = no goal)
 */
public class GoalStore {

    public static final int MAX_SCREEN_MIN = 12 * 60;
    public static final int MAX_OPENS = 200;

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("goals", Context.MODE_PRIVATE);
    }

    // ---------- screen time, per weekday (0 = Monday ... 6 = Sunday) ----------

    public static int screenMin(Context c, int day) {
        return sp(c).getInt("screen_" + day, 0);
    }

    public static void setScreenMin(Context c, int day, int min) {
        min = Math.max(0, Math.min(MAX_SCREEN_MIN, min));
        sp(c).edit().putInt("screen_" + day, min).apply();
    }

    public static void setScreenAll(Context c, int min) {
        min = Math.max(0, Math.min(MAX_SCREEN_MIN, min));
        SharedPreferences.Editor e = sp(c).edit();
        for (int d = 0; d < 7; d++) e.putInt("screen_" + d, min);
        e.apply();
    }

    // today's goal in minutes (0 = none)
    public static int screenTodayMin(Context c) {
        return screenMin(c, StudyScheduler.todayIndex());
    }

    // ---------- opens per day, per app ----------

    public static int opens(Context c, String pkg) {
        return sp(c).getInt("opens_" + pkg, 0);
    }

    public static void setOpens(Context c, String pkg, int n) {
        n = Math.max(0, Math.min(MAX_OPENS, n));
        SharedPreferences.Editor e = sp(c).edit();
        if (n == 0) e.remove("opens_" + pkg);
        else e.putInt("opens_" + pkg, n);
        e.apply();
    }

    // every app that has an opens goal
    public static Set<String> opensApps(Context c) {
        Set<String> out = new HashSet<>();
        for (Map.Entry<String, ?> e : sp(c).getAll().entrySet()) {
            if (e.getKey().startsWith("opens_")) out.add(e.getKey().substring(6));
        }
        return out;
    }
}
