package com.example.nds;

import android.content.Context;
import android.text.format.DateFormat;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// the next pending tasks
public class TasksWidget extends NdsWidget {

    private static final int[] TITLE = {R.id.wRow1, R.id.wRow2, R.id.wRow3, R.id.wRow4, R.id.wRow5};
    private static final int[] SUB = {R.id.wSub1, R.id.wSub2, R.id.wSub3, R.id.wSub4, R.id.wSub5};

    @Override
    RemoteViews build(Context c, int id) {
        List<Task> pending = new ArrayList<>();
        for (Task t : TaskStore.load(c)) {
            if (!t.done) pending.add(t);
        }
        long now = System.currentTimeMillis();
        int size = sizeClass(c, id);
        RemoteViews v;

        if (size == MINI) {
            v = views(c, R.layout.widget_mini);
            txt(v, R.id.wMiniValue, String.valueOf(pending.size()));
            txt(v, R.id.wMiniLabel, "TASKS");

        } else if (size == STRIP) {
            v = views(c, R.layout.widget_strip);
            if (pending.isEmpty()) {
                txt(v, R.id.wStripLabel, "TASKS");
                txt(v, R.id.wStripSub, "No pending tasks");
                txt(v, R.id.wStripValue, "");
            } else {
                Task t = pending.get(0);
                txt(v, R.id.wStripLabel, "NEXT TASK  ·  " + pending.size() + " pending");
                txt(v, R.id.wStripSub, t.title);
                txt(v, R.id.wStripValue, shortWhen(c, t.timeMs));
                color(v, R.id.wStripValue, t.timeMs < now ? 0xFFFF6B6B : 0xFFFF7A1A);
            }

        } else {
            v = views(c, R.layout.widget_list);
            txt(v, R.id.wTitle, "TASKS");
            txt(v, R.id.wCount, pending.isEmpty() ? "" : pending.size() + " pending");

            int rows = listRows(sizeDp(c, id)[1]);   // as many tasks as the widget is tall
            for (int i = 0; i < 5; i++) {
                boolean on = i < rows && i < pending.size();
                show(v, TITLE[i], on);
                show(v, SUB[i], on);
                if (!on) continue;
                Task t = pending.get(i);
                boolean late = t.timeMs < now;
                txt(v, TITLE[i], t.title);
                txt(v, SUB[i], (late ? "Overdue · " : "") + when(c, t.timeMs));
                color(v, SUB[i], late ? 0xFFFF6B6B : 0xB3FFFFFF);
            }
            show(v, R.id.wEmpty, pending.isEmpty());
            txt(v, R.id.wEmpty, "No pending tasks");
        }

        v.setOnClickPendingIntent(R.id.widgetRoot, openApp(c, 102, 2, false));
        return v;
    }

    // 6:00 PM today, otherwise Tom / Mon
    private static String shortWhen(Context c, long ms) {
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(ms);
        Calendar now = Calendar.getInstance();
        if (sameDay(t, now)) return DateFormat.getTimeFormat(c).format(new Date(ms));
        now.add(Calendar.DAY_OF_YEAR, 1);
        if (sameDay(t, now)) return "Tomorrow";
        return new SimpleDateFormat("EEE d", Locale.getDefault()).format(new Date(ms));
    }

    private static String when(Context c, long ms) {
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(ms);
        Calendar now = Calendar.getInstance();
        String time = DateFormat.getTimeFormat(c).format(new Date(ms));

        if (sameDay(t, now)) return "Today " + time;
        now.add(Calendar.DAY_OF_YEAR, 1);
        if (sameDay(t, now)) return "Tomorrow " + time;
        return new SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(new Date(ms))
                + " " + time;
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
