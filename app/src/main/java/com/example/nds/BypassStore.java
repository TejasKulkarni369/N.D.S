package com.example.nds;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;

public class BypassStore {

    public static final int MINUTES = 15;

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("bypass", Context.MODE_PRIVATE);
    }

    private static String today() {
        return LocalDate.now().toString();
    }

    public static boolean usedToday(Context c) {
        return today().equals(sp(c).getString("day", ""));
    }

    public static boolean available(Context c) {
        return !usedToday(c);
    }

    // the app that got today's bypass ("" if none)
    public static String pkgToday(Context c) {
        return usedToday(c) ? sp(c).getString("pkg", "") : "";
    }

    public static void use(Context c, String pkg) {
        sp(c).edit().putString("day", today()).putString("pkg", pkg).apply();
    }

    // extra minutes added to this app's limit today
    public static int bonus(Context c, String pkg) {
        return (usedToday(c) && pkg.equals(sp(c).getString("pkg", ""))) ? MINUTES : 0;
    }
}