package com.example.nds;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FocusStore {

    public static final int MAX_HOURS = 24;
    public static final int APP_COUNT = 5;

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("focus", Context.MODE_PRIVATE);
    }

    public static void start(Context c, long durationMs, List<String> pkgs) {
        long now = System.currentTimeMillis();
        sp(c).edit()
                .putLong("startAt", now)
                .putLong("endAt", now + durationMs)
                .putString("apps", TextUtils.join(",", pkgs))
                .apply();
    }

    public static boolean isActive(Context c) {
        return System.currentTimeMillis() < sp(c).getLong("endAt", 0);
    }

    public static long remainingMs(Context c) {
        return Math.max(0, sp(c).getLong("endAt", 0) - System.currentTimeMillis());
    }

    public static long totalMs(Context c) {
        return Math.max(1, sp(c).getLong("endAt", 0) - sp(c).getLong("startAt", 0));
    }

    // ends Focus right now (used by the Emergency Exit)
    public static void exitNow(Context c) {
        sp(c).edit().putLong("endAt", 0).putBoolean("exited", true).apply();
    }

    // true once after an Emergency Exit, so "Focus complete" is not shown
    public static boolean consumeExitFlag(Context c) {
        boolean exited = sp(c).getBoolean("exited", false);
        if (exited) sp(c).edit().putBoolean("exited", false).apply();
        return exited;
    }

    // the 5 emergency apps
    public static List<String> apps(Context c) {
        String s = sp(c).getString("apps", "");
        List<String> out = new ArrayList<>();
        if (!s.isEmpty()) out.addAll(Arrays.asList(s.split(",")));
        return out;
    }

    // everything that may stay on the screen during Focus
    public static Set<String> allowed(Context c) {
        Set<String> s = new HashSet<>(apps(c));
        s.add(c.getPackageName());
        String dialer = SystemApps.dialer(c);
        if (dialer != null) s.add(dialer);
        String contacts = SystemApps.contacts(c);
        if (contacts != null) s.add(contacts);
        s.addAll(Arrays.asList(SystemApps.ALWAYS_ALLOWED));
        return s;
    }
}