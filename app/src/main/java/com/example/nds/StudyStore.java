package com.example.nds;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class StudyStore {

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("study", Context.MODE_PRIVATE);
    }

    public static List<StudySlot> load(Context c) {
        List<StudySlot> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString("list", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                List<StudySlot.Chapter> chapters = new ArrayList<>();
                JSONArray ch = o.optJSONArray("chapters");
                if (ch != null) {
                    for (int k = 0; k < ch.length(); k++) {
                        JSONObject chObj = ch.getJSONObject(k);
                        chapters.add(new StudySlot.Chapter(
                                chObj.optString("t"), chObj.optBoolean("d")));
                    }
                }
                out.add(new StudySlot(o.getLong("id"), o.getString("subject"),
                        o.getInt("days"), o.getInt("start"), o.getInt("end"),
                        o.optBoolean("notify", true), o.optBoolean("lock", false), chapters));
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    private static void saveAll(Context c, List<StudySlot> list) {
        JSONArray arr = new JSONArray();
        for (StudySlot s : list) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", s.id);
                o.put("subject", s.subject);
                o.put("days", s.dayMask);
                o.put("start", s.startMin);
                o.put("end", s.endMin);
                o.put("notify", s.notify);
                o.put("lock", s.lockIn);
                JSONArray ch = new JSONArray();
                for (StudySlot.Chapter chapter : s.chapters) {
                    JSONObject co = new JSONObject();
                    co.put("t", chapter.text);
                    co.put("d", chapter.done);
                    ch.put(co);
                }
                o.put("chapters", ch);
            } catch (JSONException ignored) {
            }
            arr.put(o);
        }
        sp(c).edit().putString("list", arr.toString()).apply();
    }

    public static StudySlot get(Context c, long id) {
        for (StudySlot s : load(c)) {
            if (s.id == id) return s;
        }
        return null;
    }

    // add a new session, or replace the one with the same id
    public static void upsert(Context c, StudySlot slot) {
        List<StudySlot> out = new ArrayList<>();
        boolean found = false;
        for (StudySlot s : load(c)) {
            if (s.id == slot.id) {
                out.add(slot);
                found = true;
            } else {
                out.add(s);
            }
        }
        if (!found) out.add(slot);
        saveAll(c, out);
    }

    public static void delete(Context c, long id) {
        List<StudySlot> out = new ArrayList<>();
        for (StudySlot s : load(c)) {
            if (s.id != id) out.add(s);
        }
        saveAll(c, out);
    }
}