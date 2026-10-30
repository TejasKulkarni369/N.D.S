package com.example.nds;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

/**
 * Banking-safe Accessibility.
 *
 * Banking apps refuse to run while ANY accessibility service is switched on.
 * With a one-time ADB grant of WRITE_SECURE_SETTINGS, N.D.S switches its own
 * service on only while it has a job (Force stop, or a protected Settings screen)
 * and off everywhere else. Without the grant, nothing here does anything.
 *
 * One-time command (phone connected by USB, USB debugging on):
 *   adb shell pm grant com.example.nds android.permission.WRITE_SECURE_SETTINGS
 */
public class AccessGate {

    public static boolean canToggle(Context c) {
        return c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                == PackageManager.PERMISSION_GRANTED;
    }

    private static ComponentName me(Context c) {
        return new ComponentName(c, GuardAccessibilityService.class);
    }

    private static boolean sameService(String entry, ComponentName me) {
        ComponentName cn = ComponentName.unflattenFromString(entry);
        return cn != null && cn.equals(me);
    }

    public static synchronized boolean isEnabled(Context c) {
        String list = Settings.Secure.getString(
                c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (list == null || list.isEmpty()) return false;
        ComponentName me = me(c);
        for (String e : list.split(":")) {
            if (sameService(e, me)) return true;
        }
        return false;
    }

    // keeps every other accessibility service the person has turned on
    public static synchronized void set(Context c, boolean on) {
        if (!canToggle(c)) return;
        try {
            String list = Settings.Secure.getString(
                    c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            ComponentName me = me(c);

            StringBuilder out = new StringBuilder();
            if (list != null && !list.isEmpty()) {
                for (String e : list.split(":")) {
                    if (e.isEmpty() || sameService(e, me)) continue;
                    if (out.length() > 0) out.append(':');
                    out.append(e);
                }
            }
            if (on) {
                if (out.length() > 0) out.append(':');
                out.append(me.flattenToString());
            }

            Settings.Secure.putString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, out.toString());
            Settings.Secure.putInt(c.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, out.length() > 0 ? 1 : 0);
        } catch (Exception ignored) {
        }
    }
}