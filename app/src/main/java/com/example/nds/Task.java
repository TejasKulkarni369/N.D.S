package com.example.nds;

import java.util.ArrayList;
import java.util.List;

public class Task {

    public static class Check {
        public final String text;
        public final boolean done;

        public Check(String text, boolean done) {
            this.text = text;
            this.done = done;
        }
    }

    public final long id;
    public final String title;
    public final String description;
    public final long timeMs;
    public final boolean done;
    public final List<Check> checks;

    public Task(long id, String title, String description, long timeMs,
                boolean done, List<Check> checks) {
        this.id = id;
        this.title = title;
        this.description = description == null ? "" : description;
        this.timeMs = timeMs;
        this.done = done;
        this.checks = checks == null ? new ArrayList<>() : checks;
    }

    public int checksDone() {
        int n = 0;
        for (Check c : checks) {
            if (c.done) n++;
        }
        return n;
    }
}