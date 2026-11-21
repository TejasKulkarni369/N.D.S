package com.example.nds;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.LinearLayout;

/**
 * A card that can be swiped away. The work is in SwipeIgnore, which also works on a plain
 * LinearLayout, so this class is only needed if a layout names it.
 */
public class DismissibleCard extends LinearLayout {

    private final SwipeIgnore swipe;

    public DismissibleCard(Context c) {
        super(c);
        swipe = SwipeIgnore.attach(this, false);
    }

    public DismissibleCard(Context c, AttributeSet a) {
        super(c, a);
        swipe = SwipeIgnore.attach(this, false);
    }

    // a button inside the card got the touch first: the card can still take a sideways swipe
    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        return swipe.intercept(e);
    }
}
