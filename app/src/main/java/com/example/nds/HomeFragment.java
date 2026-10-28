package com.example.nds;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!isAdded()) return;
            loadData();
            handler.postDelayed(this, 1000 - (System.currentTimeMillis() % 1000) + 5);        }
    };

    private View permCard, dataGroup;
    private TextView permTitle, permDesc, grantBtn;
    private RecyclerView list;
    private AppUsageAdapter adapter;
    private GlowRingView ring;
    private TextView totalTime, totalLimit;
    private View barP, barD, barO;
    private TextView prodTime, prodPct, distTime, distPct, othTime, othPct;
    private UsageRepository.Snapshot lastSnap;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_home, container, false);
        permCard = v.findViewById(R.id.permCard);
        permTitle = v.findViewById(R.id.permTitle);
        permDesc = v.findViewById(R.id.permDesc);
        grantBtn = v.findViewById(R.id.grantBtn);
        dataGroup = v.findViewById(R.id.dataGroup);
        list = v.findViewById(R.id.appList);
        ring = v.findViewById(R.id.ring);
        totalTime = v.findViewById(R.id.totalTime);
        totalLimit = v.findViewById(R.id.totalLimit);
        barP = v.findViewById(R.id.barP);
        barD = v.findViewById(R.id.barD);
        barO = v.findViewById(R.id.barO);
        prodTime = v.findViewById(R.id.prodTime);
        prodPct = v.findViewById(R.id.prodPct);
        distTime = v.findViewById(R.id.distTime);
        distPct = v.findViewById(R.id.distPct);
        othTime = v.findViewById(R.id.othTime);
        othPct = v.findViewById(R.id.othPct);

        adapter = new AppUsageAdapter(u -> AppDetailDialog.show(
                requireContext(), u, UsageRepository.opensFor(lastSnap, u.pkg)));
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        // tapping Today's balance lists every app used today
        v.findViewById(R.id.balanceCard).setOnClickListener(
                b -> ((MainActivity) requireActivity()).openUsedToday());
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        Context ctx = requireContext();
        handler.removeCallbacks(tick);

        if (!PermissionUtils.hasUsageAccess(ctx)) {
            ring.stopPulse();
            showPerm("Usage access needed",
                    "N.D.S needs it to see how long you use apps. Data stays on your phone.",
                    "Grant access", true,
                    b -> PermissionUtils.openUsageSettings(ctx));
        } else if (!PermissionUtils.hasOverlayAccess(ctx)) {
            ring.stopPulse();
            showPerm("Display over other apps",
                    "N.D.S needs it to show the lock screen on top of apps that reached their limit.",
                    "Grant access", true,
                    b -> PermissionUtils.openOverlaySettings(ctx));
        } else {
            dataGroup.setVisibility(View.VISIBLE);

            // protection steps: shown above the data, they do not hide it
            if (!GuardUtils.isAdminActive(ctx)) {
                showPerm("Protect N.D.S from uninstall",
                        "Make N.D.S a device admin so Android refuses to uninstall it while limits are on.",
                        "Turn on", false,
                        b -> startActivityForResult(GuardUtils.adminIntent(ctx), 1));
            } else if (!GuardUtils.isAccessibilityOn(ctx)) {
                showPerm("Lock the settings screens",
                        "Turn on N.D.S under Accessibility. It then blocks the uninstall and permission screens once a limit is used up.",
                        "Turn on", false,
                        b -> GuardUtils.openAccessibilitySettings(ctx));
            } else {
                permCard.setVisibility(View.GONE);
            }

            ring.startPulse();
            handler.post(tick);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        ring.stopPulse();
    }

    private void showPerm(String title, String desc, String button,
                          boolean hideData, View.OnClickListener onGrant) {
        permTitle.setText(title);
        permDesc.setText(desc);
        grantBtn.setText(button);
        grantBtn.setOnClickListener(onGrant);
        permCard.setVisibility(View.VISIBLE);
        if (hideData) dataGroup.setVisibility(View.GONE);
    }

    private void loadData() {
        Context ctx = requireContext();
        UsageRepository.Snapshot s = UsageRepository.snapshot(ctx);
        lastSnap = s;

        // time-left ring + Distracting apps list
        List<AppUsage> usage = UsageRepository.usageList(ctx, s);
        long leftMs = 0, limitMs = 0;
        for (AppUsage u : usage) {
            long lim = u.limitMin * 60000L;
            limitMs += lim;
            leftMs += Math.max(0, lim - u.usedMs);
        }

        // big number: total screen time today (all apps)
        long totalAll = 0;
        for (long v : s.millis.values()) {
            totalAll += v;
        }
        totalTime.setText(formatLeft(totalAll));
        totalLimit.setText("Total screen time");

        // the ring: how much of your app limits is still left
        ring.setProgress(limitMs == 0 ? 0f : leftMs / (float) limitMs);
        adapter.setItems(usage);

        // balance widget: [0] others, [1] productive, [2] distracting
        long[] cat = UsageRepository.categoryMillis(ctx, s);
        long total = cat[0] + cat[1] + cat[2];

        setWeight(barP, cat[1]);
        setWeight(barD, cat[2]);
        setWeight(barO, total == 0 ? 1 : cat[0]);

        prodTime.setText(minutes(cat[1]));
        distTime.setText(minutes(cat[2]));
        othTime.setText(minutes(cat[0]));
        prodPct.setText(percent(cat[1], total));
        distPct.setText(percent(cat[2], total));
        othPct.setText(percent(cat[0], total));
    }

    private String formatLeft(long ms) {
        long sec = ms / 1000;
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) return String.format(Locale.US, "%dh %02dm", h, m);
        return String.format(Locale.US, "%dm %02ds", m, s);
    }

    private void setWeight(View v, float w) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        lp.weight = w;
        v.setLayoutParams(lp);
    }

    private String minutes(long ms) {
        return UsageRepository.formatMinutes((int) (ms / 60000));
    }

    private String percent(long part, long total) {
        if (total == 0) return "0%";
        return Math.round(part * 100f / total) + "%";
    }
}