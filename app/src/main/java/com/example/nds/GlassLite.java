package com.example.nds;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Swaps the layered glass backgrounds for one-layer ones when the Optimizations mode is
 * Balanced or Performance. The cards look the same shape and colour, but draw much faster
 * on weak phones (a card is 1 layer instead of 4). Quality keeps the full glass.
 */
public class GlassLite {

    // full glass -> lite
    private static final int[][] PAIRS = {
            {R.drawable.glass_card, R.drawable.glass_card_lite},
            {R.drawable.glass_card_accent, R.drawable.glass_card_lite},
            {R.drawable.glass_card_locked, R.drawable.glass_card_locked_lite},
            {R.drawable.glass_sheet, R.drawable.glass_sheet_lite},
            {R.drawable.nav_bg, R.drawable.nav_bg_lite},
            {R.drawable.nav_active, R.drawable.nav_active_lite},
            {R.drawable.btn_orange, R.drawable.btn_orange_lite},
            {R.drawable.btn_danger, R.drawable.btn_danger_lite},
            {R.drawable.bg_focus_btn, R.drawable.bg_focus_btn_lite},
            {R.drawable.bg_settings_btn, R.drawable.bg_settings_btn_lite},
    };

    private static Drawable.ConstantState[] full;
    private static Drawable.ConstantState[] lite;

    // what the screens were built with the last time (to know when a restart is needed)
    private static volatile boolean appliedLite = false;

    // true when the mode changed between full glass and lite glass
    public static boolean styleChanged(Context c) {
        return Perf.lite(c) != appliedLite;
    }

    public static void apply(Context c, View root) {
        boolean on = Perf.lite(c);
        appliedLite = on;
        if (root == null || !on) return;
        init(c);
        walk(root);
    }

    public static void applyDialog(Dialog d) {
        Window w = d.getWindow();
        if (w != null) apply(d.getContext(), w.getDecorView());
    }

    private static synchronized void init(Context c) {
        if (full != null) return;
        Resources r = c.getResources();
        full = new Drawable.ConstantState[PAIRS.length];
        lite = new Drawable.ConstantState[PAIRS.length];
        for (int i = 0; i < PAIRS.length; i++) {
            full[i] = r.getDrawable(PAIRS[i][0], c.getTheme()).getConstantState();
            lite[i] = r.getDrawable(PAIRS[i][1], c.getTheme()).getConstantState();
        }
    }

    private static final RecyclerView.OnChildAttachStateChangeListener LISTENER =
            new RecyclerView.OnChildAttachStateChangeListener() {
                @Override
                public void onChildViewAttachedToWindow(View view) {
                    walk(view);   // a new list row: same swap
                }

                @Override
                public void onChildViewDetachedFromWindow(View view) {
                }
            };

    private static void walk(View v) {
        swap(v);

        if (v instanceof RecyclerView && v.getTag(R.id.glassLiteTag) == null) {
            v.setTag(R.id.glassLiteTag, Boolean.TRUE);
            ((RecyclerView) v).addOnChildAttachStateChangeListener(LISTENER);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i));
        }
    }

    private static void swap(View v) {
        Drawable bg = v.getBackground();
        if (bg == null) return;
        Drawable.ConstantState cs = bg.getConstantState();
        if (cs == null) return;
        for (int i = 0; i < full.length; i++) {
            if (cs == full[i]) {
                v.setBackground(lite[i].newDrawable(v.getResources()));
                return;
            }
        }
    }
}
