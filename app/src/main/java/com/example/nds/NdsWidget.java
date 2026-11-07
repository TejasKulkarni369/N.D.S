package com.example.nds;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base of every N.D.S widget.
 *  - the widget picks a layout from the size the person gave it: FULL, STRIP (one row) or MINI (1x1)
 *  - a redraw is sent to the launcher only when something on the widget really changed
 */
public abstract class NdsWidget extends AppWidgetProvider {

    static final int FULL = 0, STRIP = 1, MINI = 2;

    // what each widget showed the last time (not sent again when it is the same)
    private static final Map<Integer, String> LAST = new ConcurrentHashMap<>();
    private final StringBuilder sig = new StringBuilder();

    abstract RemoteViews build(Context c, int widgetId);

    final void refresh(Context c, AppWidgetManager m, int[] ids, boolean force) {
        for (int id : ids) {
            try {
                sig.setLength(0);
                RemoteViews v = build(c, id);
                String key = sig.toString();
                if (!force && key.equals(LAST.get(id))) continue;   // same as before: nothing to send
                LAST.put(id, key);
                m.updateAppWidget(id, v);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        refresh(c, m, ids, true);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        refresh(c, m, new int[]{id}, true);   // resized: draw the layout that fits
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        for (int id : ids) LAST.remove(id);
    }

    // ---------- small helpers that also remember what was set ----------

    RemoteViews views(Context c, int layout) {
        sig.append('L').append(layout).append('|');
        return new RemoteViews(c.getPackageName(), layout);
    }

    void txt(RemoteViews v, int id, String s) {
        v.setTextViewText(id, s);
        sig.append(id).append('=').append(s).append('|');
    }

    void color(RemoteViews v, int id, int argb) {
        v.setTextColor(id, argb);
        sig.append(id).append('c').append(argb).append('|');
    }

    void prog(RemoteViews v, int id, int pct) {
        v.setProgressBar(id, 100, pct, false);
        sig.append(id).append('%').append(pct).append('|');
    }

    void show(RemoteViews v, int id, boolean on) {
        v.setViewVisibility(id, on ? View.VISIBLE : View.GONE);
        sig.append(id).append(on ? '+' : '-').append('|');
    }

    void note(String s) {
        sig.append(s).append('|');
    }

    // ---------- size ----------

    // the size the person gave this widget, in dp: {width, height}
    static int[] sizeDp(Context c, int id) {
        try {
            Bundle o = AppWidgetManager.getInstance(c).getAppWidgetOptions(id);
            int w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            int h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
            return new int[]{w > 0 ? w : 110, h > 0 ? h : 110};
        } catch (Exception e) {
            return new int[]{110, 110};
        }
    }

    // 1 cell wide = MINI, one row high = STRIP, bigger = FULL
    static int sizeClass(Context c, int id) {
        int[] s = sizeDp(c, id);
        if (s[0] < 100) return MINI;
        return s[1] < 85 ? STRIP : FULL;
    }

    static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    // how many two-line rows fit in a list widget of this height (1 to 5)
    static int listRows(int hDp) {
        return Math.max(1, Math.min(5, (hDp - 38) / 33));
    }

    // ---------- taps ----------

    // opens N.D.S on a tab (0 Home, 1 Limits, 2 Tasks, 3 Time Table), or the Focus question
    static PendingIntent openApp(Context c, int request, int tab, boolean focus) {
        Intent i = new Intent(c, MainActivity.class);
        i.putExtra("tab", tab);
        i.putExtra("focus", focus);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, request, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // 3h 12m, 45m, <1m
    static String dur(long ms) {
        if (ms > 0 && ms < 60000) return "<1m";
        return UsageRepository.formatMinutes((int) (ms / 60000));
    }

    // 3h12m (for the very small widget)
    static String durTight(long ms) {
        return dur(ms).replace(" ", "");
    }
}
