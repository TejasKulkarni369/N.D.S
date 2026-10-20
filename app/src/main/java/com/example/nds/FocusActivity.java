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
    private TextView timeView;
    private boolean done = false;

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

        // intro of Focus mode, then the screen with the allowed apps
        if (getIntent().getBooleanExtra("intro", false) && savedInstanceState == null) {
            content.setAlpha(0f);
            IntroView intro = new IntroView(this, "FOCUS", "Everything else can wait", true);
            ((ViewGroup) root).addView(intro, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            intro.start(() -> content.animate().alpha(1f).setDuration(600).start());
        }
    }

    // Phone, Contacts, then the 5 emergency apps
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
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        if (ring != null) ring.stopPulse();
    }
}