package com.example.nds;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UsedTodayFragment extends Fragment {

    private RecyclerView list;
    private TextView summary, empty;
    private volatile UsageRepository.Snapshot snap;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_used_today, container, false);
        list = v.findViewById(R.id.usedList);
        summary = v.findViewById(R.id.usedSummary);
        empty = v.findViewById(R.id.usedEmpty);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));

        v.findViewById(R.id.usedBack).setOnClickListener(
                b -> ((MainActivity) requireActivity()).closeEditor());

        // read today's usage off the main thread
        Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            UsageRepository.Snapshot s = UsageRepository.snapshot(app);
            snap = s;

            Set<String> keys = new HashSet<>(s.millis.keySet());
            keys.addAll(s.opens.keySet());

            List<UsedAppAdapter.Row> rows = new ArrayList<>();
            long total = 0;
            for (String pkg : keys) {
                Long ms = s.millis.get(pkg);
                long time = (ms == null) ? 0 : ms;
                int opens = UsageRepository.opensFor(s, pkg);
                if (time <= 0 && opens <= 0) continue;
                rows.add(new UsedAppAdapter.Row(pkg, UsageRepository.labelFor(app, pkg),
                        opens, time, CategoryStore.get(app, pkg)));
                total += time;
            }
            Collections.sort(rows, (a, b) -> Long.compare(b.ms, a.ms));
            final long totalMs = total;

            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                if (rows.isEmpty()) {
                    summary.setText("Nothing used yet today.");
                    empty.setVisibility(View.VISIBLE);
                    return;
                }
                summary.setText(rows.size() + " apps used today · "
                        + UsageRepository.formatMinutes((int) (totalMs / 60000)) + " in total\n"
                        + "Tap an app to see its history.");
                list.setAdapter(new UsedAppAdapter(rows, this::openApp));
            });
        }).start();

        return v;
    }

    // tapping an app shows its details card with the History
    private void openApp(UsedAppAdapter.Row r) {
        Context ctx = requireContext();
        AppUsage u = (snap == null) ? null : UsageRepository.usageFor(ctx, snap, r.pkg);
        if (u == null) {
            // not a Distracting app: it has no limit
            u = new AppUsage(r.pkg, r.label, r.ms, 0);
        }
        AppDetailDialog.show(ctx, u, r.opens);
    }
}