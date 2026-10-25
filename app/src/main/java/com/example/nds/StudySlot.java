package com.example.nds;

public class StudySlot {

    public final long id;
    public final String subject;
    public final int dayMask;    // bit 0 = Monday ... bit 6 = Sunday
    public final int startMin;   // minutes after midnight
    public final int endMin;
    public final boolean notify;

    public StudySlot(long id, String subject, int dayMask, int startMin, int endMin,
                     boolean notify) {
        this.id = id;
        this.subject = subject;
        this.dayMask = dayMask;
        this.startMin = startMin;
        this.endMin = endMin;
        this.notify = notify;
    }

    // day: 0 = Monday ... 6 = Sunday
    public boolean onDay(int day) {
        return (dayMask & (1 << day)) != 0;
    }
}