package com.example.nds;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

public class ExitsLeftView extends TextView {

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            update();
            postDelayed(this, 1000);
        }
    };

    public ExitsLeftView(Context context) {
        super(context);
    }

    public ExitsLeftView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refresh.run();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(refresh);
        super.onDetachedFromWindow();
    }

    private void update() {
        int left = ExitStore.left(getContext());
        setText("Emergency exits left this week: " + left + "/" + ExitStore.WEEKLY);
        setTextColor(ContextCompat.getColor(getContext(),
                left == 0 ? R.color.locked : R.color.orange_soft));
    }
}