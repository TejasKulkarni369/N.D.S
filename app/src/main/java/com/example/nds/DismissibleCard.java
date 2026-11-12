package com.example.nds;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

/**
 * The reminder card on Home ("turn on Usage access", "turn on Device admin"...).
 * Swipe it left or right to ignore it: it flies away and stays hidden for the rest of the day.
 * Tomorrow it comes back. A different reminder (another text) is not affected.
 */
public class DismissibleCard extends LinearLayout {

    // true while the card is being dragged: the page swipe (Home / Limits / ...) must not fire
    public static volatile boolean dragging = false;

    private final int slop;
    private float startRawX, startX, startY;
    private boolean drag = false;
    private VelocityTracker velocity;

    public DismissibleCard(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
    }

    public DismissibleCard(Context c, AttributeSet a) {
        super(c, a);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
    }

    // ---------------- which reminder is on the card, and is it ignored today ----------------

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("ignored_reminders", Context.MODE_PRIVATE);
    }

    private static String today() {
        Calendar n = Calendar.getInstance();
        return n.get(Calendar.YEAR) + "-" + n.get(Calendar.DAY_OF_YEAR);
    }

    private static void texts(View v, StringBuilder out) {
        if (v instanceof TextView) out.append(((TextView) v).getText()).append('|');
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) texts(g.getChildAt(i), out);
        }
    }

    private String key() {
        StringBuilder sb = new StringBuilder();
        texts(this, sb);
        return String.valueOf(sb.toString().hashCode());
    }

    private boolean ignoredToday() {
        SharedPreferences p = sp(getContext());
        return today().equals(p.getString("day", ""))
                && p.getStringSet("keys", new HashSet<>()).contains(key());
    }

    private void ignoreNow() {
        SharedPreferences p = sp(getContext());
        Set<String> keys = today().equals(p.getString("day", ""))
                ? new HashSet<>(p.getStringSet("keys", new HashSet<>())) : new HashSet<>();
        keys.add(key());
        p.edit().putString("day", today()).putStringSet("keys", keys).apply();
    }

    // Home shows the card by calling setVisibility(VISIBLE). If this reminder was ignored today
    // it is hidden again before the first frame is drawn (so it never flashes).
    @Override
    public void setVisibility(int v) {
        super.setVisibility(v);
        if (v != VISIBLE) return;
        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                getViewTreeObserver().removeOnPreDrawListener(this);
                if (getVisibility() == VISIBLE && ignoredToday()) {
                    DismissibleCard.super.setVisibility(GONE);
                    return false;
                }
                return true;
            }
        });
    }

    // ---------------- the swipe ----------------

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = e.getX();
                startY = e.getY();
                startRawX = e.getRawX();
                drag = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - startX;
                float dy = e.getY() - startY;
                // only a clearly sideways move: taps on the buttons and up-down scrolling stay normal
                if (!drag && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    drag = true;
                    dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    startRawX = e.getRawX();
                    return true;
                }
                break;
        }
        return drag;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (velocity == null) velocity = VelocityTracker.obtain();
        velocity.addMovement(e);

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return true;   // keep receiving the move events
            case MotionEvent.ACTION_MOVE:
                if (drag) {
                    float dx = e.getRawX() - startRawX;
                    setTranslationX(dx);
                    setAlpha(Math.max(0.15f, 1f - Math.abs(dx) / (getWidth() * 0.9f)));
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (drag) {
                    velocity.computeCurrentVelocity(1000);
                    float dx = e.getRawX() - startRawX;
                    boolean flick = Math.abs(velocity.getXVelocity()) > 900
                            && Math.signum(velocity.getXVelocity()) == Math.signum(dx);
                    boolean far = Math.abs(dx) > getWidth() * 0.33f;
                    if (e.getActionMasked() == MotionEvent.ACTION_UP && (far || flick)) {
                        flyAway(dx >= 0 ? 1 : -1);
                    } else {
                        springBack();
                    }
                }
                drag = false;
                velocity.recycle();
                velocity = null;
                // the page swipe checks this on the same touch: clear it after that
                post(() -> dragging = false);
                return true;
        }
        return true;
    }

    private void flyAway(int dir) {
        ignoreNow();
        if (!Motion.on(getContext())) {
            finishHide();
            return;
        }
        animate().translationX(dir * getWidth() * 1.1f).alpha(0f)
                .setDuration(Motion.ms(getContext(), 240))
                .setInterpolator(Motion.STANDARD)
                .withEndAction(this::finishHide)
                .start();
    }

    private void finishHide() {
        super.setVisibility(GONE);
        setTranslationX(0f);   // ready for the next reminder
        setAlpha(1f);
    }

    private void springBack() {
        animate().translationX(0f).alpha(1f)
                .setDuration(Motion.ms(getContext(), 260))
                .setInterpolator(Motion.STANDARD)
                .start();
    }
}
