package com.example.nds;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class UsageRepository {

    private static final String[] PKGS = {
            "com.instagram.android",
            "com.google.android.youtube",
            "com.twitter.android"
    };
    private static final String[] NAMES = {"Instagram", "YouTube", "X"};
    private static final int[] DEFAULTS = {60, 45, 30}; // minutes, used until user edits

    public static List<AppUsage> todayUsage(Context ctx) {
        UsageStatsManager usm =
                (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);

        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        Map<String, UsageStats> stats =
                usm.queryAndAggregateUsageStats(c.getTimeInMillis(), System.currentTimeMillis());

        List<AppUsage> out = new ArrayList<>();
        for (int i = 0; i < PKGS.length; i++) {
            UsageStats s = stats.get(PKGS[i]);
            int min = (s == null) ? 0 : (int) (s.getTotalTimeInForeground() / 60000);
            int limit = LimitStore.getLimit(ctx, PKGS[i], DEFAULTS[i]);
            out.add(new AppUsage(PKGS[i], NAMES[i], min, limit));
        }
        Collections.sort(out, (a, b) -> b.usedMin - a.usedMin);
        return out;
    }

    public static String formatMinutes(int min) {
        if (min < 60) return min + "m";
        int h = min / 60;
        int m = min % 60;
        return (m == 0) ? h + "h" : h + "h " + m + "m";
    }
}