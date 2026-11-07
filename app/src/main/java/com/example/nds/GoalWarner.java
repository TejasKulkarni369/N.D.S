package com.example.nds;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.core.app.NotificationCompat;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

/** Called about once a second by the guard. Each warning is shown once per day. */
public class GoalWarner {

    private static final String CHANNEL = "goals";

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("goal_warned", Context.MODE_PRIVATE);
    }

    private static String today() {
        Calendar n = Calendar.getInstance();
        return n.get(Calendar.YEAR) + "-" + n.get(Calendar.DAY_OF_YEAR);
    }

    public static void check(Context c, UsageRepository.Snapshot s) {
        int screenGoal = GoalStore.screenTodayMin(c);
        Set<String> apps = GoalStore.opensApps(c);
        if (screenGoal <= 0 && apps.isEmpty()) return;   // nothing to watch: do no work

        // today's warnings that were already shown
        SharedPreferences p = sp(c);
        boolean newDay = !today().equals(p.getString("day", ""));
        Set<String> done = newDay ? new HashSet<>() : new HashSet<>(p.getStringSet("done", new HashSet<>()));
        boolean changed = newDay;

        // ---- screen time goal ----
        if (screenGoal > 0) {
            long total = 0;
            for (Long v : s.millis.values()) total += v;
            long goalMs = screenGoal * 60000L;

            if (total >= goalMs) {
                if (done.add("screen100")) {
                    changed = true;
                    notify(c, "screen100", "Screen time goal reached",
                            "You used " + UsageRepository.formatMinutes((int) (total / 60000))
                                    + " today. That is your goal for today.");
                }
            } else if (total * 100 >= goalMs * 90) {
                if (done.add("screen90")) {
                    changed = true;
                    notify(c, "screen90", "Almost at your screen time goal",
                            UsageRepository.formatMinutes((int) (total / 60000)) + " of "
                                    + UsageRepository.formatMinutes(screenGoal) + " used today.");
                }
            }
        }

        // ---- opens goals ----
        for (String pkg : apps) {
            int goal = GoalStore.opens(c, pkg);
            if (goal <= 0) continue;
            int opens = UsageRepository.opensFor(s, pkg);
            String name = UsageRepository.labelFor(c, pkg);

            if (opens > goal) {
                if (done.add("over:" + pkg)) {
                    changed = true;
                    notify(c, "over:" + pkg, "Over your opens goal",
                            "You opened " + name + " " + opens + " times today. Your goal was "
                                    + goal + ".");
                }
            } else if (opens == goal) {
                if (done.add("reached:" + pkg)) {
                    changed = true;
                    notify(c, "reached:" + pkg, "Opens goal reached",
                            "You opened " + name + " " + opens
                                    + " times today. The next open is over your goal.");
                }
            }
        }

        if (changed) p.edit().putString("day", today()).putStringSet("done", done).apply();
    }

    private static void notify(Context c, String key, String title, String text) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Goals", NotificationManager.IMPORTANCE_DEFAULT));

        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 7000 + (key.hashCode() & 0xFFF), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        nm.notify(7000 + (key.hashCode() & 0xFFF), new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build());
    }
}
