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
        long[] cat = UsageRepository.categoryMillis(c, s);

        long used = 0;
        long limit = 0;
        List<AppUsage> list = UsageRepository.usageList(c, s);
        for (AppUsage u : list) {
            used += u.usedMs;
            limit += u.limitMin * 60000L;
        }
        int pct = limit <= 0 ? 0 : (int) Math.min(100, used * 100 / limit);

        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_screen_time);
        v.setTextViewText(R.id.wTotal, dur(total));
        v.setProgressBar(R.id.wBar, 100, pct, false);
        v.setTextViewText(R.id.wSub, limit <= 0
                ? "No limits set"
                : "Limits used " + pct + "%  ·  Distracting " + dur(cat[CategoryStore.DISTRACTING]));
        v.setOnClickPendingIntent(R.id.widgetRoot, openApp(c, 100, 0, false));
        return v;
    }
}
