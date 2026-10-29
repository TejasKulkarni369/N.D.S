package com.example.nds;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

public class MainActivity extends AppCompatActivity {

    private static final int TAB_COUNT = 4;   // 0 Home, 1 Limits, 2 Tasks, 3 Time Table
    private static final int SLOTS = 5;       // nav slots: the middle one holds the Focus button
    private static final int[] SLOT = {0, 1, 3, 4};   // tab -> nav slot

    private int current = 0;
    private int pillW = 0;
    private View navBar, navPill;
    private final TextView[] tabs = new TextView[TAB_COUNT];
    private GestureDetector swipe;
    private TourView tour;   // the first-run tour (null when it is not showing)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // system splash is just the dark background; our own intro takes over
        splash.setOnExitAnimationListener(provider -> provider.remove());

        // system bars AND the keyboard push the content; the Settings card follows the status bar
        View content = findViewById(R.id.content);
        View settingsBtn = findViewById(R.id.settingsBtn);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            content.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            settingsBtn.setTranslationY(bars.top);
            settingsBtn.setTranslationX(-bars.right);
            return insets;
        });

        navBar = findViewById(R.id.navBar);
        navPill = findViewById(R.id.navPill);
        tabs[0] = findViewById(R.id.navHome);
        tabs[1] = findViewById(R.id.navLimits);
        tabs[2] = findViewById(R.id.navTasks);
        tabs[3] = findViewById(R.id.navApps);   // the Time Table tab

        if (savedInstanceState != null) {
            current = savedInstanceState.getInt("tab", 0);
        } else {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new HomeFragment())
                    .commit();
            if (Perf.intro(this)) {
                prepareEntrance();
                showIntro();
            } else {
                // Balanced / Performance: no intro, the app is simply there
                navBar.postDelayed(this::maybeStartTour, 600);
            }
        }
        updateNavColors();

        // size the sliding pill to one nav slot
        navBar.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            int inner = (r - l) - navBar.getPaddingLeft() - navBar.getPaddingRight();
            int w = inner / SLOTS;
            if (w > 0 && w != pillW) {
                pillW = w;
                ViewGroup.LayoutParams lp = navPill.getLayoutParams();
                lp.width = w;
                navPill.setLayoutParams(lp);
                navPill.setTranslationX(SLOT[current] * w);
            }
        });

        for (int i = 0; i < TAB_COUNT; i++) {
            final int idx = i;
            tabs[i].setOnClickListener(v -> select(idx));
        }

        // the round Focus button: a plain click only, swipes never reach it
        findViewById(R.id.focusBtn).setOnClickListener(v -> askFocus());

        // the Settings card in the top-right corner
        settingsBtn.setOnClickListener(v -> openSettings());

        // opened from a task reminder: jump to that tab
        final int wanted = getIntent().getIntExtra("tab", 0);
        if (savedInstanceState == null && wanted > 0 && wanted < TAB_COUNT) {
            navBar.post(() -> select(wanted));
        }

        // swipe anywhere: left = next page, right = previous page
        swipe = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float vx, float vy) {
                if (e1 == null) return false;
                // no page swipes during the tour or while an editor/Settings page is open
                if (tour != null) return false;
                if (getSupportFragmentManager().getBackStackEntryCount() > 0) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                float minDist = 100 * getResources().getDisplayMetrics().density;
                if (Math.abs(dx) > minDist
                        && Math.abs(dx) > Math.abs(dy) * 1.5f
                        && Math.abs(vx) > 600) {
                    if (dx < 0 && current < TAB_COUNT - 1) select(current + 1);
                    else if (dx > 0 && current > 0) select(current - 1);
                    return true;
                }
                return false;
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (tour != null) {            // Back skips the tour
                    tour.finish();
                    return;
                }
                FragmentManager fm = getSupportFragmentManager();
                if (fm.getBackStackEntryCount() > 0) fm.popBackStack();   // close the open page
                else if (current != 0) select(0);
                else finish();
            }
        });

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        swipe.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // during Focus the app itself shows only the Focus screen
        if (FocusStore.isActive(this)) {
            startActivity(new Intent(this, FocusActivity.class));
            return;
        }

        if (PermissionUtils.hasUsageAccess(this) && PermissionUtils.hasOverlayAccess(this)) {
            ContextCompat.startForegroundService(this, new Intent(this, BlockService.class));
        }
    }

    // the Optimizations mode was changed: update what is already on the screen
    public void onPerfChanged() {
        View bg = ((ViewGroup) findViewById(R.id.main)).getChildAt(0);
        if (bg instanceof GlowBackgroundView) ((GlowBackgroundView) bg).refreshMode();
    }

    // a page transaction with the slide animations only when the mode allows them
    private FragmentTransaction pageTx() {
        FragmentTransaction t = getSupportFragmentManager().beginTransaction();
        if (Perf.pageAnim(this)) {
            t.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left,
                    R.anim.slide_in_left, R.anim.slide_out_right);
        }
        return t;
    }

    // the first time the app is opened after installing: tour of every feature
    private void maybeStartTour() {
        if (isFinishing() || tour != null || TourStore.isDone(this)) return;
        if (FocusStore.isActive(this)) return;

        ViewGroup main = findViewById(R.id.main);
        tour = new TourView(this, TourView.defaultSteps(), () -> {
            tour = null;
            TourStore.markDone(this);
        });
        main.addView(tour, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tour.start();
    }

    // "Are you sure you wanna enter Focus mode?"  Yes / No
    private void askFocus() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) return;

        if (FocusStore.isActive(this)) {
            startActivity(new Intent(this, FocusActivity.class));
            return;
        }

        Dialog d = new Dialog(this, R.style.NdsDialog);
        d.setContentView(R.layout.dialog_focus_confirm);
        d.findViewById(R.id.focusNo).setOnClickListener(v -> d.dismiss());
        d.findViewById(R.id.focusYes).setOnClickListener(v -> {
            d.dismiss();
            openFocusSetup();
        });

        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            w.setDimAmount(0.3f);
        }
        BlurHelper.blurBehind(findViewById(R.id.main), d);
        d.show();
    }

    private void showIntro() {
        ViewGroup main = findViewById(R.id.main);
        IntroView intro = new IntroView(this);
        main.addView(intro, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        intro.start(this::playEntrance);
    }

    // hide the real UI while the intro plays
    private void prepareEntrance() {
        float d = getResources().getDisplayMetrics().density;
        ViewGroup main = findViewById(R.id.main);
        View glow = main.getChildAt(0);
        View container = findViewById(R.id.fragmentContainer);

        glow.setAlpha(0f);
        container.setAlpha(0f);
        container.setTranslationY(28 * d);
        navBar.setAlpha(0f);
        navBar.setTranslationY(48 * d);
    }

    // glow fades in, then content and nav bar rise, then (first run only) the tour starts
    private void playEntrance() {
        ViewGroup main = findViewById(R.id.main);
        View glow = main.getChildAt(0);
        View container = findViewById(R.id.fragmentContainer);

        glow.animate().alpha(1f).setDuration(900).start();
        container.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(100)
                .setDuration(650)
                .setInterpolator(new DecelerateInterpolator(2f))
                .start();
        navBar.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(220)
                .setDuration(650)
                .setInterpolator(new DecelerateInterpolator(2f))
                .start();

        navBar.postDelayed(this::maybeStartTour, 1400);
    }

    private Fragment fragmentFor(int tab) {
        if (tab == 1) return new LimitsFragment();
        if (tab == 2) return new TasksFragment();
        if (tab == 3) return new StudyFragment();   // the Time Table tab
        return new HomeFragment();
    }

    // used by older code that jumps to a tab
    public void openTab(int tab) {
        select(tab);
    }

    // opens the separate task page (id 0 = new task)
    public void openTaskEditor(long taskId) {
        pageTx().replace(R.id.fragmentContainer, TaskEditFragment.newInstance(taskId))
                .addToBackStack("editor")
                .commit();
    }

    // opens the Focus setup page (timer + emergency apps)
    public void openFocusSetup() {
        pageTx().replace(R.id.fragmentContainer, new FocusSetupFragment())
                .addToBackStack("editor")
                .commit();
    }

    // opens the list of apps used today (from the Today's balance card)
    public void openUsedToday() {
        pageTx().replace(R.id.fragmentContainer, new UsedTodayFragment())
                .addToBackStack("editor")
                .commit();
    }

    // opens the study timetable
    public void openStudy() {
        pageTx().replace(R.id.fragmentContainer, new StudyFragment())
                .addToBackStack("editor")
                .commit();
    }

    // opens one study session to add or edit (id 0 = new session)
    public void openStudyEditor(long id) {
        pageTx().replace(R.id.fragmentContainer, StudyEditFragment.newInstance(id))
                .addToBackStack("editor")
                .commit();
    }

    // opens the Apps page (tag apps Productive / Distracting / Other) from "Add app"
    public void openApps() {
        pageTx().replace(R.id.fragmentContainer, new AppsFragment())
                .addToBackStack("editor")
                .commit();
    }

    // opens Settings (from the card in the top-right corner)
    public void openSettings() {
        if (tour != null) return;
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) return;
        pageTx().replace(R.id.fragmentContainer, new SettingsFragment())
                .addToBackStack("editor")
                .commit();
    }

    // closes the open page (task, Focus setup, used-today, study, apps, Settings)
    public void closeEditor() {
        getSupportFragmentManager().popBackStack();
    }

    private void select(int tab) {
        FragmentManager fm = getSupportFragmentManager();

        // leaving an open page: close it first
        boolean editorOpen = fm.getBackStackEntryCount() > 0;
        if (editorOpen) fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        if (tab == current) return;
        boolean toRight = tab > current;
        current = tab;

        FragmentTransaction t = fm.beginTransaction();
        if (Perf.pageAnim(this)) {
            t.setCustomAnimations(
                    toRight ? R.anim.slide_in_right : R.anim.slide_in_left,
                    toRight ? R.anim.slide_out_left : R.anim.slide_out_right);
        }
        t.replace(R.id.fragmentContainer, fragmentFor(tab)).commit();

        // the orange pill: smooth in Quality, quick in Balanced, instant in Performance
        if (Perf.pageAnim(this)) {
            navPill.animate()
                    .translationX(SLOT[tab] * pillW)
                    .setDuration(Perf.mode(this) == Perf.QUALITY ? 320 : 220)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } else {
            navPill.setTranslationX(SLOT[tab] * pillW);
        }
        updateNavColors();
    }

    private void updateNavColors() {
        for (int i = 0; i < TAB_COUNT; i++) {
            tabs[i].setTextColor(getColor(i == current ? R.color.on_orange : R.color.text_dim));
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab", current);
    }
}