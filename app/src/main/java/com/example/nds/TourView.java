package com.example.nds;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.content.Context;
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
        final int since;           // the tour version that introduced this card (1 = first release)
        final boolean updateOnly;  // shown only to people who update, not to a fresh install

        Step(int since, boolean updateOnly, int targetId, boolean useParent,
             String title, String text) {
            this.since = since;
            this.updateOnly = updateOnly;
            this.targetId = targetId;
            this.useParent = useParent;
            this.title = title;
            this.text = text;
        }

        // a card of the first release
        Step(int targetId, boolean useParent, String title, String text) {
            this(1, false, targetId, useParent, title, text);
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
    private final RectF holeEnd = new RectF();   // where the spotlight is going
    private ValueAnimator holeAnim;
    private float lastCardY = Float.NaN;
    private boolean hasHole = false;
    private float holeRadius = 0f;

    private boolean started = false;
    private boolean finished = false;
    private int index = 0;

    private final View card;
    private final TextView counter, title, text, skip, next, back;

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
        back = card.findViewById(R.id.tourBack);

        next.setOnClickListener(v -> {
            if (index >= steps.size() - 1) finish();
            else showStep(index + 1);
        });
        skip.setOnClickListener(v -> finish());

        // go back one card and read the tip again
        back.setOnClickListener(v -> {
            if (index > 0) showStep(index - 1);
        });
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
        back.setVisibility(i == 0 ? View.GONE : View.VISIBLE);   // nothing before the first card

        // spotlight: it glides from the last target to the next one
        float[] to = null;
        float toRadius = 0f;
        View target = resolve(s);
        if (target != null) {
            int[] tl = new int[2];
            int[] me = new int[2];
            target.getLocationInWindow(tl);
            getLocationInWindow(me);
            float pad = 6 * d;
            RectF r = new RectF(tl[0] - me[0] - pad, tl[1] - me[1] - pad,
                    tl[0] - me[0] + target.getWidth() + pad,
                    tl[1] - me[1] + target.getHeight() + pad);
            // keep the hole on the screen
            if (r.intersect(0, 0, getWidth(), getHeight()) && r.height() > 0) {
                to = new float[]{r.left, r.top, r.right, r.bottom};
                toRadius = (s.targetId == R.id.focusBtn) ? r.width() / 2f : 24 * d;
            }
        }
        moveSpotlight(to, toRadius);

        // place the card once it is measured
        card.animate().cancel();
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

    // moves the spotlight (hole + its radius) to the new target; null = no spotlight on this card
    private void moveSpotlight(float[] to, float toRadius) {
        if (holeAnim != null) holeAnim.cancel();

        if (to == null) {
            hasHole = false;
            invalidate();
            return;
        }
        holeEnd.set(to[0], to[1], to[2], to[3]);

        final float[] from;
        final float fromRadius;
        if (hasHole) {
            from = new float[]{hole.left, hole.top, hole.right, hole.bottom};
            fromRadius = holeRadius;
        } else {   // the first spotlight grows out of the middle of its target
            float cx = (to[0] + to[2]) / 2f;
            float cy = (to[1] + to[3]) / 2f;
            from = new float[]{cx - 24 * d, cy - 24 * d, cx + 24 * d, cy + 24 * d};
            fromRadius = 24 * d;
        }
        hasHole = true;

        if (!Motion.on(act)) {
            hole.set(to[0], to[1], to[2], to[3]);
            holeRadius = toRadius;
            invalidate();
            return;
        }

        final float[] end = to;
        final float endRadius = toRadius;
        holeAnim = ValueAnimator.ofFloat(0f, 1f);
        holeAnim.setDuration(Motion.ms(act, 460));
        holeAnim.setInterpolator(Motion.STANDARD);
        holeAnim.addUpdateListener(an -> {
            float k = (float) an.getAnimatedValue();
            hole.set(from[0] + (end[0] - from[0]) * k, from[1] + (end[1] - from[1]) * k,
                    from[2] + (end[2] - from[2]) * k, from[3] + (end[3] - from[3]) * k);
            holeRadius = fromRadius + (endRadius - fromRadius) * k;
            invalidate();
        });
        holeAnim.start();
    }

    private void placeCard() {
        int h = card.getHeight();
        int total = getHeight();
        float margin = 14 * d;

        float y;
        if (!hasHole) {
            y = (total - h) / 2f;                 // centred
        } else if (holeEnd.centerY() > total / 2f) {
            y = holeEnd.top - h - margin;         // target in the lower half: card above it
        } else {
            y = holeEnd.bottom + margin;          // target in the upper half: card below it
        }
        float maxY = total - h - 24 * d;
        y = Math.max(40 * d, Math.min(y, maxY));

        float to = y - card.getTop();
        if (!Motion.on(act)) {
            card.setTranslationY(to);
            card.setAlpha(1f);
            lastCardY = to;
            return;
        }
        // the card fades in while it slides from where the last card was
        float from = Float.isNaN(lastCardY) ? to + 18 * d : lastCardY;
        card.setTranslationY(from);
        lastCardY = to;
        card.animate().translationY(to).alpha(1f)
                .setDuration(Motion.ms(act, 400)).setInterpolator(Motion.STANDARD).start();
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

    // ------------------------------------------------------------------
    // What the person sees:
    //  - a fresh install: every card
    //  - after an update: ONLY the cards added since the version they last saw
    // To add a feature card in a later update: raise TourStore.CURRENT by 1 and add the
    // card with  new Step(NEW_NUMBER, false, 0, false, "Title", "Text").
    // ------------------------------------------------------------------
    public static List<Step> stepsFor(Context c) {
        int seen = TourStore.seenVersion(c);
        boolean update = seen > 0;

        List<Step> out = new ArrayList<>();
        out.add(update
                ? new Step(0, false, "N.D.S was updated",
                        "Here is only what is new since the last time. It takes a minute.")
                : new Step(0, false, "Welcome to N.D.S",
                        "Block your Distractions. This short tour shows every feature of the "
                                + "app. Tap Next."));

        for (Step s : allSteps()) {
            if (s.since <= seen) continue;               // already seen
            if (s.updateOnly && !update) continue;       // a fresh install does not need it
            out.add(s);
        }

        if (update) {
            out.add(new Step(0, false, "That's everything new",
                    "Be in your limits!"));
        }
        return out;
    }

    // every card of the app, in order (the welcome and update cards are added above)
    private static List<Step> allSteps() {
        List<Step> s = new ArrayList<>();

        // ===== version 1: the first release =====

        s.add(new Step(0, false, "Permissions",
                "N.D.S needs Usage access (to measure your apps), Display over other apps "
                        + "(for the lock screen) and notifications. Home asks for them one by "
                        + "one. Everything stays on your phone."));

        s.add(new Step(R.id.ring, true, "Screen time today",
                "The big number is your total screen time today, across all your apps. "
                        + "The ring around it fills up as you use your app limits."));

        s.add(new Step(R.id.balanceCard, false, "Today's balance",
                "See how your day splits between Productive, Distracting and Others. Tap this "
                        + "card to list every app you used today, with how many times you "
                        + "opened it and its screen time."));

        s.add(new Step(R.id.appList, false, "Your app limits",
                "Apps you mark as Distracting appear here with a limit bar. Tap one for its "
                        + "details: Opened, Notifications, Screen time and a 7-day History."));

        s.add(new Step(R.id.navLimits, false, "Limits",
                "Change each app's limit. Lowering applies now, raising applies tomorrow. "
                        + "Once a limit is used up, the app is locked until tomorrow. "
                        + "Add app opens the list where you tag every app as Productive, "
                        + "Distracting or Other."));

        s.add(new Step(R.id.navTasks, false, "Tasks",
                "Plan your work with a title, a description and checkpoints you can tick. "
                        + "Pick a time and a reminder with its own sound pops up."));

        s.add(new Step(R.id.navApps, false, "Time Table",
                "Build your weekly study plan: subject, days, start and end time. You get a "
                        + "reminder 5 minutes before, at the start and at the end."));

        s.add(new Step(R.id.focusBtn, false, "Focus mode",
                "Tap this round button to block the whole phone except Phone, Contacts and "
                        + "1 to 5 emergency apps. Apps you marked as Distracting can't be "
                        + "emergency apps. Set a timer, and use the stopwatch to time your "
                        + "work. It only opens with a tap, never with a swipe."));

        s.add(new Step(0, false, "Emergency Exit",
                "Need to leave Focus? You get 3 Emergency Exits a week, reset every Monday. "
                        + "A warning card shows first, and the number left is on Home."));

        s.add(new Step(0, false, "When time is up",
                "A locked app shows a grey logo with a red lock. If you open it, the Time's up "
                        + "screen appears and the app closes. In a real emergency you get one "
                        + "Emergency Bypass a day: 15 minutes for one app, after a warning."));

        s.add(new Step(R.id.settingsBtn, false, "Settings",
                "Turn permissions on and off, choose how smooth N.D.S looks (Quality, Balanced "
                        + "or Performance) and run this tour again. Settings stays locked while "
                        + "Focus is on."));

        s.add(new Step(0, false, "Protection",
                "Turn on Device admin and N.D.S cannot be uninstalled. From 90% of a limit it "
                        + "also blocks the Settings pages that could switch off its permissions, "
                        + "until tomorrow. You can still uninstall your other apps. Accessibility "
                        + "is not needed, so banking and UPI apps keep working."));

        s.add(new Step(0, false, "Swipe and tips",
                "Swipe left or right anywhere to change pages. The N.D.S icon on your home "
                        + "screen changes as your limit fills up."));

        s.add(new Step(0, false, "You're all set",
                "Be in your limits!"));

        // ===== version 2: this update =====

        s.add(new Step(2, false, 0, false, "Home screen widgets",
                "Long-press your home screen, open Widgets and find N.D.S. Add Screen time, "
                        + "Tasks, Time Table, Focus mode or the screen time of one app. They fit "
                        + "from 1x1 and show more when you stretch them."));

        s.add(new Step(2, false, 0, false, "Lock in your study time",
                "In Time Table, tick Want to lock in and your phone goes into Focus from the "
                        + "start to the end of that session. Add chapters under the subject "
                        + "and tick them as you finish."));

        s.add(new Step(2, false, 0, false, "Daily goals",
                "Open the Limits tab and tap Goals. Set a screen time goal for each day of the week, and "
                        + "how many times a day you want to open an app. N.D.S warns you when "
                        + "you get close. A goal never locks anything, a limit does."));

        s.add(new Step(2, false, 0, false, "Drafts are saved",
                "If you leave a task or a study session half written, N.D.S keeps it. Open "
                        + "New task or New session again and it is back."));

        s.add(new Step(2, false, 0, false, "Privacy & offline mode",
                "N.D.S works fully offline. It has no internet access, no account and no "
                        + "login, and it never sends anything out of your phone. Your screen "
                        + "time, limits, tasks and time table are stored only on this phone, "
                        + "and uninstalling N.D.S deletes all of it.\n\n"
                        + "Usage access only measures your app time. Notification access only "
                        + "counts notifications per app and never reads what they say."));

        // only for people who already had the app (a fresh install reads this above)
        s.add(new Step(2, true, 0, false, "Stricter Focus",
                "Apps you marked as Distracting can no longer be picked as emergency apps, "
                        + "and the Settings button is locked while Focus is on."));

        s.add(new Step(2, true, R.id.settingsBtn, false, "Permissions in Settings",
                "Settings now lists every permission with its status and takes you to the "
                        + "right screen to turn it on or off. The Optimizations modes are tucked "
                        + "away: tap Optimizations to open them."));

        s.add(new Step(2, true, 0, false, "Banking and UPI apps",
                "N.D.S no longer needs Accessibility. Keep it switched off and your banking "
                        + "and UPI apps will not complain. Protection still blocks the "
                        + "sensitive Settings pages."));

        s.add(new Step(2, true, 0, false, "Lighter and faster",
                "N.D.S now uses much less battery and no longer slows down other apps."));

        return s;
    }
}
