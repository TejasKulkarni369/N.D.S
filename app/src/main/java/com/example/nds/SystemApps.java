package com.example.nds;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.ContactsContract;
import android.telecom.TelecomManager;

public class SystemApps {

    // system screens that must keep working during Focus (calls, status bar, permission pop-ups)
    public static final String[] ALWAYS_ALLOWED = {
            "android",
            "com.android.systemui",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.incallui",
            "com.google.android.dialer",
            "com.samsung.android.incallui",
            "com.samsung.android.dialer",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller"
    };

    public static String dialer(Context c) {
        try {
            TelecomManager tm = (TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
            if (tm != null) {
                String p = tm.getDefaultDialerPackage();
                if (p != null) return p;
            }
        } catch (Exception ignored) {
        }
        return resolve(c, new Intent(Intent.ACTION_DIAL));
    }

    public static String contacts(Context c) {
        String p = resolve(c, Intent.makeMainSelectorActivity(
                Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS));
        if (p != null) return p;
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(ContactsContract.Contacts.CONTENT_URI);
        return resolve(c, i);
    }

    public static String home(Context c) {
        return resolve(c, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
    }

    @SuppressWarnings("deprecation")
    private static String resolve(Context c, Intent i) {
        ResolveInfo ri = c.getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
        if (ri == null || ri.activityInfo == null) return null;
        String p = ri.activityInfo.packageName;
        return "android".equals(p) ? null : p;
    }
}