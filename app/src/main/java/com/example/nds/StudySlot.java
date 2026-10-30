package com.example.nds;

import java.util.ArrayList;
import java.util.List;

public class StudySlot {

    // one chapter of the subject, with a tick
    public static class Chapter {
        public final String text;
        public boolean done;

        public Chapter(String text, boolean done) {
            this.text = text;
            this.done = done;
        }
    }

    public final long id;
    public final String subject;
    public final int dayMask;    // bit 0 = Monday ... bit 6 = Sunday
    public final int startMin;   // minutes after midnight
    public final int endMin;
    public final boolean notify;
    public final boolean lockIn;                // the phone is locked (Focus) from start to end
    public final List<Chapter> chapters;

    public StudySlot(long id, String subject, int dayMask, int startMin, int endMin,
                     boolean notify, boolean lockIn, List<Chapter> chapters) {
        this.id = id;
        this.subject = subject;
        this.dayMask = dayMask;
        this.startMin = startMin;
        this.endMin = endMin;
        this.notify = notify;
        this.lockIn = lockIn;
        this.chapters = (chapters == null) ? new ArrayList<>() : chapters;
    }

    // old form, kept so nothing else breaks
    public StudySlot(long id, String subject, int dayMask, int startMin, int endMin,
                     boolean notify) {
        this(id, subject, dayMask, startMin, endMin, notify, false, null);
    }

    // day: 0 = Monday ... 6 = Sunday
    public boolean onDay(int day) {
        return (dayMask & (1 << day)) != 0;
    }

    public int doneCount() {
        int n = 0;
        for (Chapter c : chapters) {
            if (c.done) n++;
        }
        return n;
    }
}