package com.example.nds;

import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

/** The small round buttons: the chosen one is orange glass, the others are flat glass. */
public class Chip {

    public static void style(TextView chip, boolean on, float density, float radiusDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(radiusDp * density);
        if (on) {
            bg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
            bg.setColors(new int[]{0xFFFFA35C, 0xFFFF7A1A});
            bg.setStroke((int) density, 0x66FFFFFF);
        } else {
            bg.setColor(0x14FFFFFF);
            bg.setStroke((int) density, 0x1FFFFFFF);
        }
        chip.setBackground(bg);
        chip.setTextColor(on ? 0xFF1A0E05 : 0xFFC9C9CF);
    }
}
