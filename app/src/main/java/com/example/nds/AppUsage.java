package com.example.nds;

public class AppUsage {
    public final String pkg;
    public final String name;
    public final long usedMs;
    public final int usedMin;
    public final int limitMin;

    public AppUsage(String pkg, String name, long usedMs, int limitMin) {
        this.pkg = pkg;
        this.name = name;
        this.usedMs = usedMs;
        this.usedMin = (int) (usedMs / 60000);
        this.limitMin = limitMin;
    }
}