package com.example.nds;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;

/**
 * Every permission N.D.S uses: its status, and a way to turn it on or off.
 * Android only lets the person change most of them on its own Settings screen, so a tap
 * opens that screen. Device admin is switched here directly.
 */
public class Perms {

    public static final int USAGE = 0, OVERLAY = 1, NOTIF = 2, LISTENER = 3,
            ACCESS = 4, ADMIN = 5, ALARM = 6;

    public static final String[] TITLES = {
            "Usage access", "Display over other apps", "Notifications", "Notification access",
            "Accessibility", "Device admin", "Alarms & reminders"};

    public static final String[] DESCS = {
            "Measures your screen time. N.D.S can't work without it.",
            "Shows the Time's up and Focus screens on top of other apps.",
            "Task and study reminders, and limit warnings.",
            "Counts notifications per app. It never reads what they say.",
            "Optional. Only for the instant close of a locked app. Keep it off for banking and UPI apps.",
            "Stops N.D.S from being uninstalled.",
            "Lets reminders arrive exactly on time."};

    // the rows to show on this phone
    public static int[] visible() {
        return Build.VERSION.SDK_INT >= 31
                ? new int[]{USAGE, OVERLAY, NOTIF, LISTENER, ACCESS, ADMIN, ALARM}
                : new int[]{USAGE, OVERLAY, NOTIF, LISTENER, ACCESS, ADMIN};
    }

    private static ComponentName admin(Context c) {
        return new ComponentName(c, AdminReceiver.class);
    }

    @SuppressWarnings("deprecation")
    public static boolean isOn(Context c, int id) {
        switch (id) {
            case USAGE: {
                AppOpsManager ao = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
                int mode = Build.VERSION.SDK_INT >= 29
                        ? ao.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(), c.getPackageName())
                        : ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(), c.getPackageName());
                return mode == AppOpsManager.MODE_ALLOWED;
            }
            case OVERLAY:
                return Settings.canDrawOverlays(c);
            case NOTIF:
                return NotificationManagerCompat.from(c).areNotificationsEnabled();
            case LISTENER:
                return NotificationManagerCompat.getEnabledListenerPackages(c)
                        .contains(c.getPackageName());
            case ACCESS:
                return AccessGate.isEnabled(c);
            case ADMIN:
                return ((DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE))
                        .isAdminActive(admin(c));
            case ALARM:
                return Build.VERSION.SDK_INT < 31
                        || ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE))
                        .canScheduleExactAlarms();
            default:
                return false;
        }
    }

    // On / Off / Auto (Accessibility is switched automatically when the ADB grant was given)
    public static String status(Context c, int id) {
        if (id == ACCESS && AccessGate.canToggle(c)) return "Auto";
        return isOn(c, id) ? "On" : "Off";
    }

    // a permission that keeps N.D.S protected: it cannot be turned OFF while protection is active
    private static boolean critical(int id) {
        return id == USAGE || id == OVERLAY || id == LISTENER || id == ACCESS || id == ADMIN;
    }

    // tap on a row: turn it on or off
    public static void toggle(Activity a, int id) {
        if (id == ACCESS && AccessGate.canToggle(a)) {
            Toast.makeText(a, "N.D.S switches Accessibility on only while it closes an app",
                    Toast.LENGTH_LONG).show();
            return;
        }

        boolean on = isOn(a, id);
        if (on && critical(id) && Protection.isActive(a)) {
            Toast.makeText(a, "Locked: your limits are almost used. Try again tomorrow.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (id == ADMIN) {
            DevicePolicyManager dpm =
                    (DevicePolicyManager) a.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (on) {
                dpm.removeActiveAdmin(admin(a));   // switched off right here
            } else {
                Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(a));
                i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "So N.D.S cannot be uninstalled while your limits are almost used.");
                start(a, i, null);
            }
            return;
        }

        String pkg = a.getPackageName();
        Uri uri = Uri.parse("package:" + pkg);
        Intent i;
        Intent fallback = new Intent(Settings.ACTION_SETTINGS);
        switch (id) {
            case USAGE:
                i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, uri);
                fallback = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                break;
            case OVERLAY:
                i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri);
                break;
            case NOTIF:
                i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, pkg);
                break;
            case LISTENER:
                i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                break;
            case ACCESS:
                i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                break;
            default:   // ALARM
                i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, uri);
                break;
        }
        start(a, i, fallback);
    }

    private static void start(Activity a, Intent i, Intent fallback) {
        try {
            a.startActivity(i);
        } catch (Exception e) {
            try {
                if (fallback != null) a.startActivity(fallback);
            } catch (Exception ignored) {
            }
        }
    }
}
