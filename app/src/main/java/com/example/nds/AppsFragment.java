package com.example.nds;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import java.util.List;
import java.util.Set;

public class AppsFragment extends Fragment {

    private RecyclerView list;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_apps, container, false);
        list = v.findViewById(R.id.catList);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        RecyclerView.ItemAnimator an = list.getItemAnimator();
        if (an instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) an).setSupportsChangeAnimations(false);
        }
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (list.getAdapter() != null) return;

        Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            List<AppList.Item> apps = AppList.load(app);
            Set<String> locked = UsageRepository.lockedPackages(app);
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (isAdded()) list.setAdapter(new CategoryAdapter(apps, locked));
            });
        }).start();
    }
}