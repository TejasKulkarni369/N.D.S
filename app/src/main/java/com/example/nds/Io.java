package com.example.nds;

import android.app.Activity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One small pool for work that must not run on the main thread (loading app lists, ...). */
public class Io {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, runnable -> {
        Thread t = new Thread(runnable, "nds-io");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);   // never competes with the screen
        return t;
    });

    public static void run(Runnable r) {
        POOL.execute(r);
    }

    // back on the main thread, only if the screen is still there
    public static void ui(Activity a, Runnable r) {
        if (a != null && !a.isFinishing()) a.runOnUiThread(r);
    }
}
