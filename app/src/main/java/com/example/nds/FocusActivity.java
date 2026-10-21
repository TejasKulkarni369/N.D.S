package com.example.nds;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FocusActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private GlowRingView ring;
    private TextView timeView, swTime, swToggle, swReset;
    private boolean done = false;

    // the Focus countdown, once per second
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long left = FocusStore.remainingMs(FocusActivity.this);
            if (left <= 0) {
                finishFocus();
                return;
            }
            timeView.setText(format(left));
            ring.setProgress(left / (float) FocusStore.totalMs(FocusActivity.this));
            handler.postDelayed(this, 1000 - (System.currentTimeMillis() % 1000) + 5);
        }
    };

    // the stopwatch, ten times per second
    private final Runnable swTick = new Runnable() {
        @Override
        public void run() {
            long ms = StopwatchStore.elapsedMs(FocusActivity.this);
            boolean running = StopwatchStore.isRunning(FocusActivity.this);

            swTime.setText(formatWatch(ms));
            swToggle.setText(running ? "Pause" : (ms > 0 ? "Resume" : "Start"));
            swReset.setAlpha(ms > 0 ? 1f : 0.4f);

            handler.postDelayed(this, 100);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_focus);

        if (!FocusStore.isActive(this)) {
            finish();
            return;
        }

        View root = findViewById(R.id.focusRoot);
        View content = findViewById(R.id.focusContent);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            content.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        ring = findViewById(R.id.focusRing);
        timeView = findViewById(R.id.focusTime);
        swTime = findViewById(R.id.swTime);
        swToggle = findViewById(R.id.swToggle);
        swReset = findViewById(R.id.swReset);

        // stopwatch buttons
        swToggle.setOnClickListener(v -> {
            if (StopwatchStore.isRunning(this)) StopwatchStore.pause(this);
            else StopwatchStore.start(this);
        });
        swReset.setOnClickListener(v -> StopwatchStore.reset(this));

        RecyclerView grid = findViewById(R.id.focusGrid);
        grid.setLayoutManager(new GridLayoutManager(this, 3));
        grid.setAdapter(new FocusAppAdapter(buildApps(), this::launch));

        // the back button does nothing while Focus is on
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // blocked on purpose
            }
        });

        // a new Focus session: stopwatch starts from zero, then the intro plays
        if (getIntent().getBooleanExtra("intro", false) && savedInstanceState == null) {
            StopwatchStore.reset(this);

            content.setAlpha(0f);
            IntroView intro = new IntroView(this, "FOCUS", "Everything else can wait", true);
            ((ViewGroup) root).addView(intro, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            intro.start(() -> content.animate().alpha(1f).setDuration(600).start());
        }
    }

    // Phone, Contacts, then the emergency apps (1 to 5)
    private List<FocusAppAdapter.App> buildApps() {
        List<FocusAppAdapter.App> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        String dialer = SystemApps.dialer(this);
        if (dialer != null && seen.add(dialer)) out.add(new FocusAppAdapter.App(dialer, "Phone"));

        String contacts = SystemApps.contacts(this);
        if (contacts != null && seen.add(contacts)) {
            out.add(new FocusAppAdapter.App(contacts, "Contacts"));
        }

        for (String pkg : FocusStore.apps(this)) {
            if (seen.add(pkg)) {
                out.add(new FocusAppAdapter.App(pkg, UsageRepository.labelFor(this, pkg)));
            }
        }
        return out;
    }

    private void launch(FocusAppAdapter.App a) {
        Intent i = getPackageManager().getLaunchIntentForPackage(a.pkg);
        if (i != null) startActivity(i);
    }

    private static String format(long ms) {
        long sec = ms / 1000;
        return String.format(Locale.US, "%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60);
    }

    // HH:MM:SS.t (t = tenths of a second)
    private static String formatWatch(long ms) {
        long tenths = (ms / 100) % 10;
        long sec = (ms / 1000) % 60;
        long min = (ms / 60000) % 60;
        long hours = ms / 3600000;
        return String.format(Locale.US, "%02d:%02d:%02d.%d", hours, min, sec, tenths);
    }

    private void finishFocus() {
        if (done) return;
        done = true;
        Toast.makeText(this, "Focus complete", Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ring != null) {
            ring.startPulse();
            handler.post(tick);
            handler.post(swTick);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        handler.removeCallbacks(swTick);
        if (ring != null) ring.stopPulse();
    }
}