package com.example.nds;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class TaskScheduler {

    private static PendingIntent pending(Context c, Task t) {
        Intent i = new Intent(c, TaskReminderReceiver.class);
        i.putExtra("id", t.id);
        i.putExtra("title", t.title);
        return PendingIntent.getBroadcast(c, (int) (t.id & 0x7fffffff), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void schedule(Context c, Task t) {
        if (t.done || t.timeMs <= System.currentTimeMillis()) return;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent p = pending(c, t);
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.timeMs, p);   // may be a bit late
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.timeMs, p);
        }
    }

    public static void cancel(Context c, Task t) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pending(c, t));
    }

    // alarms are wiped when the phone restarts, so set them again
    public static void rescheduleAll(Context c) {
        for (Task t : TaskStore.load(c)) {
            schedule(c, t);
        }
    }
}