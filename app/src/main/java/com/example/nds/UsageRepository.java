package com.example.nds;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UsageRepository {

    private static Set<String> launchable = null;
    private static String homePkg = null;
    private static long cachedAt = 0;
    private static final Map<String, String> labels = new HashMap<>();

    public static class Snapshot {
        public final Map<String, Long> millis;     // today's screen time per app
        public final Map<String, Integer> opens;   // times opened today per app
        public final String foreground;            // app on screen right now (null = none)

        Snapshot(Map<String, Long> millis, Map<String, Integer> opens, String foreground) {
            this.millis = millis;
            this.opens = opens;
            this.foreground = foreground;
        }
    }

    // first limit for an app that just became Distracting
    private static int defaultLimit(String pkg) {
        if (pkg.equals("com.instagram.android")) return 60;
        if (pkg.equals("com.google.android.youtube")) return 45;
        return 30;
    }

    @SuppressWarnings("deprecation")
    private static synchronized void refreshCache(Context ctx) {
        long now = System.currentTimeMillis();
        if (launchable != null && now - cachedAt < 5 * 60000L) return;

        PackageManager pm = ctx.getPackageManager();
        Set<String> set = new HashSet<>();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            set.add(ri.activityInfo.packageName);
        }
        set.remove(ctx.getPackageName());

        ResolveInfo home = pm.resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY);
        homePkg = (home == null) ? null : home.activityInfo.packageName;

        launchable = set;
        cachedAt = now;
    }

    private static boolean isInstalled(String pkg) {
        return launchable != null && launchable.contains(pkg);
    }

    private static boolean counts(String pkg) {
        return isInstalled(pkg) && !pkg.equals(homePkg);
    }

    @SuppressWarnings("deprecation")
    public static synchronized String labelFor(Context ctx, String pkg) {
        String label = labels.get(pkg);
        if (label == null) {
            try {
                PackageManager pm = ctx.getPackageManager();
                label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
            } catch (PackageManager.NameNotFoundException e) {
                label = pkg;
            }
            labels.put(pkg, label);
        }
        return label;
    }

    private static void close(Map<String, Long> total, String pkg, long start, long end) {
        if (pkg != null && end > start && counts(pkg)) {
            Long old = total.get(pkg);
            total.put(pkg, (old == null ? 0L : old) + (end - start));
        }
    }

    @SuppressWarnings("deprecation")
    public static Snapshot snapshot(Context ctx) {
        refreshCache(ctx);
        UsageStatsManager usm =
                (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);

        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long now = System.currentTimeMillis();

        Map<String, Long> total = new HashMap<>();
        Map<String, Integer> opens = new HashMap<>();
        UsageEvents events = usm.queryEvents(c.getTimeInMillis(), now);
        if (events == null) return new Snapshot(total, opens, null);

        UsageEvents.Event e = new UsageEvents.Event();
        String cur = null;
        long curStart = 0;
        String lastPausedPkg = null;
        long lastPausedAt = 0;

        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            int type = e.getEventType();
            long t = e.getTimeStamp();

            if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                String pkg = e.getPackageName();
                if (!pkg.equals(cur)) {
                    close(total, cur, curStart, t);
                    // moving between screens inside one app is not a new "open"
                    boolean quickReturn = pkg.equals(lastPausedPkg) && t - lastPausedAt < 1500;
                    if (!quickReturn && counts(pkg)) {
                        Integer n = opens.get(pkg);
                        opens.put(pkg, n == null ? 1 : n + 1);
                    }
                    cur = pkg;
                    curStart = t;
                }
            } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                String pkg = e.getPackageName();
                if (pkg.equals(cur)) {
                    close(total, cur, curStart, t);
                    cur = null;
                }
                lastPausedPkg = pkg;
                lastPausedAt = t;
            } else if (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE
                    || type == UsageEvents.Event.DEVICE_SHUTDOWN) {
                close(total, cur, curStart, t);
                cur = null;
            }
        }

        String foreground = cur;
        close(total, cur, curStart, now);   // app still on screen
        return new Snapshot(total, opens, foreground);
    }

    public static int opensFor(Snapshot s, String pkg) {
        if (s == null) return 0;
        Integer n = s.opens.get(pkg);
        return n == null ? 0 : n;
    }

    // only apps tagged Distracting have a limit; everything else returns null
    public static AppUsage usageFor(Context ctx, Snapshot s, String pkg) {
        if (CategoryStore.get(ctx, pkg) != CategoryStore.DISTRACTING) return null;
        Long ms = s.millis.get(pkg);
        int limit = LimitStore.getLimit(ctx, pkg, defaultLimit(pkg))
                + BypassStore.bonus(ctx, pkg);
        return new AppUsage(pkg, labelFor(ctx, pkg), ms == null ? 0L : ms, limit);
    }

    public static List<AppUsage> usageList(Context ctx, Snapshot s) {
        List<AppUsage> out = new ArrayList<>();
        for (String pkg : CategoryStore.distractingPackages(ctx)) {
            if (!isInstalled(pkg)) continue;
            AppUsage u = usageFor(ctx, s, pkg);
            if (u != null) out.add(u);
        }
        Collections.sort(out, (a, b) -> Long.compare(b.usedMs, a.usedMs));
        return out;
    }

    public static List<AppUsage> todayUsage(Context ctx) {
        return usageList(ctx, snapshot(ctx));
    }

    // apps whose limit is used up today
    public static Set<String> lockedPackages(Context ctx) {
        Set<String> out = new HashSet<>();
        for (AppUsage u : usageList(ctx, snapshot(ctx))) {
            if (u.usedMin >= u.limitMin) out.add(u.pkg);
        }
        return out;
    }

    // [0] = others, [1] = productive, [2] = distracting (milliseconds)
    public static long[] categoryMillis(Context ctx, Snapshot s) {
        long[] out = new long[3];
        for (Map.Entry<String, Long> en : s.millis.entrySet()) {
            out[CategoryStore.get(ctx, en.getKey())] += en.getValue();
        }
        return out;
    }

    public static String formatMinutes(int min) {
        if (min < 60) return min + "m";
        int h = min / 60;
        int m = min % 60;
        return (m == 0) ? h + "h" : h + "h " + m + "m";
    }
}