package com.example.nds;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * First run: the person chooses which apps get a daily limit (are tagged Distracting).
 * Known doom-scrolling apps are ticked already. Continue (or Back) applies the choice.
 */
public class SetupAppsFragment extends Fragment {

    private static class Row {
        final AppList.Item item;
        boolean checked;

        Row(AppList.Item item, boolean checked) {
            this.item = item;
            this.checked = checked;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private RecyclerView list;
    private TextView cont;
    private boolean finished = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_setup_apps, container, false);
        list = v.findViewById(R.id.setupList);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        cont = v.findViewById(R.id.setupContinue);
        cont.setOnClickListener(b -> applyAndFinish());

        // start from an empty choice if the person wants to pick everything by themselves
        v.findViewById(R.id.setupClear).setOnClickListener(b -> {
            for (Row r : rows) r.checked = false;
            if (list.getAdapter() != null) list.getAdapter().notifyDataSetChanged();
            updateButton();
        });

        // Back works like Continue: the choice is kept and the tour starts
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        applyAndFinish();
                    }
                });

        Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            List<AppList.Item> all = AppList.load(app);
            Set<String> distracting = CategoryStore.distractingPackages(app);
            String dialer = SystemApps.dialer(app);
            String contacts = SystemApps.contacts(app);

            List<Row> ticked = new ArrayList<>();
            List<Row> others = new ArrayList<>();
            for (AppList.Item it : all) {
                if (it.pkg.equals(dialer) || it.pkg.equals(contacts)) continue;
                boolean on = CategoryStore.isSuggested(it.pkg) || distracting.contains(it.pkg);
                (on ? ticked : others).add(new Row(it, on));
            }

            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                rows.addAll(ticked);
                rows.addAll(others);
                list.setAdapter(new Adapter());
                updateButton();
            });
        }).start();

        return v;
    }

    private void updateButton() {
        int n = 0;
        for (Row r : rows) {
            if (r.checked) n++;
        }
        cont.setText("Continue  ·  " + n + " selected");
    }

    private void applyAndFinish() {
        if (finished || !isAdded()) return;
        finished = true;

        Context ctx = requireContext();
        for (Row r : rows) {
            int cur = CategoryStore.get(ctx, r.item.pkg);
            if (r.checked && cur != CategoryStore.DISTRACTING) {
                CategoryStore.setNow(ctx, r.item.pkg, CategoryStore.DISTRACTING);
            } else if (!r.checked && cur == CategoryStore.DISTRACTING) {
                CategoryStore.setNow(ctx, r.item.pkg, CategoryStore.OTHER);
            }
        }
        ((MainActivity) requireActivity()).onAppSetupDone();
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView name, hint;
            final CheckBox check;

            VH(View v) {
                super(v);
                icon = v.findViewById(R.id.setupIcon);
                name = v.findViewById(R.id.setupName);
                hint = v.findViewById(R.id.setupHint);
                check = v.findViewById(R.id.setupCheck);
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
            Row r = rows.get(position);
            Context ctx = h.itemView.getContext();

            h.name.setText(r.item.label);
            try {
                h.icon.setImageDrawable(ctx.getPackageManager().getApplicationIcon(r.item.pkg));
            } catch (PackageManager.NameNotFoundException e) {
                h.icon.setImageDrawable(null);
            }
            h.check.setChecked(r.checked);
            h.hint.setText(r.checked
                    ? "Daily limit: " + UsageRepository.formatMinutes(
                    UsageRepository.defaultLimit(r.item.pkg))
                    : "No limit");

            h.itemView.setOnClickListener(x -> {
                r.checked = !r.checked;
                notifyItemChanged(h.getAdapterPosition());
                updateButton();
            });
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }
}