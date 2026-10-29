package com.example.nds;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {

    private static final String[] TITLES = {"Quality", "Balanced", "Performance"};
    private static final String[] DESCS = {
            "Smoothest. Intro animation, moving background glow, glowing ring, and smooth page and card animations.",
            "Only the page swipe animations. No intro and no moving glow.",
            "No animations at all and no blur. The fastest, and it saves battery."
    };

    private final CheckBox[] boxes = new CheckBox[3];

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_settings, container, false);
        Context ctx = requireContext();

        LinearLayout list = v.findViewById(R.id.optList);
        LayoutInflater inf = LayoutInflater.from(ctx);
        for (int i = 0; i < 3; i++) {
            View row = inf.inflate(R.layout.item_optimization, list, false);
            ((TextView) row.findViewById(R.id.optTitle)).setText(TITLES[i]);
            ((TextView) row.findViewById(R.id.optDesc)).setText(DESCS[i]);
            boxes[i] = row.findViewById(R.id.optBox);

            final int mode = i;
            row.setOnClickListener(x -> select(mode));
            list.addView(row);
        }
        refreshChecks();

        v.findViewById(R.id.settingsBack).setOnClickListener(
                b -> ((MainActivity) requireActivity()).closeEditor());
        return v;
    }

    private void select(int mode) {
        Perf.setMode(requireContext(), mode);
        refreshChecks();
        ((MainActivity) requireActivity()).onPerfChanged();
    }

    private void refreshChecks() {
        int current = Perf.mode(requireContext());
        for (int i = 0; i < 3; i++) {
            boxes[i].setChecked(i == current);
        }
    }
}