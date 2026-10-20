package com.example.nds;

import android.content.Context;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Set;

public class FocusPickAdapter extends RecyclerView.Adapter<FocusPickAdapter.VH> {

    private final List<AppList.Item> items;
    private final Set<String> picked;
    private final Runnable onChange;

    public FocusPickAdapter(List<AppList.Item> items, Set<String> picked, Runnable onChange) {
        this.items = items;
        this.picked = picked;
        this.onChange = onChange;
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView name;
        CheckBox box;

        VH(View v) {
            super(v);
            icon = v.findViewById(R.id.pickIcon);
            name = v.findViewById(R.id.pickName);
            box = v.findViewById(R.id.pickBox);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_focus_pick, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AppList.Item a = items.get(position);
        Context ctx = h.itemView.getContext();

        h.name.setText(a.label);
        try {
            h.icon.setImageDrawable(ctx.getPackageManager().getApplicationIcon(a.pkg));
        } catch (PackageManager.NameNotFoundException e) {
            h.icon.setImageDrawable(null);
        }
        h.box.setChecked(picked.contains(a.pkg));

        h.itemView.setOnClickListener(v -> {
            if (picked.contains(a.pkg)) {
                picked.remove(a.pkg);
            } else if (picked.size() >= FocusStore.APP_COUNT) {
                Toast.makeText(ctx, "Only " + FocusStore.APP_COUNT + " emergency apps",
                        Toast.LENGTH_SHORT).show();
                return;
            } else {
                picked.add(a.pkg);
            }
            int pos = h.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
            onChange.run();
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}