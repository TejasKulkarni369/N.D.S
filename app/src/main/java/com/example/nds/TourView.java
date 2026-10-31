package com.example.nds;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class TourView extends FrameLayout {

    public static class Step {
        final int targetId;        // the view to spotlight (0 = none)
        final boolean useParent;   // spotlight the card around that view
        final String title;
        final String text;

        Step(int targetId, boolean useParent, String title, String text) {
            this.targetId = targetId;
            this.useParent = useParent;
            this.title = title;
            this.text = text;
        }
    }

    public interface Listener {
        void onFinished();
    }

    private final Activity act;
    private final List<Step> steps;
    private final Listener listener;
    private final float d;

    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF hole = new RectF();
    private boolean hasHole = false;
    private float holeRadius = 0f;

    private boolean started = false;
    private boolean finished = false;
    private int index = 0;

    private final View card;
    private final TextView counter, title, text, skip, next;

    public TourView(Activity act, List<Step> steps, Listener listener) {
        super(act);
        this.act = act;
        this.steps = steps;
        this.listener = listener;
        d = act.getResources().getDisplayMetrics().density;

        setLayerType(LAYER_TYPE_HARDWARE, null);   // needed to cut the spotlight hole
        setWillNotDraw(false);
        setClickable(true);                        // the app underneath ignores touches

        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        ringPaint.setStyle(Paint.Style.STROKE);

        card = LayoutInflater.from(act).inflate(R.layout.view_tour_card, this, false);
        addView(card);
        counter = card.findViewById(R.id.tourCounter);
        title = card.findViewById(R.id.tourTitle);
        text = card.findViewById(R.id.tourText);
        skip = card.findViewById(R.id.tourSkip);
        next = card.findViewById(R.id.tourNext);

        next.setOnClickListener(v -> {
            if (index >= steps.size() - 1) finish();
            else showStep(index + 1);
        });
        skip.setOnClickListener(v -> finish());
    }

    public void start() {
        started = true;
        post(() -> showStep(0));
    }

    public void finish() {
        if (finished) return;
        finished = true;
        animate().alpha(0f).setDuration(250).withEndAction(() -> {
            ViewGroup p = (ViewGroup) getParent();
            if (p != null) p.removeView(this);
            if (listener != null) listener.onFinished();
        }).start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (started && !finished && w > 0 && h > 0) post(() -> showStep(index));
    }

    // the view of this step, or null when it is not on the screen
    private View resolve(Step s) {
        if (s.targetId == 0) return null;
        View t = act.findViewById(s.targetId);
        if (t == null) return null;
        if (s.useParent && t.getParent() instanceof View) t = (View) t.getParent();
        if (!t.isShown() || t.getWidth() <= 0 || t.getHeight() <= 0) return null;
        return t;
    }

    private void showStep(int i) {
        if (finished) return;
        index = i;
        Step s = steps.get(i);
        boolean last = (i == steps.size() - 1);

        counter.setText((i + 1) + " / " + steps.size());
        title.setText(s.title);
        text.setText(s.text);
        next.setText(last ? "Done" : "Next");
        skip.setVisibility(last ? View.GONE : View.VISIBLE);

        // spotlight
        hasHole = false;
        View target = resolve(s);
        if (target != null) {
            int[] t = new int[2];
            int[] me = new int[2];
            target.getLocationInWindow(t);
            getLocationInWindow(me);
            float pad = 6 * d;
            hole.set(t[0] - me[0] - pad, t[1] - me[1] - pad,
                    t[0] - me[0] + target.getWidth() + pad,
                    t[1] - me[1] + target.getHeight() + pad);
            // keep the hole on the screen
            if (hole.intersect(0, 0, getWidth(), getHeight()) && hole.height() > 0) {
                hasHole = true;
                holeRadius = (s.targetId == R.id.focusBtn) ? hole.width() / 2f : 24 * d;
            }
        }
        invalidate();

        // place the card once it is measured
        card.setAlpha(0f);
        card.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                card.getViewTreeObserver().removeOnPreDrawListener(this);
                placeCard();
                return true;
            }
        });
        card.requestLayout();
    }

    private void placeCard() {
        int h = card.getHeight();
        int total = getHeight();
        float margin = 14 * d;

        float y;
        if (!hasHole) {
            y = (total - h) / 2f;                 // centred
        } else if (hole.centerY() > total / 2f) {
            y = hole.top - h - margin;            // target in the lower half: card above it
        } else {
            y = hole.bottom + margin;             // target in the upper half: card below it
        }
        float maxY = total - h - 24 * d;
        y = Math.max(40 * d, Math.min(y, maxY));

        card.setTranslationY(y - card.getTop());
        card.animate().alpha(1f).setDuration(220).start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xD90D0D0F);
        if (hasHole) {
            canvas.drawRoundRect(hole, holeRadius, holeRadius, clearPaint);   // the spotlight
            ringPaint.setColor(0x55FF7A1A);
            ringPaint.setStrokeWidth(8 * d);
            canvas.drawRoundRect(hole, holeRadius, holeRadius, ringPaint);
            ringPaint.setColor(0xFFFF7A1A);
            ringPaint.setStrokeWidth(2 * d);
            canvas.drawRoundRect(hole, holeRadius, holeRadius, ringPaint);
        }
    }

    // every feature of the app, in order
    public static List<Step> defaultSteps() {
        List<Step> s = new ArrayList<>();

        s.add(new Step(0, false, "Welcome to N.D.S",
                "Block your Distractions. This short tour shows every feature of the app. "
                        + "Tap Next."));

        s.add(new Step(0, false, "Permissions",
                "N.D.S needs Usage access (to measure your apps), Display over other apps "
                        + "(for the lock screen) and notifications. Home asks for them one by "
                        + "one. Everything stays on your phone: no internet, no account."));

        s.add(new Step(R.id.settingsBtn, false, "Settings",
                "Tap this card for Optimizations: Quality, Balanced or Performance. "
                        + "Quality is the smoothest, Performance is the fastest."));

        s.add(new Step(R.id.ring, true, "Total screen time",
                "The big number is your total screen time today. The ring shows how much of "
                        + "your app limits is left, and it glows slowly."));

        s.add(new Step(R.id.balanceCard, false, "Today's balance",
                "See how your day splits between Productive, Distracting and Others. Tap this "
                        + "card to list every app you used today, with how many times you "
                        + "opened it and its screen time."));

        s.add(new Step(R.id.appList, false, "Your app limits",
                "Apps you mark as Distracting appear here with a limit bar. Tap one for its "
                        + "details: Opened, Notifications, Screen time and a 7-day History."));

        s.add(new Step(R.id.navApps, false, "Time Table",
                "Plan your weekly study sessions. N.D.S notifies you 5 minutes before, at "
                        + "the start and at the end of each session."));

        s.add(new Step(R.id.navTasks, false, "Tasks",
                "Plan your work with a title, a description and checkpoints you can tick. "
                        + "Pick a time and a reminder with its own sound pops up."));

        s.add(new Step(R.id.navApps, false, "Apps",
                "Tag every app as Productive, Distracting or Other. Distracting apps get a "
                        + "limit and show up on Home."));

        s.add(new Step(R.id.focusBtn, false, "Focus mode",
                "Tap this round button to block the whole phone except Phone, Contacts and "
                        + "1 to 5 emergency apps. Set a timer, and use the stopwatch to time "
                        + "your work. It only opens with a tap, never with a swipe."));

        s.add(new Step(0, false, "Emergency Exit",
                "Inside Focus mode you can leave early in a real emergency. You get 3 "
                        + "emergency exits a week, and the number left shows on Home and "
                        + "before you start Focus."));

        s.add(new Step(R.id.navTasks, false, "Study timetable",
                "Open Tasks and tap Study timetable to plan your weekly study sessions. "
                        + "N.D.S notifies you 5 minutes before, at the start and at the end "
                        + "of each session."));

        s.add(new Step(0, false, "When time is up",
                "A locked app shows a grey logo with a red lock. If you open it, the Time's up "
                        + "screen appears and the app closes. In a real emergency you get one "
                        + "Emergency Bypass a day: 15 minutes for one app, after a warning."));

        s.add(new Step(0, false, "Protection",
                "From 90% of a limit, N.D.S protects itself: the Settings screens that could "
                        + "uninstall it or switch off its permissions are blocked until "
                        + "tomorrow. Turn on Device admin and Accessibility on Home to enable it."));

        s.add(new Step(0, false, "Swipe and tips",
                "Swipe left or right anywhere to change pages. The N.D.S icon on your home "
                        + "screen changes as your limit fills up."));

        s.add(new Step(0, false, "Privacy & offline mode",
                "N.D.S works fully offline. It has no internet access, no account and no "
                        + "login, and it never sends anything out of your phone. Your screen "
                        + "time, limits, tasks and time table are stored only on this phone, "
                        + "and uninstalling N.D.S deletes all of it.\n\n"
                        + "Usage access only measures your app time. Notification access only "
                        + "counts notifications per app and never reads what they say. "
                        + "Accessibility, if you turn it on, only guards the Settings screens "
                        + "and closes locked apps."));

        s.add(new Step(0, false, "You're all set",
                "Start by tagging your Distracting apps on the Apps tab. Be in your limits!"));

        return s;
    }
}