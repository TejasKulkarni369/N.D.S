package com.example.nds;

import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

/**
 * A list that is refreshed every second (the app list on Home) must not animate a changed row.
 * Android's default does: it fades the old row out and the new one in, so every row "blinks"
 * once a second. This turns that animation off for every list in the app.
 */
public class Calm {

    public static void lists(View root) {
        if (root instanceof RecyclerView) {
            ((RecyclerView) root).setHasFixedSize(false);   // the list sizes itself to its rows
            RecyclerView.ItemAnimator a = ((RecyclerView) root).getItemAnimator();
            if (a instanceof SimpleItemAnimator) {
                ((SimpleItemAnimator) a).setSupportsChangeAnimations(false);
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) lists(g.getChildAt(i));
        }
    }
}
