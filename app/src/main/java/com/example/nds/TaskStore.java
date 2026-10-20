package com.example.nds;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TaskStore {

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("tasks", Context.MODE_PRIVATE);
    }

    public static List<Task> load(Context c) {
        List<Task> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString("list", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);

                List<Task.Check> checks = new ArrayList<>();
                JSONArray ca = o.optJSONArray("checks");
                if (ca != null) {
                    for (int j = 0; j < ca.length(); j++) {
                        JSONObject co = ca.getJSONObject(j);
                        checks.add(new Task.Check(co.getString("t"), co.optBoolean("d", false)));
                    }
                }

                out.add(new Task(o.getLong("id"), o.getString("title"),
                        o.optString("desc", ""), o.getLong("time"),
                        o.optBoolean("done", false), checks));
            }
        } catch (JSONException ignored) {
        }
        Collections.sort(out, (a, b) -> Long.compare(a.timeMs, b.timeMs));
        return out;
    }

    private static void saveAll(Context c, List<Task> list) {
        JSONArray arr = new JSONArray();
        for (Task t : list) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", t.id);
                o.put("title", t.title);
                o.put("desc", t.description);
                o.put("time", t.timeMs);
                o.put("done", t.done);

                JSONArray ca = new JSONArray();
                for (Task.Check ch : t.checks) {
                    JSONObject co = new JSONObject();
                    co.put("t", ch.text);
                    co.put("d", ch.done);
                    ca.put(co);
                }
                o.put("checks", ca);
            } catch (JSONException ignored) {
            }
            arr.put(o);
        }
        sp(c).edit().putString("list", arr.toString()).apply();
    }

    public static Task get(Context c, long id) {
        for (Task t : load(c)) {
            if (t.id == id) return t;
        }
        return null;
    }

    // add a new task, or replace the one with the same id
    public static void upsert(Context c, Task task) {
        List<Task> out = new ArrayList<>();
        boolean found = false;
        for (Task t : load(c)) {
            if (t.id == task.id) {
                out.add(task);
                found = true;
            } else {
                out.add(t);
            }
        }
        if (!found) out.add(task);
        saveAll(c, out);
    }

    public static void setDone(Context c, long id, boolean done) {
        List<Task> out = new ArrayList<>();
        for (Task t : load(c)) {
            out.add(t.id == id
                    ? new Task(t.id, t.title, t.description, t.timeMs, done, t.checks)
                    : t);
        }
        saveAll(c, out);
    }

    public static void delete(Context c, long id) {
        List<Task> out = new ArrayList<>();
        for (Task t : load(c)) {
            if (t.id != id) out.add(t);
        }
        saveAll(c, out);
    }
}