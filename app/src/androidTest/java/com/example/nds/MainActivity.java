package com.example.nds;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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

public class MainActivity extends AppCompatActivity {

    private static final int TAB_COUNT = 3;   // 0 Home, 1 Limits, 2 Apps

    private int current = 0;
    private int pillW = 0;
    private View navBar, navPill;
    private final TextView[] tabs = new TextView[TAB_COUNT];
    private GestureDetector swipe;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // system splash is just the dark background; our own intro takes over
        splash.setOnExitAnimationListener(provider -> provider.remove());

        View content = findViewById(R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            content.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        navBar = findViewById(R.id.navBar);
        navPill = findViewById(R.id.navPill);
        tabs[0] = findViewById(R.id.navHome);
        tabs[1] = findViewById(R.id.navLimits);
        tabs[2] = findViewById(R.id.navApps);

        if (savedInstanceState != null) {
            current = savedInstanceState.getInt("tab", 0);
        } else {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new HomeFragment())
                    .commit();
            prepareEntrance();
            showIntro();
        }
        updateNavColors();

        // size the sliding pill to one third of the nav bar
        navBar.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            int inner = (r - l) - navBar.getPaddingLeft() - navBar.getPaddingRight();
            int w = inner / TAB_COUNT;
            if (w > 0 && w != pillW) {
                pillW = w;
                ViewGroup.LayoutParams lp = navPill.getLayoutParams();
                lp.width = w;
                navPill.setLayoutParams(lp);
                navPill.setTranslationX(current * w);
            }
        });

        for (int i = 0; i < TAB_COUNT; i++) {
            final int idx = i;
            tabs[i].setOnClickListener(v -> select(idx));
        }

        // swipe anywhere: left = next page, right = previous page
        swipe = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float vx, float vy) {
                if (e1 == null) return false;
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
                if (current != 0) select(0);
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
        if (PermissionUtils.hasUsageAccess(this) && PermissionUtils.hasOverlayAccess(this)) {
            ContextCompat.startForegroundService(this, new Intent(this, BlockService.class));
        }
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

    // glow fades in, then content and nav bar rise
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
    }

    private Fragment fragmentFor(int tab) {
        if (tab == 1) return new LimitsFragment();
        if (tab == 2) return new AppsFragment();
        return new HomeFragment();
    }

    private void select(int tab) {
        if (tab == current) return;
        boolean toRight = tab > current;
        current = tab;

        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(
                        toRight ? R.anim.slide_in_right : R.anim.slide_in_left,
                        toRight ? R.anim.slide_out_left : R.anim.slide_out_right)
                .replace(R.id.fragmentContainer, fragmentFor(tab))
                .commit();

        navPill.animate()
                .translationX(tab * pillW)
                .setDuration(260)
                .setInterpolator(new DecelerateInterpolator())
                .start();
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