package com.example.nds;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;

import java.time.LocalDate;

public class NotificationStore {

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("notifs", Context.MODE_PRIVATE);
    }

    public static void add(Context c, String pkg) {
        SharedPreferences sp = sp(c);
        String day = LocalDate.now().toString();
        SharedPreferences.Editor e = sp.edit();
        if (!day.equals(sp.getString("_day", ""))) {
            e.clear().putString("_day", day);        // new day: forget old counts
        }
        String key = day + "|" + pkg;
        int n = day.equals(sp.getString("_day", "")) ? sp.getInt(key, 0) : 0;
        e.putInt(key, n + 1).apply();
    }

    public static int today(Context c, String pkg) {
        return sp(c).getInt(LocalDate.now().toString() + "|" + pkg, 0);
    }

    public static boolean hasAccess(Context c) {
        return NotificationManagerCompat.getEnabledListenerPackages(c)
                .contains(c.getPackageName());
    }

    public static void openSettings(Context c) {
        Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }
}