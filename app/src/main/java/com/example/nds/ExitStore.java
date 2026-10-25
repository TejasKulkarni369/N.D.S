package com.example.nds;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public class ExitStore {

    public static final int WEEKLY = 3;   // emergency exits per week

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("exits", Context.MODE_PRIVATE);
    }

    // the week starts on Monday
    private static String weekKey() {
        return LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
    }

    public static int used(Context c) {
        SharedPreferences sp = sp(c);
        return weekKey().equals(sp.getString("week", "")) ? sp.getInt("used", 0) : 0;
    }

    public static int left(Context c) {
        return Math.max(0, WEEKLY - used(c));
    }

    public static void use(Context c) {
        sp(c).edit().putString("week", weekKey()).putInt("used", used(c) + 1).apply();
    }
}