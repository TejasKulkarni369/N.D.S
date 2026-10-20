package com.example.nds;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;

public class AppUsageAdapter extends RecyclerView.Adapter<AppUsageAdapter.VH> {

    private final List<AppUsage> items;

    public AppUsageAdapter(List<AppUsage> items) {
        this.items = items;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, time;
        LinearProgressIndicator bar;

        VH(View v) {
            super(v);
            name = v.findViewById(R.id.appName);
            time = v.findViewById(R.id.appTime);
            bar = v.findViewById(R.id.appBar);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_usage, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AppUsage u = items.get(position);
        h.name.setText(u.name);
        h.time.setText(u.usedMin + "m / " + u.limitMin + "m");
        h.bar.setProgress(u.usedMin * 100 / u.limitMin);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}