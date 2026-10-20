package com.example.nds;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;

public class LimitStore {

    public static final int STEP = 5;
    public static final int MIN = 5;

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("limits", Context.MODE_PRIVATE);
    }

    public static int maxFor(String pkg) {
        return pkg.equals("com.instagram.android") ? 60 : 180;
    }

    public static int getLimit(Context c, String pkg, int def) {
        SharedPreferences sp = sp(c);
        int pending = sp.getInt("pending_" + pkg, 0);
        String day = sp.getString("pendingDay_" + pkg, "");
        if (pending > 0 && LocalDate.now().toString().compareTo(day) >= 0) {
            sp.edit()
                    .putInt("limit_" + pkg, pending)
                    .remove("pending_" + pkg)
                    .remove("pendingDay_" + pkg)
                    .apply();
        }
        return sp.getInt("limit_" + pkg, def);
    }

    public static int getPending(Context c, String pkg) {
        return sp(c).getInt("pending_" + pkg, 0);
    }

    // Lower = applies now. Higher = applies tomorrow.
    public static void change(Context c, String pkg, int currentLimit, int delta) {
        int pending = getPending(c, pkg);
        int basis = (pending > 0) ? pending : currentLimit;
        int target = Math.max(MIN, Math.min(maxFor(pkg), basis + delta));

        SharedPreferences.Editor e = sp(c).edit();
        if (target <= currentLimit) {
            e.putInt("limit_" + pkg, target)
                    .remove("pending_" + pkg)
                    .remove("pendingDay_" + pkg);
        } else {
            e.putInt("pending_" + pkg, target)
                    .putString("pendingDay_" + pkg, LocalDate.now().plusDays(1).toString());
        }
        e.apply();
    }
}