package com.example.nds;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import java.util.Map;

public class IconManager {

    private static final String[] ALIASES = {
            "com.example.nds.IconState1",
            "com.example.nds.IconState2",
            "com.example.nds.IconState3",
            "com.example.nds.IconState4"
    };

    // highest "percent of limit used" among tracked apps
    private static int maxPercent(Context ctx, UsageRepository.Snapshot s) {
        int best = 0;
        for (Map.Entry<String, Long> en : s.millis.entrySet()) {
            AppUsage u = UsageRepository.usageFor(ctx, s, en.getKey());
            if (u == null) continue;
            long limitMs = u.limitMin * 60000L;
            int pct = (int) Math.min(100, en.getValue() * 100 / limitMs);
            if (pct > best) best = pct;
        }
        return best;
    }

    private static int stateFor(int percent) {
        if (percent >= 100) return 3;
        if (percent >= 75) return 2;
        if (percent >= 50) return 1;
        return 0;
    }

    public static void apply(Context ctx, UsageRepository.Snapshot s) {
        int state = stateFor(maxPercent(ctx, s));
        SharedPreferences sp = ctx.getSharedPreferences("icon", Context.MODE_PRIVATE);
        if (sp.getInt("state", 0) == state) return;

        PackageManager pm = ctx.getPackageManager();
        // enable the new one first so the app is never without a launcher icon
        set(pm, ctx, state, PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        for (int i = 0; i < ALIASES.length; i++) {
            if (i != state) set(pm, ctx, i, PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        }
        sp.edit().putInt("state", state).apply();
    }

    private static void set(PackageManager pm, Context ctx, int i, int flag) {
        pm.setComponentEnabledSetting(
                new ComponentName(ctx.getPackageName(), ALIASES[i]),
                flag,
                PackageManager.DONT_KILL_APP);
    }
}