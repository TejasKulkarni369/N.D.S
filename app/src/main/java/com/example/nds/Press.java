package com.example.nds;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

/** A card or button that can be tapped shrinks a little while it is pressed, and springs back. */
public class Press {

    private static final float DOWN = 0.97f;

    public static void apply(View root) {
        if (!Motion.on(root.getContext())) return;
        walk(root);
    }

    private static void walk(View v) {
        if (v.isClickable() && v.hasOnClickListeners() && !(v instanceof RecyclerView)) {
            v.setOnTouchListener((view, e) -> {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().scaleX(DOWN).scaleY(DOWN)
                                .setDuration(90).setInterpolator(Motion.STANDARD).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.animate().scaleX(1f).scaleY(1f)
                                .setDuration(240).setInterpolator(Motion.DECEL).start();
                        break;
                }
                return false;   // the tap itself works exactly as before
            });
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i));
        }
    }
}
