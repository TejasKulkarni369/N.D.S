package com.example.nds;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
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
import java.util.Set;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {

    private final List<AppList.Item> items;
    private final Set<String> locked;   // apps whose limit is used up today

    public CategoryAdapter(List<AppList.Item> items, Set<String> locked) {
        this.items = items;
        this.locked = locked;
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView name, note, prod, dist, other;

        VH(View v) {
            super(v);
            icon = v.findViewById(R.id.catIcon);
            name = v.findViewById(R.id.catName);
            note = v.findViewById(R.id.catNote);
            prod = v.findViewById(R.id.tagProd);
            dist = v.findViewById(R.id.tagDist);
            other = v.findViewById(R.id.tagOther);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_category, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AppList.Item a = items.get(position);
        Context ctx = h.itemView.getContext();

        boolean isLocked = locked.contains(a.pkg);
        int cat = CategoryStore.get(ctx, a.pkg);
        int pending = CategoryStore.getPending(ctx, a.pkg);

        h.name.setText(a.label);

        Drawable icon;
        try {
            icon = ctx.getPackageManager().getApplicationIcon(a.pkg);
        } catch (PackageManager.NameNotFoundException e) {
            icon = null;
        }
        if (icon != null && isLocked) icon = LockedIcon.of(ctx, icon);
        h.icon.setImageDrawable(icon);

        bindTags(h, cat, isLocked);

        if (isLocked) {
            h.note.setText("Locked until tomorrow");
            h.note.setTextColor(ContextCompat.getColor(ctx, R.color.locked));
            h.note.setVisibility(View.VISIBLE);
        } else if (pending >= 0) {
            h.note.setText("Moves to " + nameOf(pending) + " tomorrow");
            h.note.setTextColor(ContextCompat.getColor(ctx, R.color.text_dim));
            h.note.setVisibility(View.VISIBLE);
        } else {
            h.note.setVisibility(View.GONE);
        }

        h.prod.setOnClickListener(v -> pick(h, a, CategoryStore.PRODUCTIVE, isLocked));
        h.dist.setOnClickListener(v -> pick(h, a, CategoryStore.DISTRACTING, isLocked));
        h.other.setOnClickListener(v -> pick(h, a, CategoryStore.OTHER, isLocked));
    }

    private void pick(VH h, AppList.Item a, int cat, boolean isLocked) {
        if (isLocked) return;
        CategoryStore.set(h.itemView.getContext(), a.pkg, cat);
        int pos = h.getAdapterPosition();
        if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
    }

    private static String nameOf(int cat) {
        if (cat == CategoryStore.PRODUCTIVE) return "Productive";
        if (cat == CategoryStore.DISTRACTING) return "Distracting";
        return "Other";
    }

    private void bindTags(VH h, int cat, boolean dim) {
        Context ctx = h.itemView.getContext();
        style(h.prod, cat == CategoryStore.PRODUCTIVE,
                ContextCompat.getColor(ctx, R.color.productive), dim);
        style(h.dist, cat == CategoryStore.DISTRACTING,
                ContextCompat.getColor(ctx, R.color.orange), dim);
        style(h.other, cat == CategoryStore.OTHER,
                ContextCompat.getColor(ctx, R.color.others), dim);
    }

    private static void style(TextView t, boolean on, int color, boolean dim) {
        float d = t.getResources().getDisplayMetrics().density;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12 * d);
        bg.setColor(on ? color : 0x14FFFFFF);
        t.setBackground(bg);
        t.setTextColor(on ? 0xFF1A0E05 : 0xFFC9C9CF);
        t.setAlpha(dim ? 0.45f : 1f);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}