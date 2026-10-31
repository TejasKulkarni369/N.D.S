package com.example.nds;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;

// base of every N.D.S widget: build() draws one widget, the rest is shared
public abstract class NdsWidget extends AppWidgetProvider {

    abstract RemoteViews build(Context c, int widgetId);

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) {
            try {
                m.updateAppWidget(id, build(c, id));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        onUpdate(c, m, new int[]{id});
    }

    // ---------- helpers ----------

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
}
