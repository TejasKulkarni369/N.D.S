package com.example.nds;

import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Set;

/**
 * Swipe a reminder card left or right to ignore it. It flies away and stays hidden for the
 * rest of the day (tomorrow it comes back). A different reminder (another text) is not affected.
 *
 * It works on any card, so the layout does not have to be changed:
 *     SwipeIgnore.attach(card, true);
 */
public class SwipeIgnore implements View.OnTouchListener {

    // true while a card is being dragged: the page swipe (Home / Limits / ...) must not fire
    public static volatile boolean dragging = false;

    private static final long CHECK_EVERY_MS = 300;

    private final View card;
    private final int slop;
    private float startRawX, startRawY;
    private boolean drag = false;
    private VelocityTracker velocity;

    // "is this reminder ignored today" is asked at most every 300 ms
    private long checkedAt = 0;
    private boolean lastIgnored = false;

    private SwipeIgnore(View card) {
        this.card = card;
        this.slop = ViewConfiguration.get(card.getContext()).getScaledTouchSlop();
    }

    public static SwipeIgnore attach(View card, boolean addHint) {
        Object old = card.getTag(R.id.swipeIgnoreTag);
        if (old instanceof SwipeIgnore) return (SwipeIgnore) old;

        final SwipeIgnore s = new SwipeIgnore(card);
        card.setTag(R.id.swipeIgnoreTag, s);
        card.setOnTouchListener(s);

        // Home shows the card again and again (setVisibility(VISIBLE)). A reminder that was
        // ignored today is hidden again before the frame is drawn, so it never flashes.
        card.getViewTreeObserver().addOnPreDrawListener(() -> {
            if (card.getVisibility() == View.VISIBLE && s.ignoredToday()) {
                card.setVisibility(View.GONE);
                return false;
            }
            return true;
        });
        card.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                dragging = false;   // never leave the page swipe switched off
            }
        });

        if (addHint && card instanceof LinearLayout) {
            TextView hint = new TextView(card.getContext());
            hint.setText("\u2039  Swipe to ignore  \u203a");
            hint.setTextColor(0xFFC9C9CF);
            hint.setTextSize(11);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            lp.topMargin = (int) (14 * card.getResources().getDisplayMetrics().density);
            ((LinearLayout) card).addView(hint, lp);
        }
        return s;
    }

    // ---------------- which reminder is on the card, and is it ignored today ----------------

    private static String today() {
        return String.valueOf(java.time.LocalDate.now().toEpochDay());
    }

    private static void texts(View v, StringBuilder out) {
        if (v instanceof TextView) out.append(((TextView) v).getText()).append('|');
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) texts(g.getChildAt(i), out);
        }
    }

    private String key() {
        StringBuilder sb = new StringBuilder();
        texts(card, sb);
        return String.valueOf(sb.toString().hashCode());
    }

    private android.content.SharedPreferences sp() {
        return card.getContext().getApplicationContext()
                .getSharedPreferences("ignored_reminders", android.content.Context.MODE_PRIVATE);
    }

    private boolean ignoredToday() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - checkedAt < CHECK_EVERY_MS) return lastIgnored;
        checkedAt = now;
        android.content.SharedPreferences p = sp();
        lastIgnored = today().equals(p.getString("day", ""))
                && p.getStringSet("keys", new HashSet<>()).contains(key());
        return lastIgnored;
    }

    private void ignoreNow() {
        android.content.SharedPreferences p = sp();
        Set<String> keys = today().equals(p.getString("day", ""))
                ? new HashSet<>(p.getStringSet("keys", new HashSet<>())) : new HashSet<>();
        keys.add(key());
        p.edit().putString("day", today()).putStringSet("keys", keys).apply();
        lastIgnored = true;
        checkedAt = android.os.SystemClock.uptimeMillis();
    }

    // ---------------- the swipe ----------------

    private void track(MotionEvent e) {
        if (velocity == null) velocity = VelocityTracker.obtain();
        velocity.addMovement(e);
    }

    // a sideways move that is clearly not an up-down scroll starts the drag
    private boolean startDragIfSideways(MotionEvent e) {
        float dx = e.getRawX() - startRawX;
        float dy = e.getRawY() - startRawY;
        if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
            drag = true;
            dragging = true;
            if (card.getParent() != null) card.getParent().requestDisallowInterceptTouchEvent(true);
            startRawX = e.getRawX();   // the card starts moving from here, without a jump
            return true;
        }
        return false;
    }

    /** For a card that is a ViewGroup: when a button inside got the touch first, the card can still take the swipe. */
    public boolean intercept(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startRawX = e.getRawX();
                startRawY = e.getRawY();
                drag = false;
                break;
            case MotionEvent.ACTION_MOVE:
                track(e);
                if (!drag) startDragIfSideways(e);
                break;
        }
        return drag;
    }

    @Override
    public boolean onTouch(View v, MotionEvent e) {
        track(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startRawX = e.getRawX();
                startRawY = e.getRawY();
                drag = false;
                return true;   // we want the move events that follow

            case MotionEvent.ACTION_MOVE:
                // When nothing inside the card took the touch, the card is the only one asked:
                // there is no intercept step, so the drag has to start here too.
                if (!drag) startDragIfSideways(e);
                if (drag) {
                    float dx = e.getRawX() - startRawX;
                    v.setTranslationX(dx);
                    v.setAlpha(Math.max(0.15f, 1f - Math.abs(dx) / (v.getWidth() * 0.9f)));
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (drag) {
                    velocity.computeCurrentVelocity(1000);
                    float dx = e.getRawX() - startRawX;
                    boolean flick = Math.abs(velocity.getXVelocity()) > 900
                            && Math.signum(velocity.getXVelocity()) == Math.signum(dx);
                    boolean far = Math.abs(dx) > v.getWidth() * 0.33f;
                    if (e.getActionMasked() == MotionEvent.ACTION_UP && (far || flick)) {
                        flyAway(dx >= 0 ? 1 : -1);
                    } else {
                        springBack();
                    }
                }
                drag = false;
                if (velocity != null) {
                    velocity.recycle();
                    velocity = null;
                }
                // the page swipe looks at this on the same touch: clear it after that
                v.post(() -> dragging = false);
                return true;
        }
        return true;
    }

    private void flyAway(int dir) {
        ignoreNow();
        if (!Motion.on(card.getContext())) {
            hide();
            return;
        }
        card.animate().translationX(dir * card.getWidth() * 1.1f).alpha(0f)
                .setDuration(Motion.ms(card.getContext(), 240))
                .setInterpolator(Motion.STANDARD)
                .withEndAction(this::hide)
                .start();
    }

    private void hide() {
        card.setVisibility(View.GONE);
        card.setTranslationX(0f);   // ready for the next reminder
        card.setAlpha(1f);
    }

    private void springBack() {
        card.animate().translationX(0f).alpha(1f)
                .setDuration(Motion.ms(card.getContext(), 260))
                .setInterpolator(Motion.STANDARD)
                .start();
    }
}
