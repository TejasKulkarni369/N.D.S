package com.example.nds;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.core.widget.NestedScrollView;

import java.util.ArrayList;
import java.util.List;

/**
 * When a page opens, its title and cards rise in one after another (a short stagger) instead of
 * all appearing at once. Only in Quality; at most 9 items so it never feels slow.
 */
public class Reveal {

    private static final int MAX_ITEMS = 9;

    public static void page(View root) {
        Context c = root.getContext();
        if (!Perf.dialogAnim(c) || !Motion.on(c)) return;

        ViewGroup content = scrollContent(root);
        if (content == null) return;

        List<View> items = new ArrayList<>();
        collect(content, items, 0);
        int n = Math.min(items.size(), MAX_ITEMS);
        if (n < 2) return;

        float rise = 14 * c.getResources().getDisplayMetrics().density;
        for (int i = 0; i < n; i++) {
            View v = items.get(i);
            v.setAlpha(0f);
            v.setTranslationY(rise);
        }
        // started once the page is on the screen
        root.post(() -> {
            for (int i = 0; i < n; i++) {
                items.get(i).animate().alpha(1f).translationY(0f)
                        .setStartDelay(i * 38L)
                        .setDuration(Motion.ms(c, 360))
                        .setInterpolator(Motion.DECEL)
                        .start();
            }
        });
    }

    // the one vertical column inside the page's scroll view
    private static ViewGroup scrollContent(View root) {
        if (root instanceof NestedScrollView || root instanceof ScrollView) {
            ViewGroup sv = (ViewGroup) root;
            return sv.getChildCount() > 0 && sv.getChildAt(0) instanceof ViewGroup
                    ? (ViewGroup) sv.getChildAt(0) : null;
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                ViewGroup found = scrollContent(g.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    // cards and titles are the items; a column without a background (just a holder) is opened up
    private static void collect(ViewGroup column, List<View> out, int depth) {
        for (int i = 0; i < column.getChildCount(); i++) {
            View v = column.getChildAt(i);
            if (v.getVisibility() == View.GONE) continue;
            boolean holder = v instanceof ViewGroup && v.getBackground() == null
                    && !(v instanceof androidx.recyclerview.widget.RecyclerView)
                    && ((ViewGroup) v).getChildCount() > 1 && depth < 2;
            if (holder) collect((ViewGroup) v, out, depth + 1);
            else out.add(v);
        }
    }
}
