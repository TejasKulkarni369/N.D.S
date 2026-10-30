package com.example.nds;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;

/**
 * Time Table lock-in. A session with "Want to lock in" ticked turns on Focus mode
 * from its start time to its end time. Each session starts its lock-in once a day.
 */
public class StudyLock {

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("studylock", Context.MODE_PRIVATE);
    }

    private static String todayKey(Calendar now) {
        return now.get(Calendar.YEAR) + "-" + now.get(Calendar.DAY_OF_YEAR);
    }

    private static boolean covers(StudySlot s, Calendar now) {
        int day = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7;   // Monday = 0
        int min = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        return s.lockIn && s.onDay(day) && min >= s.startMin && min < s.endMin;
    }

    private static boolean used(Context c, StudySlot s, Calendar now) {
        SharedPreferences p = sp(c);
        if (!todayKey(now).equals(p.getString("day", ""))) return false;
        return Arrays.asList(p.getString("used", "").split(",")).contains(String.valueOf(s.id));
    }

    private static void markUsed(Context c, StudySlot s, Calendar now) {
        SharedPreferences p = sp(c);
        String day = todayKey(now);
        String used = day.equals(p.getString("day", "")) ? p.getString("used", "") : "";
        String id = String.valueOf(s.id);
        if (!Arrays.asList(used.split(",")).contains(id)) {
            used = used.isEmpty() ? id : used + "," + id;
        }
        p.edit().putString("day", day).putString("used", used).apply();
    }

    /**
     * If a lock-in session is running right now and has not started yet today,
     * start Focus until its end. Safe to call often (alarm, and a check every 15 s).
     */
    public static synchronized boolean startIfDue(Context c) {
        if (FocusStore.isActive(c)) return false;   // Focus is already on

        Calendar now = Calendar.getInstance();
        for (StudySlot s : StudyStore.load(c)) {
            if (!covers(s, now) || used(c, s, now)) continue;

            markUsed(c, s, now);

            Calendar end = (Calendar) now.clone();
            end.set(Calendar.HOUR_OF_DAY, s.endMin / 60);
            end.set(Calendar.MINUTE, s.endMin % 60);
            end.set(Calendar.SECOND, 0);
            end.set(Calendar.MILLISECOND, 0);
            if (end.getTimeInMillis() - System.currentTimeMillis() < 30000) return false;

            // no emergency apps: only Phone, Contacts and N.D.S stay open
            FocusStore.startUntil(c, end.getTimeInMillis(), new ArrayList<>());

            try {
                Intent i = new Intent(c, FocusActivity.class);
                i.putExtra("intro", true);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                c.startActivity(i);
            } catch (Exception ignored) {
                // the guard still brings the Focus screen up when another app opens
            }
            return true;
        }
        return false;
    }

    // after an Emergency Exit: the sessions running now do not lock again today
    public static void skipCurrent(Context c) {
        Calendar now = Calendar.getInstance();
        for (StudySlot s : StudyStore.load(c)) {
            if (covers(s, now)) markUsed(c, s, now);
        }
    }

    // a session saved while it is already running starts locking from its next day
    public static void skipIfRunning(Context c, StudySlot slot) {
        Calendar now = Calendar.getInstance();
        if (covers(slot, now)) markUsed(c, slot, now);
    }
}