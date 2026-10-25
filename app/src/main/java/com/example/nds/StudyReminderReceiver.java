package com.example.nds;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;

import androidx.core.app.NotificationCompat;

public class StudyReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL = "study_v1";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        long id = intent.getLongExtra("id", 0);
        int day = intent.getIntExtra("day", 0);
        int type = intent.getIntExtra("type", StudyScheduler.START);

        StudySlot s = StudyStore.get(ctx, id);
        if (s == null || !s.notify || !s.onDay(day)) return;   // deleted or changed since

        // set next week's alarm of the same kind
        StudyScheduler.scheduleOne(ctx, s, day, type);

        String range = StudyScheduler.timeText(s.startMin) + " – "
                + StudyScheduler.timeText(s.endMin);
        String title;
        String text;
        if (type == StudyScheduler.BEFORE) {
            title = "Study in " + StudyScheduler.BEFORE_MIN + " minutes";
            text = s.subject + " starts at " + StudyScheduler.timeText(s.startMin);
        } else if (type == StudyScheduler.START) {
            title = "Study time";
            text = s.subject + " · " + range + ". Time to focus!";
        } else {
            title = "Session finished";
            text = s.subject + " is done. Take a short break.";
        }

        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        Uri sound = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE
                + "://" + ctx.getPackageName() + "/" + R.raw.reminder);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        NotificationChannel ch = new NotificationChannel(
                CHANNEL, "Study timetable", NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(sound, attrs);
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 250, 150, 250});
        nm.createNotificationChannel(ch);

        int code = StudyScheduler.code(id, day, type);
        Intent open = new Intent(ctx, MainActivity.class);
        open.putExtra("tab", 2);   // opens the Tasks tab
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, code, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
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