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
import java.util.Iterator;
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

    // ---- incremental tracking: the whole day is read ONCE, after that only new events ----
    private static final Object TRACK_LOCK = new Object();
    private static long trkDay = 0;                 // midnight this state belongs to
    private static long trkLastTs = 0;              // newest event time already processed
    private static final Map<String, Long> trkDone = new HashMap<>();      // closed sessions today
    private static final Map<String, Integer> trkOpens = new HashMap<>();
    private static String trkCur = null;            // app on screen
    private static long trkCurStart = 0;
    private static String trkPausedPkg = null;
    private static long trkPausedAt = 0;
    private static final Map<String, Long> trkSeen = new HashMap<>();      // dedupe of the 1.5 s overlap

    private static long trkMidnight() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static void trkClose(String pkg, long start, long end) {
        if (pkg != null && end > start && counts(pkg)) {
            Long old = trkDone.get(pkg);
            trkDone.put(pkg, (old == null ? 0L : old) + (end - start));
        }
    }

    @SuppressWarnings("deprecation")
    private static void trkApply(UsageEvents.Event e) {
        int type = e.getEventType();
        long t = e.getTimeStamp();
        String pkg = e.getPackageName();

        if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
            if (pkg == null || pkg.equals(trkCur)) return;
            trkClose(trkCur, trkCurStart, t);
            // moving between screens inside one app is not a new "open"
            boolean quickReturn = pkg.equals(trkPausedPkg) && t - trkPausedAt < 1500;
            if (!quickReturn && counts(pkg)) {
                Integer n = trkOpens.get(pkg);
                trkOpens.put(pkg, n == null ? 1 : n + 1);
            }
            trkCur = pkg;
            trkCurStart = t;
        } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND) {
            if (pkg == null) return;
            if (pkg.equals(trkCur)) {
                trkClose(trkCur, trkCurStart, t);
                trkCur = null;
            }
            trkPausedPkg = pkg;
            trkPausedAt = t;
        } else if (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE
                || type == UsageEvents.Event.DEVICE_SHUTDOWN) {
            trkClose(trkCur, trkCurStart, t);
            trkCur = null;
        }
    }

    @SuppressWarnings("deprecation")
    public static Snapshot snapshot(Context ctx) {
        refreshCache(ctx);
        synchronized (TRACK_LOCK) {
            long now = System.currentTimeMillis();
            long midnight = trkMidnight();

            // new day (or the clock/date changed): start again from midnight
            if (trkDay != midnight) {
                trkDone.clear();
                trkOpens.clear();
                trkSeen.clear();
                trkCur = null;
                trkCurStart = 0;
                trkPausedPkg = null;
                trkPausedAt = 0;
                trkDay = midnight;
                trkLastTs = midnight;
            }

            UsageStatsManager usm =
                    (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);

            // first call: the whole day. After that: only the last moments (1.5 s overlap, deduped)
            long begin = Math.max(trkDay, trkLastTs - 1500);
            UsageEvents events = usm.queryEvents(begin, now);
            if (events != null) {
                UsageEvents.Event e = new UsageEvents.Event();
                while (events.hasNextEvent()) {
                    events.getNextEvent(e);
                    long ts = e.getTimeStamp();
                    String sig = e.getEventType() + "|" + e.getPackageName() + "|" + ts;
                    if (trkSeen.containsKey(sig)) continue;
                    trkSeen.put(sig, ts);
                    trkApply(e);
                    if (ts > trkLastTs) trkLastTs = ts;
                }
            }

            // forget signatures that can no longer come back
            Iterator<Map.Entry<String, Long>> it = trkSeen.entrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getValue() < trkLastTs - 6000) it.remove();
            }

            Map<String, Long> total = new HashMap<>(trkDone);
            if (trkCur != null && counts(trkCur)) {          // app still on screen
                Long old = total.get(trkCur);
                total.put(trkCur, (old == null ? 0L : old) + Math.max(0L, now - trkCurStart));
            }
            return new Snapshot(total, new HashMap<>(trkOpens), trkCur);
        }
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

    public static class DayStat {
        public final long dayStart;   // midnight of that day (ms)
        public int opens = 0;
        public long millis = 0;

        DayStat(long dayStart) {
            this.dayStart = dayStart;
        }
    }

    // times opened and screen time of one app for each of the last `days` days (oldest first)
    @SuppressWarnings("deprecation")
    public static List<DayStat> history(Context ctx, String target, int days) {
        // midnight of every day, plus the midnight after the last one
        long[] bounds = new long[days + 1];
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_YEAR, -(days - 1));
        for (int i = 0; i <= days; i++) {
            bounds[i] = c.getTimeInMillis();
            c.add(Calendar.DAY_OF_YEAR, 1);
        }

        List<DayStat> out = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            out.add(new DayStat(bounds[i]));
        }

        UsageStatsManager usm =
                (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(bounds[0], now);
        if (events == null) return out;

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
                    addSession(out, bounds, target, cur, curStart, t);
                    boolean quickReturn = pkg.equals(lastPausedPkg) && t - lastPausedAt < 1500;
                    if (!quickReturn && pkg.equals(target)) countOpen(out, bounds, t);
                    cur = pkg;
                    curStart = t;
                }
            } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                String pkg = e.getPackageName();
                if (pkg.equals(cur)) {
                    addSession(out, bounds, target, cur, curStart, t);
                    cur = null;
                }
                lastPausedPkg = pkg;
                lastPausedAt = t;
            } else if (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE
                    || type == UsageEvents.Event.DEVICE_SHUTDOWN) {
                addSession(out, bounds, target, cur, curStart, t);
                cur = null;
            }
        }
        addSession(out, bounds, target, cur, curStart, now);   // still on screen
        return out;
    }

    // add one session of the target app to the days it covers
    private static void addSession(List<DayStat> out, long[] bounds, String target,
                                   String pkg, long start, long end) {
        if (pkg == null || !pkg.equals(target) || end <= start) return;
        for (int i = 0; i < out.size(); i++) {
            long from = Math.max(start, bounds[i]);
            long to = Math.min(end, bounds[i + 1]);
            if (to > from) out.get(i).millis += to - from;
        }
    }

    private static void countOpen(List<DayStat> out, long[] bounds, long t) {
        for (int i = 0; i < out.size(); i++) {
            if (t >= bounds[i] && t < bounds[i + 1]) {
                out.get(i).opens++;
                return;
            }
        }
    }

    public static String formatMinutes(int min) {
        if (min < 60) return min + "m";
        int h = min / 60;
        int m = min % 60;
        return (m == 0) ? h + "h" : h + "h " + m + "m";
    }
}