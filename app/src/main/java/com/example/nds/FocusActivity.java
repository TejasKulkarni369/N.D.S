package com.example.nds;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
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
    private TextView timeView, swTime, swToggle, swReset, exitBtn;
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

        // the Emergency Exit button, under the allowed apps
        float d = getResources().getDisplayMetrics().density;
        exitBtn = new TextView(this);
        exitBtn.setBackgroundResource(R.drawable.glass_card);
        exitBtn.setGravity(Gravity.CENTER);
        exitBtn.setTextSize(14);
        exitBtn.setTextColor(ContextCompat.getColor(this, R.color.locked));
        int pad = (int) (14 * d);
        exitBtn.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (12 * d);
        ((ViewGroup) grid.getParent()).addView(exitBtn, lp);
        exitBtn.setOnClickListener(v -> askExit());
        refreshExitLabel();

        // the back button does nothing while Focus is on
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // blocked on purpose
            }
        });

        // a new Focus session: the intro plays (the stopwatch keeps what it already counted)
        if (getIntent().getBooleanExtra("intro", false) && savedInstanceState == null) {
            content.setAlpha(0f);
            IntroView intro = new IntroView(this, "FOCUS", "Everything else can wait", true);
            ((ViewGroup) root).addView(intro, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            intro.start(() -> content.animate().alpha(1f).setDuration(600).start());
        }
    }

    private void refreshExitLabel() {
        int left = ExitStore.left(this);
        exitBtn.setText(left > 0
                ? "Emergency Exit · " + left + " left this week"
                : "Emergency Exit · none left this week");
        exitBtn.setAlpha(left > 0 ? 1f : 0.45f);
    }

    // "Are you sure this is an emergency situation?"  Yes, Exit / No
    private void askExit() {
        int left = ExitStore.left(this);
        if (left <= 0) {
            Toast.makeText(this, "No emergency exits left. They come back on Monday.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        Dialog d = new Dialog(this, R.style.NdsDialog);
        d.setContentView(R.layout.dialog_focus_exit);
        ((TextView) d.findViewById(R.id.exitInfo)).setText("You have " + left
                + " emergency exit" + (left == 1 ? "" : "s")
                + " left this week. This uses one.");
        d.findViewById(R.id.exitNo).setOnClickListener(v -> d.dismiss());
        d.findViewById(R.id.exitYes).setOnClickListener(v -> {
            d.dismiss();
            doExit();
        });

        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            w.setDimAmount(0.3f);
        }
        BlurHelper.blurBehind(findViewById(R.id.focusRoot), d);
        d.show();
    }

    // end Focus now and use one of this week's exits
    private void doExit() {
        ExitStore.use(this);
        FocusStore.exitNow(this);
        StudyLock.skipCurrent(this);   // a Time Table lock-in does not start again today
        StopwatchStore.reset(this);

        int left = ExitStore.left(this);
        Toast.makeText(this, "Focus ended · " + left + " emergency exit"
                + (left == 1 ? "" : "s") + " left this week", Toast.LENGTH_LONG).show();
        done = true;
        finish();
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
        StopwatchStore.reset(this);   // the next Focus starts with a fresh stopwatch
        Toast.makeText(this, "Focus complete", Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ring != null) {
            refreshExitLabel();
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