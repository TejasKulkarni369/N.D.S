package com.example.nds;

import android.content.Context;

/** Same rule the guard uses: Focus on, a bypass used today, or any limit 90% used. */
public class Protection {

    public static boolean isActive(Context c) {
        return isActive(c, FocusStore.isActive(c),
                UsageRepository.usageList(c, UsageRepository.snapshot(c)));
    }

    // the same rule for a caller that already has Focus and the usage list (the guard, every second)
    public static boolean isActive(Context c, boolean focus, java.util.List<AppUsage> usage) {
        if (focus || BypassStore.usedToday(c)) return true;
        for (AppUsage u : usage) {
            if (u.usedMs * 100 >= u.limitMin * 60000L * 90) return true;
        }
        return false;
    }
}
