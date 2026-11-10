package com.example.nds;

import android.content.Context;
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

    private static final int[] TITLE = {R.id.wRow1, R.id.wRow2, R.id.wRow3, R.id.wRow4, R.id.wRow5};
    private static final int[] SUB = {R.id.wSub1, R.id.wSub2, R.id.wSub3, R.id.wSub4, R.id.wSub5};

    @Override
    RemoteViews build(Context c, int id) {
        Calendar now = Calendar.getInstance();
        int day = StudyScheduler.todayIndex();
        int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        String dayName = new SimpleDateFormat("EEE", Locale.getDefault()).format(new Date());

        List<StudySlot> today = new ArrayList<>();
        List<StudySlot> coming = new ArrayList<>();
        for (StudySlot s : StudyStore.load(c)) {
            if (!s.onDay(day)) continue;
            today.add(s);
            if (s.endMin > minute) coming.add(s);
        }
        Collections.sort(coming, (a, b) -> Integer.compare(a.startMin, b.startMin));

        String emptyText = today.isEmpty() ? "No sessions today" : "Done for today";
        int size = sizeClass(c, id);
        RemoteViews v;

        if (size == MINI) {
            v = views(c, R.layout.widget_mini);
            if (coming.isEmpty()) {
                txt(v, R.id.wMiniValue, "–");
                txt(v, R.id.wMiniLabel, "TIME TABLE");
            } else {
                StudySlot s = coming.get(0);
                txt(v, R.id.wMiniValue, minute >= s.startMin ? "Now"
                        : StudyScheduler.timeText(s.startMin).replace(" ", ""));
                txt(v, R.id.wMiniLabel, s.subject);
            }

        } else if (size == STRIP) {
            v = views(c, R.layout.widget_strip);
            if (coming.isEmpty()) {
                txt(v, R.id.wStripLabel, "TIME TABLE  ·  " + dayName);
                txt(v, R.id.wStripSub, emptyText);
                txt(v, R.id.wStripValue, "");
            } else {
                StudySlot s = coming.get(0);
                boolean running = minute >= s.startMin;
                txt(v, R.id.wStripLabel, (running ? "NOW" : "NEXT") + "  ·  "
                        + StudyScheduler.timeText(s.startMin) + " – "
                        + StudyScheduler.timeText(s.endMin) + (s.lockIn ? "  ·  Lock-in" : ""));
                txt(v, R.id.wStripSub, s.subject);
                txt(v, R.id.wStripValue, running ? "Now" : StudyScheduler.timeText(s.startMin));
            }

        } else {
            v = views(c, R.layout.widget_list);
            txt(v, R.id.wTitle, "TIME TABLE");
            txt(v, R.id.wCount, dayName);

            int rows = listRows(sizeDp(c, id)[1]);   // as many sessions as the widget is tall
            for (int i = 0; i < 5; i++) {
                boolean on = i < rows && i < coming.size();
                show(v, TITLE[i], on);
                show(v, SUB[i], on);
                if (!on) continue;
                StudySlot s = coming.get(i);
                boolean running = minute >= s.startMin;
                txt(v, TITLE[i], s.subject);
                txt(v, SUB[i], (running ? "Now  ·  " : "")
                        + StudyScheduler.timeText(s.startMin) + " – "
                        + StudyScheduler.timeText(s.endMin)
                        + (s.lockIn ? "  ·  Lock-in" : ""));
                color(v, SUB[i], running ? 0xFFFF7A1A : 0xB3FFFFFF);
            }
            show(v, R.id.wEmpty, coming.isEmpty());
            txt(v, R.id.wEmpty, emptyText);
        }

        v.setOnClickPendingIntent(R.id.widgetRoot, openApp(c, 103, 3, false));
        return v;
    }
}
