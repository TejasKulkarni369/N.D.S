package com.example.nds;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

/** Settings > Goals: a screen time goal for every weekday, and an opens goal for any app. */
public class GoalsFragment extends Fragment {

    private static final String[] DAYS_LONG = {"Monday", "Tuesday", "Wednesday", "Thursday",
            "Friday", "Saturday", "Sunday"};
    private static final String[] DAYS_SHORT = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private static final String[] LETTERS = {"M", "T", "W", "T", "F", "S", "S"};

    private int day;                                   // the weekday being edited (0 = Monday)
    private final TextView[] chips = new TextView[7];
    private TextView goalValue, goalDayName, goalWeek;
    private LinearLayout appsBox;

    private final List<String> pkgs = new ArrayList<>();   // the apps in the "opens" list
    private UsageRepository.Snapshot snap;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_goals, container, false);
        Context ctx = requireContext();
        float d = ctx.getResources().getDisplayMetrics().density;

        goalValue = v.findViewById(R.id.goalValue);
        goalDayName = v.findViewById(R.id.goalDayName);
        goalWeek = v.findViewById(R.id.goalWeek);
        appsBox = v.findViewById(R.id.goalApps);
        LinearLayout daysBox = v.findViewById(R.id.goalDays);

        day = StudyScheduler.todayIndex();

        // the seven day buttons
        for (int i = 0; i < 7; i++) {
            TextView t = new TextView(ctx);
            t.setText(LETTERS[i]);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(14);
            t.setPadding(0, (int) (11 * d), 0, (int) (11 * d));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i < 6) lp.setMarginEnd((int) (6 * d));
            final int dayIndex = i;
            t.setOnClickListener(x -> {
                day = dayIndex;
                refreshScreen();
            });
            daysBox.addView(t, lp);
            chips[i] = t;
        }

        // - and + : tap 15 minutes, hold 1 hour
        v.findViewById(R.id.goalMinus).setOnClickListener(x -> adjustScreen(-15));
        v.findViewById(R.id.goalMinus).setOnLongClickListener(x -> {
            adjustScreen(-60);
            return true;
        });
        v.findViewById(R.id.goalPlus).setOnClickListener(x -> adjustScreen(15));
        v.findViewById(R.id.goalPlus).setOnLongClickListener(x -> {
            adjustScreen(60);
            return true;
        });
        v.findViewById(R.id.goalAll).setOnClickListener(x -> {
            GoalStore.setScreenAll(requireContext(), GoalStore.screenMin(requireContext(), day));
            refreshScreen();
            android.widget.Toast.makeText(requireContext(),
                    "Same goal for every day", android.widget.Toast.LENGTH_SHORT).show();
        });

        v.findViewById(R.id.goalAdd).setOnClickListener(x -> pickApp());
        v.findViewById(R.id.goalsBack).setOnClickListener(
                x -> requireActivity().getSupportFragmentManager().popBackStack());

        refreshScreen();
        loadApps();
        return v;
    }

    // ---------------- screen time goal ----------------

    private void adjustScreen(int delta) {
        Context ctx = requireContext();
        int cur = GoalStore.screenMin(ctx, day);
        int next = (cur == 0 && delta > 0) ? 120 : cur + delta;   // the first + starts at 2 hours
        GoalStore.setScreenMin(ctx, day, next);
        refreshScreen();
    }

    private void refreshScreen() {
        Context ctx = requireContext();
        float d = ctx.getResources().getDisplayMetrics().density;

        for (int i = 0; i < 7; i++) {
            boolean on = i == day;
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(12 * d);
            if (on) {
                bg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
                bg.setColors(new int[]{0xFFFFA35C, 0xFFFF7A1A});
                bg.setStroke((int) d, 0x66FFFFFF);
            } else {
                bg.setColor(0x14FFFFFF);
                bg.setStroke((int) d, 0x1FFFFFFF);
            }
            chips[i].setBackground(bg);
            chips[i].setTextColor(on ? 0xFF1A0E05 : 0xFFC9C9CF);
        }

        int m = GoalStore.screenMin(ctx, day);
        goalDayName.setText("Goal for " + DAYS_LONG[day]
                + (day == StudyScheduler.todayIndex() ? " (today)" : ""));
        goalValue.setText(m == 0 ? "No goal" : UsageRepository.formatMinutes(m));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            int g = GoalStore.screenMin(ctx, i);
            if (i > 0) sb.append(i == 4 ? "\n" : "   ·   ");
            sb.append(DAYS_SHORT[i]).append(' ')
                    .append(g == 0 ? "–" : UsageRepository.formatMinutes(g).replace(" ", ""));
        }
        goalWeek.setText(sb.toString());
    }

    // ---------------- opens goals ----------------

    private void loadApps() {
        Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            UsageRepository.Snapshot s = UsageRepository.snapshot(app);
            Set<String> all = new HashSet<>(CategoryStore.distractingPackages(app));
            all.addAll(GoalStore.opensApps(app));

            List<String> list = new ArrayList<>();
            for (String pkg : all) {
                if (app.getPackageManager().getLaunchIntentForPackage(pkg) != null) list.add(pkg);
            }
            Collections.sort(list, (a, b) -> UsageRepository.labelFor(app, a)
                    .compareToIgnoreCase(UsageRepository.labelFor(app, b)));

            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                snap = s;
                pkgs.clear();
                pkgs.addAll(list);
                refreshApps();
            });
        }).start();
    }

    private void refreshApps() {
        Context ctx = requireContext();
        LayoutInflater inf = LayoutInflater.from(ctx);
        appsBox.removeAllViews();

        for (String pkg : pkgs) {
            View row = inf.inflate(R.layout.item_goal_app, appsBox, false);
            ImageView icon = row.findViewById(R.id.goalIcon);
            TextView name = row.findViewById(R.id.goalName);
            TextView hint = row.findViewById(R.id.goalHint);
            TextView value = row.findViewById(R.id.goalValue);

            icon.setImageDrawable(AppIcons.get(ctx, pkg));
            name.setText(UsageRepository.labelFor(ctx, pkg));

            int goal = GoalStore.opens(ctx, pkg);
            int opens = snap == null ? 0 : UsageRepository.opensFor(snap, pkg);
            hint.setText("Opened " + opens + (opens == 1 ? " time" : " times") + " today");
            value.setText(goal == 0 ? "Off" : String.valueOf(goal));
            value.setTextColor(goal > 0 && opens >= goal ? 0xFFFF4D2E : 0xFFFF7A1A);

            row.findViewById(R.id.goalMinus).setOnClickListener(x -> adjustOpens(pkg, -1));
            row.findViewById(R.id.goalMinus).setOnLongClickListener(x -> {
                adjustOpens(pkg, -5);
                return true;
            });
            row.findViewById(R.id.goalPlus).setOnClickListener(x -> adjustOpens(pkg, 1));
            row.findViewById(R.id.goalPlus).setOnLongClickListener(x -> {
                adjustOpens(pkg, 5);
                return true;
            });
            appsBox.addView(row);
        }
    }

    private void adjustOpens(String pkg, int delta) {
        Context ctx = requireContext();
        int cur = GoalStore.opens(ctx, pkg);
        int next = (cur == 0 && delta > 0) ? 5 : cur + delta;   // the first + starts at 5 opens
        GoalStore.setOpens(ctx, pkg, next);
        refreshApps();
    }

    // "+ Add another app": a list of every app
    private void pickApp() {
        Context ctx = requireContext();
        Dialog dlg = new Dialog(ctx, R.style.NdsDialog);
        dlg.setContentView(R.layout.dialog_pick_app);
        RecyclerView list = dlg.findViewById(R.id.pickList);
        list.setLayoutManager(new LinearLayoutManager(ctx));

        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            List<AppList.Item> items = new ArrayList<>();
            for (AppList.Item it : AppList.load(app)) {
                if (!pkgs.contains(it.pkg)) items.add(it);
            }
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (!dlg.isShowing()) return;
                list.setAdapter(new PickAdapter(items, dlg));
            });
        }).start();

        Window w = dlg.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            w.setDimAmount(0.4f);
        }
        BlurHelper.blurBehind(requireActivity().findViewById(R.id.main), dlg);
        dlg.show();
    }

    private class PickAdapter extends RecyclerView.Adapter<PickAdapter.VH> {
        private final List<AppList.Item> items;
        private final Dialog dlg;

        PickAdapter(List<AppList.Item> items, Dialog dlg) {
            this.items = items;
            this.dlg = dlg;
        }

        class VH extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView name;

            VH(View v) {
                super(v);
                icon = v.findViewById(R.id.setupIcon);
                name = v.findViewById(R.id.setupName);
                v.findViewById(R.id.setupHint).setVisibility(View.GONE);
                v.findViewById(R.id.setupCheck).setVisibility(View.GONE);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_setup_app, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            AppList.Item it = items.get(position);
            h.name.setText(it.label);
            h.icon.setImageDrawable(AppIcons.get(h.itemView.getContext(), it.pkg));
            h.itemView.setOnClickListener(x -> {
                GoalStore.setOpens(requireContext(), it.pkg, 5);   // starts at 5 opens a day
                if (!pkgs.contains(it.pkg)) pkgs.add(it.pkg);
                dlg.dismiss();
                refreshApps();
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
