package com.example.nds;

public class AppUsage {
    public final String pkg;
    public final String name;
    public final int usedMin;
    public final int limitMin;

    public AppUsage(String pkg, String name, int usedMin, int limitMin) {
        this.pkg = pkg;
        this.name = name;
        this.usedMin = usedMin;
        this.limitMin = limitMin;
    }
}