package com.example.nds;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FocusSetupFragment extends Fragment {

    private static final int MIN_APPS = 1;   // pick at least 1 ...

    private final List<AppList.Item> apps = new ArrayList<>();
    private final Set<String> picked = new LinkedHashSet<>();

    private EditText hh, mm, ss;
    private TextView counter;
    private RecyclerView list;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_focus_setup, container, false);

        hh = v.findViewById(R.id.focusH);
        mm = v.findViewById(R.id.focusM);
        ss = v.findViewById(R.id.focusS);
        counter = v.findViewById(R.id.focusCount);
        list = v.findViewById(R.id.focusPickList);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));

        setTime(0, 25, 0);
        updateCounter();

        v.findViewById(R.id.preset25).setOnClickListener(b -> setTime(0, 25, 0));
        v.findViewById(R.id.preset60).setOnClickListener(b -> setTime(1, 0, 0));
        v.findViewById(R.id.preset120).setOnClickListener(b -> setTime(2, 0, 0));
        v.findViewById(R.id.focusBack).setOnClickListener(
                b -> ((MainActivity) requireActivity()).closeEditor());
        v.findViewById(R.id.focusStart).setOnClickListener(b -> start());

        // load the installed apps (without Phone and Contacts, they are always allowed)
        Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            List<AppList.Item> all = AppList.load(app);
            String dialer = SystemApps.dialer(app);
            String contacts = SystemApps.contacts(app);

            List<AppList.Item> filtered = new ArrayList<>();
            for (AppList.Item it : all) {
                if (!it.pkg.equals(dialer) && !it.pkg.equals(contacts)) filtered.add(it);
            }

            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                apps.addAll(filtered);
                list.setAdapter(new FocusPickAdapter(apps, picked, this::updateCounter));
            });
        }).start();

        return v;
    }

    private void setTime(int h, int m, int s) {
        hh.setText(String.format(Locale.US, "%02d", h));
        mm.setText(String.format(Locale.US, "%02d", m));
        ss.setText(String.format(Locale.US, "%02d", s));
    }

    private void updateCounter() {
        counter.setText(picked.size() + "/" + FocusStore.APP_COUNT);
    }

    private int num(EditText et) {
        String t = et.getText().toString().trim();
        if (t.isEmpty()) return 0;
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void start() {
        Context ctx = requireContext();

        long total = num(hh) * 3600L + num(mm) * 60L + num(ss);
        if (total <= 0) {
            Toast.makeText(ctx, "Set a timer first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (total > FocusStore.MAX_HOURS * 3600L) {
            Toast.makeText(ctx, "Maximum is " + FocusStore.MAX_HOURS + " hours",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        // 1 to 5 emergency apps
        if (picked.size() < MIN_APPS) {
            Toast.makeText(ctx, "Pick at least 1 emergency app (up to "
                    + FocusStore.APP_COUNT + ")", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!PermissionUtils.hasUsageAccess(ctx) || !PermissionUtils.hasOverlayAccess(ctx)) {
            Toast.makeText(ctx, "Turn on Usage access and Display over other apps on Home first",
                    Toast.LENGTH_LONG).show();
            return;
        }

        FocusStore.start(ctx, total * 1000L, new ArrayList<>(picked));
        ContextCompat.startForegroundService(ctx, new Intent(ctx, BlockService.class));

        Intent i = new Intent(ctx, FocusActivity.class);
        i.putExtra("intro", true);   // plays the Focus intro first
        startActivity(i);

        ((MainActivity) requireActivity()).closeEditor();
    }
}