package com.example.nds;

import android.content.Context;

/** Same rule the guard uses: Focus on, a bypass used today, or any limit 90% used. */
public class Protection {

    public static boolean isActive(Context c) {
        if (FocusStore.isActive(c) || BypassStore.usedToday(c)) return true;
        for (AppUsage u : UsageRepository.usageList(c, UsageRepository.snapshot(c))) {
            if (u.usedMs * 100 >= u.limitMin * 60000L * 90) return true;
        }
        return false;
    }
}
