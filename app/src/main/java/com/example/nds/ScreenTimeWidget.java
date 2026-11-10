package com.example.nds;

import android.content.Context;
import android.util.TypedValue;
import android.widget.RemoteViews;

import java.util.List;

// total screen time today, with a ring that shows how much of the goal is used.
// The goal = the daily limits of your Distracting apps added together.
public class ScreenTimeWidget extends NdsWidget {

    @Override
    RemoteViews build(Context c, int id) {
        UsageRepository.Snapshot s = UsageRepository.snapshot(c);

        long total = 0;
        for (Long v : s.millis.values()) total += v;

        long used = 0;
        long goal = 0;
        List<AppUsage> list = UsageRepository.usageList(c, s);
        for (AppUsage u : list) {
            used += u.usedMs;
            goal += u.limitMin * 60000L;
        }

        // a screen time goal for today (Settings > Goals) comes first; without one the
        // limits of the Distracting apps are the goal
        int todayGoal = GoalStore.screenTodayMin(c);
        if (todayGoal > 0) {
            used = total;
            goal = todayGoal * 60000L;
        }
        boolean hasGoal = goal > 0;
        int pct = !hasGoal ? 0 : (int) Math.min(100, used * 100 / goal);
        boolean hot = hasGoal && pct >= 90;          // the ring turns red near the goal
        String pctText = hasGoal ? pct + "%" : "–";
        String goalText = hasGoal ? "of " + dur(goal) + " goal" : "No goal set";

        RemoteViews v;
        int size = sizeClass(c, id);
        if (size == MINI) {
            v = views(c, R.layout.widget_mini_ring);
            txt(v, R.id.wMiniValue, durTight(total));
            ring(v, pct, hot);

        } else if (size == STRIP) {
            v = views(c, R.layout.widget_strip_ring);
            txt(v, R.id.wRingText, pctText);
            txt(v, R.id.wStripLabel, "SCREEN TIME TODAY");
            txt(v, R.id.wStripSub, goalText);
            txt(v, R.id.wStripValue, dur(total));
            ring(v, pct, hot);

        } else {
            long[] cat = UsageRepository.categoryMillis(c, s);
            int h = sizeDp(c, id)[1];
            v = views(c, R.layout.widget_screen_time);
            txt(v, R.id.wRingText, pctText);
            txt(v, R.id.wTotal, dur(total));
            txt(v, R.id.wSub, hasGoal
                    ? goalText + "  ·  Distracting " + dur(cat[CategoryStore.DISTRACTING])
                    : goalText);
            boolean tall = h >= 170;
            show(v, R.id.wSub2, tall);
            if (tall) {
                txt(v, R.id.wSub2, "Productive " + dur(cat[CategoryStore.PRODUCTIVE])
                        + "  ·  Others " + dur(cat[CategoryStore.OTHER]));
                v.setTextViewTextSize(R.id.wTotal, TypedValue.COMPLEX_UNIT_SP, 30);
            }
            ring(v, pct, hot);
        }
        v.setOnClickPendingIntent(R.id.widgetRoot, openApp(c, 100, 0, false));
        return v;
    }

    // orange ring, or the red one when the goal is almost used
    private void ring(RemoteViews v, int pct, boolean hot) {
        prog(v, R.id.wRing, pct);
        prog(v, R.id.wRingHot, pct);
        show(v, R.id.wRing, !hot);
        show(v, R.id.wRingHot, hot);
    }
}
