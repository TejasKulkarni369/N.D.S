package com.example.nds;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public class GuardUtils {

    private static ComponentName admin(Context c) {
        return new ComponentName(c, AdminReceiver.class);
    }

    public static boolean isAdminActive(Context c) {
        DevicePolicyManager dpm =
                (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm.isAdminActive(admin(c));
    }

    // Start this with startActivityForResult and WITHOUT FLAG_ACTIVITY_NEW_TASK.
    // Otherwise Android closes the screen immediately.
    public static Intent adminIntent(Context c) {
        Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(c));
        i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Keeps Android from uninstalling N.D.S while your limits are on.");
        return i;
    }

    public static boolean isAccessibilityOn(Context c) {
        String enabled = Settings.Secure.getString(
                c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName me = new ComponentName(c, GuardAccessibilityService.class);
        return enabled.contains(me.flattenToString())
                || enabled.contains(me.flattenToShortString());
    }

    public static void openAccessibilitySettings(Context c) {
        Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }
}