package com.example.nds;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;
import java.util.Locale;

public class StudyScheduler {

    public static final int BEFORE_MIN = 5;   // the first notification comes this early

    // the three notifications of a session
    public static final int BEFORE = 0;
    public static final int START = 1;
    public static final int END = 2;

    // Monday = 0 ... Sunday = 6
    public static int todayIndex() {
        return (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7;
    }

    public static String timeText(int min) {
        int h = min / 60;
        int m = min % 60;
        int h12 = (h % 12 == 0) ? 12 : h % 12;
        return String.format(Locale.US, "%d:%02d %s", h12, m, h < 12 ? "AM" : "PM");
    }

    public static int code(long id, int day, int type) {
        return (int) (id & 0x7FFFF) * 32 + day * 3 + type;
    }

    private static PendingIntent pending(Context c, long id, int day, int type) {
        Intent i = new Intent(c, StudyReminderReceiver.class);
        i.putExtra("id", id);
        i.putExtra("day", day);
        i.putExtra("type", type);
        return PendingIntent.getBroadcast(c, code(id, day, type), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // the next time (after now) that this weekday + time of day comes round
    private static long nextTrigger(int day, int minuteOfDay, int offsetMin) {
        Calendar c = Calendar.getInstance();
        int ahead = (day - todayIndex() + 7) % 7;
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_YEAR, ahead);
        c.add(Calendar.MINUTE, minuteOfDay + offsetMin);

        long now = System.currentTimeMillis();
        while (c.getTimeInMillis() <= now + 1000) {
            c.add(Calendar.DAY_OF_YEAR, 7);
        }
        return c.getTimeInMillis();
    }

    // set the next alarm of one kind, for one day of a session
    public static void scheduleOne(Context c, StudySlot s, int day, int type) {
        int minute = (type == END) ? s.endMin : s.startMin;
        int offset = (type == BEFORE) ? -BEFORE_MIN : 0;
        long at = nextTrigger(day, minute, offset);

        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent p = pending(c, s.id, day, type);
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);   // may be a bit late
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        }
    }

    public static void schedule(Context c, StudySlot s) {
        if (!s.notify) return;
        for (int day = 0; day < 7; day++) {
            if (!s.onDay(day)) continue;
            for (int type = 0; type < 3; type++) {
                scheduleOne(c, s, day, type);
            }
        }
    }

    public static void cancel(Context c, long id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        for (int day = 0; day < 7; day++) {
            for (int type = 0; type < 3; type++) {
                am.cancel(pending(c, id, day, type));
            }
        }
    }

    // alarms are wiped when the phone restarts, so set them again
    public static void rescheduleAll(Context c) {
        for (StudySlot s : StudyStore.load(c)) {
            cancel(c, s.id);
            schedule(c, s);
        }
    }
}