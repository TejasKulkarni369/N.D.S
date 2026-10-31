package com.example.nds;

import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// today's study sessions: running and coming up first
public class TimeTableWidget extends NdsWidget {

    private static final int[] TITLE = {R.id.wRow1, R.id.wRow2, R.id.wRow3};
    private static final int[] SUB = {R.id.wSub1, R.id.wSub2, R.id.wSub3};

    @Override
    RemoteViews build(Context c, int id) {
        Calendar now = Calendar.getInstance();
        int day = StudyScheduler.todayIndex();
        int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        List<StudySlot> today = new ArrayList<>();
        List<StudySlot> coming = new ArrayList<>();
        for (StudySlot s : StudyStore.load(c)) {
            if (!s.onDay(day)) continue;
            today.add(s);
            if (s.endMin > minute) coming.add(s);
        }
        Collections.sort(coming, (a, b) -> Integer.compare(a.startMin, b.startMin));

        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_list);
        v.setTextViewText(R.id.wTitle, "TIME TABLE");
        v.setTextViewText(R.id.wCount,
                new SimpleDateFormat("EEE", Locale.getDefault()).format(new Date()));

        for (int i = 0; i < 3; i++) {
            if (i < coming.size()) {
                StudySlot s = coming.get(i);
                boolean running = minute >= s.startMin;
                v.setViewVisibility(TITLE[i], View.VISIBLE);
                v.setViewVisibility(SUB[i], View.VISIBLE);
                v.setTextViewText(TITLE[i], s.subject);
                v.setTextViewText(SUB[i], (running ? "Now  ·  " : "")
                        + StudyScheduler.timeText(s.startMin) + " – "
                        + StudyScheduler.timeText(s.endMin)
                        + (s.lockIn ? "  ·  Lock-in" : ""));
                v.setTextColor(SUB[i], running ? 0xFFFF7A1A : 0xB3FFFFFF);
            } else {
                v.setViewVisibility(TITLE[i], View.GONE);
                v.setViewVisibility(SUB[i], View.GONE);
            }
        }

        boolean none = coming.isEmpty();
        v.setViewVisibility(R.id.wEmpty, none ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.wEmpty, today.isEmpty() ? "No sessions today" : "Done for today");

        v.setOnClickPendingIntent(R.id.widgetRoot, openApp(c, 103, 3, false));
        return v;
    }
}
