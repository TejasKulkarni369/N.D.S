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
            "Smoothest. Full glass cards, intro animation, moving background glow, glowing ring, and smooth page and card animations.",
            "Simple cards and only the page swipe animations. No intro and no moving glow.",
            "Simple cards, no animations and no blur. The fastest on any phone, and it saves battery."
    };

    private final CheckBox[] boxes = new CheckBox[3];
    private LinearLayout permList;
    private TextView optSummary, permSummary;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_settings, container, false);
        Context ctx = requireContext();
        LayoutInflater inf = LayoutInflater.from(ctx);

        // ---- Optimizations: the three modes stay hidden until the card is tapped ----
        LinearLayout list = v.findViewById(R.id.optList);
        optSummary = v.findViewById(R.id.optSummary);
        View chevron = v.findViewById(R.id.optChevron);
        for (int i = 0; i < 3; i++) {
            View row = inf.inflate(R.layout.item_optimization, list, false);
            ((TextView) row.findViewById(R.id.optTitle)).setText(TITLES[i]
                    + (i == Perf.recommended(ctx) ? "  ·  Best for this phone" : ""));
            ((TextView) row.findViewById(R.id.optDesc)).setText(DESCS[i]);
            boxes[i] = row.findViewById(R.id.optBox);
            // the three rows share one view id: Android must not "restore" one tick into all three
            boxes[i].setSaveEnabled(false);

            final int mode = i;
            row.setSaveFromParentEnabled(false);
            row.setOnClickListener(x -> select(mode));
            list.addView(row);
        }
        refreshChecks();

        v.findViewById(R.id.optHeader).setOnClickListener(
                x -> Expander.toggle(list, chevron));

        // ---- Permissions: hidden until the card is tapped ----
        permSummary = v.findViewById(R.id.permSummary);
        View permBody = v.findViewById(R.id.permBody);
        View permChevron = v.findViewById(R.id.permChevron);
        v.findViewById(R.id.permHeader).setOnClickListener(
                x -> Expander.toggle(permBody, permChevron));

        permList = v.findViewById(R.id.permList);
        for (int id : Perms.visible()) {
            View row = inf.inflate(R.layout.item_permission, permList, false);
            row.setTag(id);
            row.setSaveFromParentEnabled(false);
            ((TextView) row.findViewById(R.id.permTitle)).setText(Perms.TITLES[id]);
            ((TextView) row.findViewById(R.id.permDesc)).setText(Perms.DESCS[id]);
            row.setOnClickListener(x -> Perms.toggle(requireActivity(), id));
            permList.addView(row);
        }
        refreshPerms();

        v.findViewById(R.id.settingsBack).setOnClickListener(
                b -> ((MainActivity) requireActivity()).closeEditor());

        // run the whole tour again
        v.findViewById(R.id.settingsTour).setOnClickListener(
                b -> ((MainActivity) requireActivity()).replayTour());
        return v;
    }

    // coming back from an Android settings screen: show the new status
    @Override
    public void onResume() {
        super.onResume();
        refreshPerms();
    }

    private void refreshPerms() {
        if (permList == null || getContext() == null) return;
        Context ctx = requireContext();
        int on = 0;
        for (int i = 0; i < permList.getChildCount(); i++) {
            View row = permList.getChildAt(i);
            int id = (Integer) row.getTag();
            TextView st = row.findViewById(R.id.permStatus);
            String s = Perms.status(ctx, id);
            st.setText(s);
            st.setTextColor("Off".equals(s) ? 0xFFFF4D2E : 0xFF5BE3A0);
            if (!"Off".equals(s)) on++;
        }
        if (permSummary != null) {
            permSummary.setText(on + " of " + permList.getChildCount() + " on  ·  tap to manage");
        }
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
        if (optSummary != null) {
            optSummary.setText("Now: " + TITLES[current] + "  ·  tap to change");
        }
    }
}
