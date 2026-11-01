package com.example.nds;

import android.content.Context;
import android.widget.RemoteViews;

import java.util.List;

// total screen time today + how much of the limits is used
public class ScreenTimeWidget extends NdsWidget {

    @Override
    RemoteViews build(Context c, int id) {
        UsageRepository.Snapshot s = UsageRepository.snapshot(c);

        long total = 0;
        for (Long v : s.millis.values()) total += v;

        long used = 0;
        long limit = 0;
        List<AppUsage> list = UsageRepository.usageList(c, s);
        for (AppUsage u : list) {
            used += u.usedMs;
            limit += u.limitMin * 60000L;
        }
        int pct = limit <= 0 ? 0 : (int) Math.min(100, used * 100 / limit);
        String limitsText = limit <= 0 ? "No limits set" : "Limits used " + pct + "%";

        RemoteViews v;
        int size = sizeClass(c, id);
        if (size == MINI) {
            v = views(c, R.layout.widget_mini);
            txt(v, R.id.wMiniValue, durTight(total));
            txt(v, R.id.wMiniLabel, "SCREEN");
        } else if (size == STRIP) {
            v = views(c, R.layout.widget_strip);
            txt(v, R.id.wStripLabel, "SCREEN TIME TODAY");
            txt(v, R.id.wStripSub, limitsText);
            txt(v, R.id.wStripValue, dur(total));
        } else {
            long[] cat = UsageRepository.categoryMillis(c, s);
            int h = sizeDp(c, id)[1];
            v = views(c, R.layout.widget_screen_time);
            txt(v, R.id.wTotal, dur(total));
            prog(v, R.id.wBar, pct);
            txt(v, R.id.wSub, limit <= 0 ? limitsText
                    : limitsText + "  ·  Distracting " + dur(cat[CategoryStore.DISTRACTING]));
            boolean tall = h >= 170;
            show(v, R.id.wSub2, tall);
            if (tall) {
                txt(v, R.id.wSub2, "Productive " + dur(cat[CategoryStore.PRODUCTIVE])
                        + "  ·  Others " + dur(cat[CategoryStore.OTHER]));
                v.setTextViewTextSize(R.id.wTotal, android.util.TypedValue.COMPLEX_UNIT_SP, 34);
            }
        }
        v.setOnClickPendingIntent(R.id.widgetRoot, openApp(c, 100, 0, false));
        return v;
    }
}
