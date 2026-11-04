package com.example.nds;

import android.content.Context;

// the first-run "Choose your distracting apps" page: shown once
public class OnboardStore {

    public static boolean isDone(Context c) {
        return c.getSharedPreferences("onboard", Context.MODE_PRIVATE).getBoolean("apps", false);
    }

    public static void markDone(Context c) {
        c.getSharedPreferences("onboard", Context.MODE_PRIVATE)
                .edit().putBoolean("apps", true).apply();
    }
}
