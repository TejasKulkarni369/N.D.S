package com.example.nds;

import android.app.TimePickerDialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class StudyEditFragment extends Fragment {

    private static final String ARG_ID = "id";

    public static StudyEditFragment newInstance(long id) {
        StudyEditFragment f = new StudyEditFragment();
        Bundle b = new Bundle();
        b.putLong(ARG_ID, id);
        f.setArguments(b);
        return f;
    }

    private StudySlot existing;
    private int dayMask = 0;
    private int startMin = 18 * 60;   // 6:00 PM
    private int endMin = 19 * 60;     // 7:00 PM

    private final TextView[] chips = new TextView[7];
    private EditText subject;
    private TextView startBtn, endBtn;
    private CheckBox notifyBox;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_study_edit, container, false);
        Context ctx = requireContext();
        float d = ctx.getResources().getDisplayMetrics().density;

        TextView heading = v.findViewById(R.id.studyEditHeading);
        subject = v.findViewById(R.id.studySubjectEdit);
        startBtn = v.findViewById(R.id.studyStart);
        endBtn = v.findViewById(R.id.studyEnd);
        notifyBox = v.findViewById(R.id.studyNotifyBox);
        LinearLayout daysBox = v.findViewById(R.id.studyDays);
        View delete = v.findViewById(R.id.studyDelete);

        long id = getArguments() == null ? 0 : getArguments().getLong(ARG_ID, 0);
        existing = (id == 0) ? null : StudyStore.get(ctx, id);

        if (existing != null) {
            heading.setText("Edit session");
            subject.setText(existing.subject);
            dayMask = existing.dayMask;
            startMin = existing.startMin;
            endMin = existing.endMin;
            notifyBox.setChecked(existing.notify);
            delete.setVisibility(View.VISIBLE);
        } else {
            heading.setText("New session");
            dayMask = 1 << StudyScheduler.todayIndex();   // today is pre-selected
            notifyBox.setChecked(true);
        }

        // the seven day buttons: M T W T F S S
        String[] letters = {"M", "T", "W", "T", "F", "S", "S"};
        for (int i = 0; i < 7; i++) {
            TextView t = new TextView(ctx);
            t.setText(letters[i]);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(14);
            t.setPadding(0, (int) (12 * d), 0, (int) (12 * d));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i < 6) lp.setMarginEnd((int) (6 * d));

            final int day = i;
            t.setOnClickListener(x -> {
                dayMask ^= (1 << day);
                styleChip(day);
            });
            daysBox.addView(t, lp);
            chips[i] = t;
            styleChip(i);
        }

        updateTimes();
        boolean is24 = DateFormat.is24HourFormat(ctx);
        startBtn.setOnClickListener(b -> new TimePickerDialog(ctx, (view, h, m) -> {
            startMin = h * 60 + m;
            updateTimes();
        }, startMin / 60, startMin % 60, is24).show());
        endBtn.setOnClickListener(b -> new TimePickerDialog(ctx, (view, h, m) -> {
            endMin = h * 60 + m;
            updateTimes();
        }, endMin / 60, endMin % 60, is24).show());

        v.findViewById(R.id.studyEditBack).setOnClickListener(b -> close());
        v.findViewById(R.id.studySave).setOnClickListener(b -> save());
        delete.setOnClickListener(b -> {
            if (existing != null) {
                StudyScheduler.cancel(ctx, existing.id);
                StudyStore.delete(ctx, existing.id);
            }
            close();
        });

        return v;
    }

    private void styleChip(int day) {
        boolean on = (dayMask & (1 << day)) != 0;
        float d = getResources().getDisplayMetrics().density;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12 * d);
        bg.setColor(on ? 0xFFFF7A1A : 0x14FFFFFF);
        chips[day].setBackground(bg);
        chips[day].setTextColor(on ? 0xFF1A0E05 : 0xFFC9C9CF);
    }

    private void updateTimes() {
        startBtn.setText(StudyScheduler.timeText(startMin));
        endBtn.setText(StudyScheduler.timeText(endMin));
    }

    private void save() {
        Context ctx = requireContext();

        String name = subject.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(ctx, "Write the subject", Toast.LENGTH_SHORT).show();
            return;
        }
        if (dayMask == 0) {
            Toast.makeText(ctx, "Pick at least one day", Toast.LENGTH_SHORT).show();
            return;
        }
        if (endMin <= startMin) {
            Toast.makeText(ctx, "The end time must be after the start time",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        long id = (existing != null) ? existing.id : System.currentTimeMillis();
        StudySlot slot = new StudySlot(id, name, dayMask, startMin, endMin,
                notifyBox.isChecked());

        StudyStore.upsert(ctx, slot);
        StudyScheduler.cancel(ctx, id);       // old alarms out, new ones in
        StudyScheduler.schedule(ctx, slot);
        close();
    }

    private void close() {
        Context ctx = requireContext();
        View focus = requireActivity().getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm =
                    (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
        ((MainActivity) requireActivity()).closeEditor();
    }
}