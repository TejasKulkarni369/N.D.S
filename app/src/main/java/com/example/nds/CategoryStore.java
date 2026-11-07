package com.example.nds;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CategoryStore {

    public static final int OTHER = 0;
    public static final int PRODUCTIVE = 1;
    public static final int DISTRACTING = 2;

    private static final String[] DEFAULT_DISTRACTING = {
            "com.instagram.android",
            "com.google.android.youtube",
            "com.twitter.android"
    };

    // apps pre-ticked on the first-run "Choose your distracting apps" page (if installed)
    private static final String[] SUGGESTED = {
            "com.instagram.android", "com.instagram.lite", "com.instagram.barcelona",
            "com.google.android.youtube", "com.twitter.android",
            "com.facebook.katana", "com.facebook.lite", "com.snapchat.android",
            "com.reddit.frontpage", "com.pinterest", "com.zhiliaoapp.musically",
            "com.netflix.mediaclient", "in.startv.hotstar", "in.mohalla.sharechat",
            "in.mohalla.video", "com.eterno.shortvideos", "tv.twitch.android.app"
    };

    public static boolean isSuggested(String pkg) {
        for (String p : SUGGESTED) {
            if (p.equals(pkg)) return true;
        }
        return false;
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("categories", Context.MODE_PRIVATE);
    }

    private static int defaultFor(String pkg) {
        for (String p : DEFAULT_DISTRACTING) {
            if (p.equals(pkg)) return DISTRACTING;
        }
        return OTHER;
    }

    public static int get(Context c, String pkg) {
        SharedPreferences sp = sp(c);
        int pending = sp.getInt("pendingCat_" + pkg, -1);
        String day = sp.getString("pendingDay_" + pkg, "");
        if (pending >= 0 && LocalDate.now().toString().compareTo(day) >= 0) {
            sp.edit()
                    .putInt("cat_" + pkg, pending)
                    .remove("pendingCat_" + pkg)
                    .remove("pendingDay_" + pkg)
                    .apply();
        }
        return sp.getInt("cat_" + pkg, defaultFor(pkg));
    }

    // -1 = nothing waiting
    public static int getPending(Context c, String pkg) {
        return sp(c).getInt("pendingCat_" + pkg, -1);
    }

    // Becoming Distracting = now. Leaving Distracting = tomorrow.
    public static void set(Context c, String pkg, int cat) {
        int current = get(c, pkg);
        SharedPreferences.Editor e = sp(c).edit();
        if (current == DISTRACTING && cat != DISTRACTING) {
            e.putInt("pendingCat_" + pkg, cat)
                    .putString("pendingDay_" + pkg, LocalDate.now().plusDays(1).toString());
        } else {
            e.putInt("cat_" + pkg, cat)
                    .remove("pendingCat_" + pkg)
                    .remove("pendingDay_" + pkg);
        }
        e.apply();
    }

    // the first-run choice: takes effect right now (no "until tomorrow" wait)
    public static void setNow(Context c, String pkg, int cat) {
        sp(c).edit()
                .putInt("cat_" + pkg, cat)
                .remove("pendingCat_" + pkg)
                .remove("pendingDay_" + pkg)
                .apply();
    }

    public static Set<String> distractingPackages(Context c) {
        Set<String> out = new HashSet<>();
        for (String pkg : DEFAULT_DISTRACTING) {
            if (get(c, pkg) == DISTRACTING) out.add(pkg);
        }
        for (Map.Entry<String, ?> en : sp(c).getAll().entrySet()) {
            String key = en.getKey();
            if (key.startsWith("cat_")) {
                String pkg = key.substring(4);
                if (get(c, pkg) == DISTRACTING) out.add(pkg);
            }
        }
        return out;
    }
}