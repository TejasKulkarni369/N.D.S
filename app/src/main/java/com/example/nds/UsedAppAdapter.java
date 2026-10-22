package com.example.nds;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class UsedAppAdapter extends RecyclerView.Adapter<UsedAppAdapter.VH> {

    public static class Row {
        public final String pkg;
        public final String label;
        public final int opens;
        public final long ms;
        public final int cat;

        public Row(String pkg, String label, int opens, long ms, int cat) {
            this.pkg = pkg;
            this.label = label;
            this.opens = opens;
            this.ms = ms;
            this.cat = cat;
        }
    }

    public interface Listener {
        void onClick(Row r);
    }

    private final List<Row> items;
    private final Listener listener;

    public UsedAppAdapter(List<Row> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView name, opened, time;
        View dot;

        VH(View v) {
            super(v);
            icon = v.findViewById(R.id.usedIcon);
            name = v.findViewById(R.id.usedName);
            opened = v.findViewById(R.id.usedOpened);
            time = v.findViewById(R.id.usedTime);
            dot = v.findViewById(R.id.usedDot);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_used_app, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Row r = items.get(position);
        Context ctx = h.itemView.getContext();

        h.name.setText(r.label);
        h.opened.setText("Opened " + r.opens + "×");
        h.time.setText(r.ms < 60000 ? "<1m"
                : UsageRepository.formatMinutes((int) (r.ms / 60000)));

        try {
            h.icon.setImageDrawable(ctx.getPackageManager().getApplicationIcon(r.pkg));
        } catch (PackageManager.NameNotFoundException e) {
            h.icon.setImageDrawable(null);
        }

        // dot: green = Productive, orange = Distracting, grey = Others
        int color = ContextCompat.getColor(ctx,
                r.cat == CategoryStore.PRODUCTIVE ? R.color.productive
                        : r.cat == CategoryStore.DISTRACTING ? R.color.orange
                        : R.color.others);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color);
        h.dot.setBackground(dot);

        h.itemView.setOnClickListener(v -> listener.onClick(r));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}