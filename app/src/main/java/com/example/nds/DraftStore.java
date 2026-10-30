package com.example.nds;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Unfinished Task / Time Table forms. Kept on the phone until the form is saved or deleted.
 * Keys: task_0 = the "New task" draft, task_<id> = a task being edited,
 *       study_0 = the "New session" draft, study_<id> = a session being edited.
 */
public class DraftStore {

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("nds_drafts", Context.MODE_PRIVATE);
    }

    public static String taskKey(long id) {
        return "task_" + id;
    }

    public static String studyKey(long id) {
        return "study_" + id;
    }

    public static String get(Context c, String key) {
        return sp(c).getString(key, null);
    }

    public static boolean has(Context c, String key) {
        return sp(c).contains(key);
    }

    public static void put(Context c, String key, String json) {
        sp(c).edit().putString(key, json).apply();
    }

    public static void clear(Context c, String key) {
        sp(c).edit().remove(key).apply();
    }
}