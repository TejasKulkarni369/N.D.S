package com.example.nds;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.net.Uri;

import androidx.core.app.NotificationCompat;

import java.time.LocalDate;
import java.util.List;

public class LimitWarner {

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("limitwarn", Context.MODE_PRIVATE);
    }

    // called every second: warns once at 50% and once at 90% of each app's limit, per day
    public static void check(Context ctx, List<AppUsage> usage) {
        SharedPreferences sp = sp(ctx);
        String day = LocalDate.now().toString();
        if (!day.equals(sp.getString("day", ""))) {
            sp.edit().clear().putString("day", day).apply();   // new day: warn again
        }

        for (AppUsage u : usage) {
            if (u == null || u.limitMin <= 0) continue;

            long limitMs = u.limitMin * 60000L;
            int pct = (int) (u.usedMs * 100 / limitMs);
            if (pct >= 100) continue;                 // locked: the Time's up screen takes over

            int level = pct >= 90 ? 90 : (pct >= 50 ? 50 : 0);
            if (level == 0) continue;

            String key = u.pkg + "|" + level;
            if (sp.getBoolean(key, false)) continue;

            // one jump (for example 40% to 95%) gives only the 90% warning
            SharedPreferences.Editor e = sp.edit().putBoolean(key, true);
            if (level == 90) e.putBoolean(u.pkg + "|50", true);
            e.apply();

            warn(ctx, u, level, limitMs - u.usedMs);
        }
    }

    private static void warn(Context ctx, AppUsage u, int level, long leftMs) {
        boolean last = (level == 90);
        String channel = last ? "limit90_v1" : "limit50_v1";
        int soundRes = last ? R.raw.limit_warn_90 : R.raw.limit_warn_50;

        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        Uri sound = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE
                + "://" + ctx.getPackageName() + "/" + soundRes);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        NotificationChannel ch = new NotificationChannel(channel,
                last ? "Limit warning 90%" : "Limit warning 50%",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(sound, attrs);
        ch.enableVibration(true);
        ch.setVibrationPattern(last
                ? new long[]{0, 200, 120, 200, 120, 300}
                : new long[]{0, 250, 150, 250});
        nm.createNotificationChannel(ch);

        int leftMin = (int) Math.max(1, Math.ceil(leftMs / 60000.0));
        String left = UsageRepository.formatMinutes(leftMin);
        String title = last
                ? u.name + ": almost out of time"
                : u.name + ": halfway through your limit";
        String text = last
                ? "90% of your limit is used. Only " + left + " left, then it locks until tomorrow."
                : "50% of your limit is used. " + left + " left today.";

        int code = (u.pkg + level).hashCode();
        Intent open = new Intent(ctx, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, code, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new NotificationCompat.Builder(ctx, channel)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();

        nm.notify(code, n);
    }
}