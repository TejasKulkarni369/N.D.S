package com.example.nds;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.VH> {

    public interface Listener {
        void onOpen(Task t);

        void onToggle(Task t);

        void onDelete(Task t);

        void onCheck(Task t, int index, boolean checked);
    }

    private List<Task> items = new ArrayList<>();
    private final Listener listener;

    public TaskAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Task> list) {
        items = list;
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, desc, time, done, delete;
        LinearLayout checks;

        VH(View v) {
            super(v);
            title = v.findViewById(R.id.taskTitle);
            desc = v.findViewById(R.id.taskDesc);
            checks = v.findViewById(R.id.taskChecks);
            time = v.findViewById(R.id.taskTime);
            done = v.findViewById(R.id.taskDone);
            delete = v.findViewById(R.id.taskDelete);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Task t = items.get(position);
        Context ctx = h.itemView.getContext();

        int green = ContextCompat.getColor(ctx, R.color.productive);
        int orange = ContextCompat.getColor(ctx, R.color.orange);
        int dim = ContextCompat.getColor(ctx, R.color.text_dim);

        h.title.setText(t.title);
        h.title.setTextColor(ContextCompat.getColor(ctx, t.done ? R.color.text_dim : R.color.text));
        strike(h.title, t.done);

        if (t.description.isEmpty()) {
            h.desc.setVisibility(View.GONE);
        } else {
            h.desc.setText(t.description);
            h.desc.setVisibility(View.VISIBLE);
        }

        // checkpoints with markable checkboxes
        h.checks.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(ctx);
        for (int i = 0; i < t.checks.size(); i++) {
            Task.Check c = t.checks.get(i);
            View row = inf.inflate(R.layout.item_task_check, h.checks, false);
            CheckBox box = row.findViewById(R.id.rowCheckBox);
            TextView text = row.findViewById(R.id.rowCheckText);

            text.setText(c.text);
            text.setAlpha(c.done ? 0.5f : 1f);
            strike(text, c.done);

            box.setOnCheckedChangeListener(null);
            box.setChecked(c.done);
            final int index = i;
            box.setOnCheckedChangeListener((b, checked) -> listener.onCheck(t, index, checked));
            text.setOnClickListener(x -> box.toggle());

            h.checks.addView(row);
        }
        h.checks.setVisibility(t.checks.isEmpty() ? View.GONE : View.VISIBLE);

        String when = new SimpleDateFormat("EEE, d MMM · h:mm a", Locale.getDefault())
                .format(new Date(t.timeMs));
        String progress = t.checks.isEmpty()
                ? "" : " · " + t.checksDone() + "/" + t.checks.size();

        if (t.done) {
            h.time.setText("Done · " + when + progress);
            h.time.setTextColor(green);
        } else if (t.timeMs < System.currentTimeMillis()) {
            h.time.setText("Time passed · " + when + progress);
            h.time.setTextColor(orange);
        } else {
            h.time.setText(when + progress);
            h.time.setTextColor(dim);
        }

        circle(h.done, t.done ? green : 0x1FFFFFFF);
        h.done.setTextColor(t.done ? 0xFF1A0E05 : 0xFFC9C9CF);
        circle(h.delete, 0x1FFFFFFF);
        h.delete.setTextColor(0xFFC9C9CF);

        h.itemView.setOnClickListener(v -> listener.onOpen(t));
        h.done.setOnClickListener(v -> listener.onToggle(t));
        h.delete.setOnClickListener(v -> listener.onDelete(t));
    }

    private static void strike(TextView t, boolean on) {
        if (on) {
            t.setPaintFlags(t.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            t.setPaintFlags(t.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        }
    }

    private static void circle(View v, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        v.setBackground(g);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}