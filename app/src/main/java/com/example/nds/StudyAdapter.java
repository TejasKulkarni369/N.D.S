package com.example.nds;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class StudyAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // one row of the page: a day title or a session
    public static class Item {
        final boolean header;
        final String title;
        final boolean today;
        final StudySlot slot;

        private Item(boolean header, String title, boolean today, StudySlot slot) {
            this.header = header;
            this.title = title;
            this.today = today;
            this.slot = slot;
        }

        public static Item header(String title, boolean today) {
            return new Item(true, title, today, null);
        }

        public static Item slot(StudySlot slot) {
            return new Item(false, null, false, slot);
        }
    }

    public interface Listener {
        void onOpen(StudySlot s);
    }

    private final List<Item> items;
    private final Listener listener;

    public StudyAdapter(List<Item> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        TextView title;

        HeaderVH(View v) {
            super(v);
            title = v.findViewById(R.id.studyHeader);
        }
    }

    static class SlotVH extends RecyclerView.ViewHolder {
        TextView subject, when, notify;

        SlotVH(View v) {
            super(v);
            subject = v.findViewById(R.id.studySubject);
            when = v.findViewById(R.id.studyWhen);
            notify = v.findViewById(R.id.studyNotify);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).header ? 0 : 1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == 0) {
            return new HeaderVH(inf.inflate(R.layout.item_study_header, parent, false));
        }
        return new SlotVH(inf.inflate(R.layout.item_study_slot, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Item it = items.get(position);

        if (holder instanceof HeaderVH) {
            TextView t = ((HeaderVH) holder).title;
            t.setText(it.title);
            t.setTextColor(ContextCompat.getColor(t.getContext(),
                    it.today ? R.color.orange : R.color.text));
            return;
        }

        SlotVH h = (SlotVH) holder;
        StudySlot s = it.slot;
        h.subject.setText(s.subject);
        h.when.setText(StudyScheduler.timeText(s.startMin) + " – "
                + StudyScheduler.timeText(s.endMin));
        h.notify.setText(s.notify ? "Notifications on" : "Muted");
        h.notify.setTextColor(ContextCompat.getColor(h.itemView.getContext(),
                s.notify ? R.color.productive : R.color.text_dim));
        h.itemView.setOnClickListener(v -> listener.onOpen(s));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}