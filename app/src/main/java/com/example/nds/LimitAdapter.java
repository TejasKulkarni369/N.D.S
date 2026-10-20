package com.example.nds;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

public class LimitAdapter extends RecyclerView.Adapter<LimitAdapter.VH> {

    public interface Listener {
        void onChange(AppUsage u, int delta);
    }

    private final List<AppUsage> items;
    private final Listener listener;

    public LimitAdapter(List<AppUsage> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, note, value;
        MaterialButton minus, plus;

        VH(View v) {
            super(v);
            name = v.findViewById(R.id.limitName);
            note = v.findViewById(R.id.limitNote);
            value = v.findViewById(R.id.limitValue);
            minus = v.findViewById(R.id.btnMinus);
            plus = v.findViewById(R.id.btnPlus);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_limit, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AppUsage u = items.get(position);
        Context ctx = h.itemView.getContext();

        boolean locked = u.usedMin >= u.limitMin;
        int pending = LimitStore.getPending(ctx, u.pkg);

        h.name.setText(u.name);
        h.value.setText(UsageRepository.formatMinutes(u.limitMin));

        if (locked) {
            h.note.setText("Locked until tomorrow");
        } else if (pending > 0) {
            h.note.setText("Raising to " + UsageRepository.formatMinutes(pending) + " tomorrow");
        } else {
            h.note.setText("Used " + UsageRepository.formatMinutes(u.usedMin) + " today");
        }

        h.minus.setEnabled(!locked);
        h.plus.setEnabled(!locked);
        h.minus.setAlpha(locked ? 0.4f : 1f);
        h.plus.setAlpha(locked ? 0.4f : 1f);

        h.minus.setOnClickListener(v -> listener.onChange(u, -LimitStore.STEP));
        h.plus.setOnClickListener(v -> listener.onChange(u, LimitStore.STEP));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}