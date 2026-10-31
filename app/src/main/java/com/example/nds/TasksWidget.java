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

    private static final int[] TITLE = {R.id.wRow1, R.id.wRow2, R.id.wRow3};
    private static final int[] SUB = {R.id.wSub1, R.id.wSub2, R.id.wSub3};

    @Override
    RemoteViews build(Context c, int id) {
        List<Task> pending = new ArrayList<>();
        for (Task t : TaskStore.load(c)) {
            if (!t.done) pending.add(t);
        }

        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_list);
        v.setTextViewText(R.id.wTitle, "TASKS");
        v.setTextViewText(R.id.wCount, pending.isEmpty() ? "" : pending.size() + " pending");

        long now = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            if (i < pending.size()) {
                Task t = pending.get(i);
                boolean late = t.timeMs < now;
                v.setViewVisibility(TITLE[i], android.view.View.VISIBLE);
                v.setViewVisibility(SUB[i], android.view.View.VISIBLE);
                v.setTextViewText(TITLE[i], t.title);
                v.setTextViewText(SUB[i], (late ? "Overdue · " : "") + when(c, t.timeMs));
                v.setTextColor(SUB[i], late ? 0xFFFF6B6B : 0xB3FFFFFF);
            } else {
                v.setViewVisibility(TITLE[i], android.view.View.GONE);
                v.setViewVisibility(SUB[i], android.view.View.GONE);
            }
        }
        v.setViewVisibility(R.id.wEmpty, pending.isEmpty()
                ? android.view.View.VISIBLE : android.view.View.GONE);
        v.setTextViewText(R.id.wEmpty, "No pending tasks");

        v.setOnClickPendingIntent(R.id.widgetRoot, openApp(c, 102, 2, false));
        return v;
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
