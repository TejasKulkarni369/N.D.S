package com.example.nds;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppUsageAdapter extends RecyclerView.Adapter<AppUsageAdapter.VH> {

    public interface Listener {
        void onClick(AppUsage u);
    }

    private List<AppUsage> items = new ArrayList<>();
    private final Listener listener;
    private final Map<String, Drawable> lockedIcons = new HashMap<>();

    public AppUsageAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<AppUsage> list) {
        items = list;
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView logo, lock;
        TextView name, time;
        GlowBarView bar;

        VH(View v) {
            super(v);
            logo = v.findViewById(R.id.appLogo);
            lock = v.findViewById(R.id.appLock);
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

    // grey logo with the red lock badge (kept so it is not rebuilt every second)
    private Drawable lockedIcon(Context ctx, String pkg) {
        Drawable d = lockedIcons.get(pkg);
        if (d == null) {
            try {
                d = LockedIcon.of(ctx, ctx.getPackageManager().getApplicationIcon(pkg));
                lockedIcons.put(pkg, d);
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
        }
        return d;
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AppUsage u = items.get(position);
        Context ctx = h.itemView.getContext();
        boolean locked = u.usedMin >= u.limitMin;

        h.name.setText(u.name);

        int red = ContextCompat.getColor(ctx, R.color.locked);
        int orange = ContextCompat.getColor(ctx, R.color.orange);

        // only swap the card background when the state changes (avoids flicker)
        Object lastState = h.itemView.getTag();
        if (lastState == null || (Boolean) lastState != locked) {
            h.itemView.setBackgroundResource(
                    locked ? R.drawable.glass_card_locked : R.drawable.glass_card);
            h.itemView.setTag(locked);
        }

        if (locked) {
            h.logo.setImageDrawable(lockedIcon(ctx, u.pkg));
            h.logo.setVisibility(View.VISIBLE);
            h.lock.setVisibility(View.VISIBLE);
            h.time.setText("Locked");
            h.time.setTextColor(red);
            h.bar.setBarColor(red);
            h.bar.setProgress(1f);
        } else {
            h.logo.setVisibility(View.GONE);
            h.lock.setVisibility(View.GONE);
            h.time.setText(UsageRepository.formatMinutes(u.usedMin) + " / "
                    + UsageRepository.formatMinutes(u.limitMin));
            h.time.setTextColor(ContextCompat.getColor(ctx, R.color.orange_soft));
            h.bar.setBarColor(orange);
            h.bar.setProgress(u.usedMs / (u.limitMin * 60000f));
        }

        h.itemView.setOnClickListener(v -> listener.onClick(u));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}