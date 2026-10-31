package com.example.nds;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;

public class WidgetUpdater {

    @SuppressWarnings("unchecked")
    private static final Class<? extends NdsWidget>[] ALL = new Class[]{
            ScreenTimeWidget.class, TasksWidget.class, TimeTableWidget.class,
            FocusWidget.class, AppTimeWidget.class
    };

    // redraw every widget that is on the home screen (cheap when there are none)
    public static void updateAll(Context c) {
        for (Class<? extends NdsWidget> cls : ALL) update(c, cls);
    }

    public static void update(Context c, Class<? extends NdsWidget> cls) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, cls));
            if (ids == null || ids.length == 0) return;
            cls.getDeclaredConstructor().newInstance().onUpdate(c, m, ids);
        } catch (Exception ignored) {
        }
    }
}
