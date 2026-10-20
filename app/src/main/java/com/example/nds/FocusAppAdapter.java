package com.example.nds;

import android.content.Context;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FocusAppAdapter extends RecyclerView.Adapter<FocusAppAdapter.VH> {

    public static class App {
        public final String pkg;
        public final String label;

        public App(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }
    }

    public interface Listener {
        void onClick(App a);
    }

    private final List<App> items;
    private final Listener listener;

    public FocusAppAdapter(List<App> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView label;

        VH(View v) {
            super(v);
            icon = v.findViewById(R.id.focusIcon);
            label = v.findViewById(R.id.focusLabel);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_focus_app, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        App a = items.get(position);
        Context ctx = h.itemView.getContext();

        h.label.setText(a.label);
        try {
            h.icon.setImageDrawable(ctx.getPackageManager().getApplicationIcon(a.pkg));
        } catch (PackageManager.NameNotFoundException e) {
            h.icon.setImageDrawable(null);
        }
        h.itemView.setOnClickListener(v -> listener.onClick(a));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}