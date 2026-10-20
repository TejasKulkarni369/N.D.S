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

import java.util.List;

public class LimitsFragment extends Fragment {

    private RecyclerView list;
    private TextView bypassStatus;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_limits, container, false);
        list = v.findViewById(R.id.limitList);
        bypassStatus = v.findViewById(R.id.bypassStatus);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));

        // "Add app" = pick a Distracting app on the Apps page
        v.findViewById(R.id.addAppBtn).setOnClickListener(
                b -> ((MainActivity) requireActivity()).openTab(3));
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        Context ctx = requireContext();

        if (BypassStore.available(ctx)) {
            bypassStatus.setText("Emergency bypass: 1 available today · "
                    + BypassStore.MINUTES + " min for one app · resets at midnight");
        } else {
            bypassStatus.setText("Emergency bypass used today for "
                    + UsageRepository.labelFor(ctx, BypassStore.pkgToday(ctx))
                    + " · resets at midnight");
        }

        List<AppUsage> usage = UsageRepository.todayUsage(ctx);
        list.setAdapter(new LimitAdapter(usage, (u, delta) -> {
            // the bypass bonus is not part of the saved limit
            int base = u.limitMin - BypassStore.bonus(ctx, u.pkg);
            LimitStore.change(ctx, u.pkg, base, delta);
            refresh();
        }));
    }
}