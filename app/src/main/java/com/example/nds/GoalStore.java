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

    // all the goals, read once and kept (the guard asks for them every second)
    private static volatile Map<String, ?> cache = null;

    private static Map<String, ?> all(Context c) {
        Map<String, ?> m = cache;
        if (m == null) {
            m = new java.util.HashMap<>(sp(c).getAll());
            cache = m;
        }
        return m;
    }

    private static int intOf(Context c, String key, int def) {
        Object o = all(c).get(key);
        return o instanceof Integer ? (Integer) o : def;
    }

    // every change goes through here: the preferences are saved and the cache is dropped
    private static SharedPreferences.Editor edit(Context c) {
        cache = null;
        return sp(c).edit();
    }

    // ---------- screen time, per weekday (0 = Monday ... 6 = Sunday) ----------

    public static int screenMin(Context c, int day) {
        return intOf(c, "screen_" + day, 0);
    }

    public static void setScreenMin(Context c, int day, int min) {
        min = Math.max(0, Math.min(MAX_SCREEN_MIN, min));
        edit(c).putInt("screen_" + day, min).apply();
    }

    public static void setScreenAll(Context c, int min) {
        min = Math.max(0, Math.min(MAX_SCREEN_MIN, min));
        SharedPreferences.Editor e = edit(c);
        for (int d = 0; d < 7; d++) e.putInt("screen_" + d, min);
        e.apply();
    }

    // today's goal in minutes (0 = none)
    public static int screenTodayMin(Context c) {
        return screenMin(c, StudyScheduler.todayIndex());
    }

    // ---------- per app: ONE goal, either "opens per day" or "screen time per day" ----------

    public static final int TYPE_OPENS = 0;
    public static final int TYPE_TIME = 1;

    public static int appType(Context c, String pkg) {
        return intOf(c, "apptype_" + pkg, TYPE_OPENS);
    }

    public static void setAppType(Context c, String pkg, int type) {
        edit(c).putInt("apptype_" + pkg, type).apply();
    }

    // minutes per day (0 = none)
    public static int appTimeMin(Context c, String pkg) {
        return intOf(c, "apptime_" + pkg, 0);
    }

    public static void setAppTimeMin(Context c, String pkg, int min) {
        min = Math.max(0, Math.min(MAX_SCREEN_MIN, min));
        SharedPreferences.Editor e = edit(c);
        if (min == 0) e.remove("apptime_" + pkg);
        else e.putInt("apptime_" + pkg, min);
        e.apply();
    }

    // Phone, Contacts, the home screen, system parts and N.D.S itself can have a goal (a warning)
    // but must never be locked: that would block emergency calls or the phone itself
    public static boolean canLock(Context c, String pkg) {
        if (pkg.equals(c.getPackageName())) return false;
        if (pkg.equals(SystemApps.dialer(c)) || pkg.equals(SystemApps.contacts(c))
                || pkg.equals(SystemApps.home(c))) return false;
        return !java.util.Arrays.asList(SystemApps.ALWAYS_ALLOWED).contains(pkg);
    }

    // lock the app for the rest of the day when its goal is reached (on by default)
    public static boolean lock(Context c, String pkg) {
        Object o = all(c).get("lock_" + pkg);
        return o instanceof Boolean ? (Boolean) o : true;
    }

    public static void setLock(Context c, String pkg, boolean on) {
        edit(c).putBoolean("lock_" + pkg, on).apply();
    }

    // the goal that counts for this app: the one of the chosen type (0 = no goal)
    public static int activeOpens(Context c, String pkg) {
        return appType(c, pkg) == TYPE_OPENS ? opens(c, pkg) : 0;
    }

    public static int activeTimeMin(Context c, String pkg) {
        return appType(c, pkg) == TYPE_TIME ? appTimeMin(c, pkg) : 0;
    }

    // every app that has a goal (of either type)
    public static Set<String> goalApps(Context c) {
        Set<String> out = new HashSet<>();
        for (Map.Entry<String, ?> e : all(c).entrySet()) {
            String k = e.getKey();
            if (k.startsWith("opens_")) out.add(k.substring(6));
            else if (k.startsWith("apptime_")) out.add(k.substring(8));
        }
        return out;
    }

    // ---------- opens per day, per app ----------

    public static int opens(Context c, String pkg) {
        return intOf(c, "opens_" + pkg, 0);
    }

    public static void setOpens(Context c, String pkg, int n) {
        n = Math.max(0, Math.min(MAX_OPENS, n));
        SharedPreferences.Editor e = edit(c);
        if (n == 0) e.remove("opens_" + pkg);
        else e.putInt("opens_" + pkg, n);
        e.apply();
    }
}
