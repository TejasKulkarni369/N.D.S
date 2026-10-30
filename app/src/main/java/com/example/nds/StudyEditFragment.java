package com.example.nds;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class StudyEditFragment extends Fragment {

    private static final String ARG_ID = "id";
    private static final String[] DAY_NAMES = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

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
    private final List<StudySlot.Chapter> chapters = new ArrayList<>();
    private int lockOkKey = -1;       // the time range the person confirmed with "Alright!"
    private boolean skipDraft = false;   // true after Save / Delete: nothing to keep

    private final TextView[] chips = new TextView[7];
    private EditText subject, newChapter;
    private TextView startBtn, endBtn, chapterCount;
    private CheckBox notifyBox, lockBox;
    private LinearLayout chapterBox;

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
        lockBox = v.findViewById(R.id.studyLockBox);
        newChapter = v.findViewById(R.id.studyNewChapter);
        chapterCount = v.findViewById(R.id.studyChapterCount);
        chapterBox = v.findViewById(R.id.studyChapterContainer);
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
            lockBox.setChecked(existing.lockIn);
            for (StudySlot.Chapter c : existing.chapters) {
                chapters.add(new StudySlot.Chapter(c.text, c.done));
            }
            if (existing.lockIn) lockOkKey = lockKey();   // already confirmed when it was saved
            delete.setVisibility(View.VISIBLE);
        } else {
            heading.setText("New session");
            dayMask = 1 << StudyScheduler.todayIndex();   // today is pre-selected
            notifyBox.setChecked(true);
        }

        restoreDraft(savedInstanceState == null);   // continue where the person stopped

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
        renderChapters();

        boolean is24 = DateFormat.is24HourFormat(ctx);
        startBtn.setOnClickListener(b -> new TimePickerDialog(ctx, (view, h, m) -> {
            startMin = h * 60 + m;
            updateTimes();
        }, startMin / 60, startMin % 60, is24).show());
        endBtn.setOnClickListener(b -> new TimePickerDialog(ctx, (view, h, m) -> {
            endMin = h * 60 + m;
            updateTimes();
        }, endMin / 60, endMin % 60, is24).show());

        // chapters
        v.findViewById(R.id.studyAddChapter).setOnClickListener(b -> addChapter());
        newChapter.setOnEditorActionListener((tv, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addChapter();
                return true;
            }
            return false;
        });

        // "Want to lock in": ticking it shows the warning card
        lockBox.setOnClickListener(b -> {
            if (!lockBox.isChecked()) return;
            if (endMin <= startMin) {
                lockBox.setChecked(false);
                Toast.makeText(ctx, "The end time must be after the start time",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            askLock(false);
        });

        v.findViewById(R.id.studyEditBack).setOnClickListener(b -> close());
        v.findViewById(R.id.studySave).setOnClickListener(b -> save());
        delete.setOnClickListener(b -> {
            if (existing != null) {
                StudyScheduler.cancel(ctx, existing.id);
                StudyStore.delete(ctx, existing.id);
            }
            skipDraft = true;
            DraftStore.clear(ctx, draftKey());
            close();
        });

        return v;
    }

    // ---------------- lock-in warning ----------------

    private int lockKey() {
        return startMin * 2000 + endMin;
    }

    private String daysText() {
        if (dayMask == 127) return "Every day";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((dayMask & (1 << i)) == 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(DAY_NAMES[i]);
        }
        return sb.toString();
    }

    // "Your Phone will be locked during this Time!"   Cancel / Alright!
    private void askLock(boolean thenSave) {
        Context ctx = requireContext();
        Dialog d = new Dialog(ctx, R.style.NdsDialog);
        d.setContentView(R.layout.dialog_study_lock);

        ((TextView) d.findViewById(R.id.lockTime)).setText(
                StudyScheduler.timeText(startMin) + " – " + StudyScheduler.timeText(endMin));
        ((TextView) d.findViewById(R.id.lockDays)).setText(daysText());

        final boolean[] answered = {false};
        d.findViewById(R.id.lockCancel).setOnClickListener(x -> {
            answered[0] = true;
            lockBox.setChecked(false);
            d.dismiss();
        });
        d.findViewById(R.id.lockOk).setOnClickListener(x -> {
            answered[0] = true;
            lockOkKey = lockKey();
            d.dismiss();
            if (thenSave) save();
        });
        // tapping outside or Back counts as Cancel
        d.setOnCancelListener(x -> {
            if (!answered[0]) lockBox.setChecked(false);
        });

        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            w.setDimAmount(0.3f);
        }
        BlurHelper.blurBehind(requireActivity().findViewById(R.id.main), d);
        d.show();
    }

    // ---------------- chapters ----------------

    private void addChapter() {
        String t = newChapter.getText().toString().trim();
        if (t.isEmpty()) return;
        chapters.add(new StudySlot.Chapter(t, false));
        newChapter.setText("");
        renderChapters();
    }

    private void renderChapters() {
        chapterBox.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(requireContext());

        for (StudySlot.Chapter ch : chapters) {
            View row = inf.inflate(R.layout.item_check, chapterBox, false);
            CheckBox box = row.findViewById(R.id.checkBox);
            TextView text = row.findViewById(R.id.checkText);
            View del = row.findViewById(R.id.checkDelete);

            text.setText(ch.text);
            box.setChecked(ch.done);
            strike(text, ch.done);

            box.setOnCheckedChangeListener((b, checked) -> {
                ch.done = checked;
                strike(text, checked);
                updateChapterCount();
            });
            text.setOnClickListener(x -> box.toggle());
            del.setOnClickListener(x -> {
                chapters.remove(ch);
                renderChapters();
            });
            chapterBox.addView(row);
        }
        updateChapterCount();
    }

    private void strike(TextView t, boolean done) {
        t.setAlpha(done ? 0.5f : 1f);
        if (done) {
            t.setPaintFlags(t.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            t.setPaintFlags(t.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        }
    }

    private void updateChapterCount() {
        if (chapters.isEmpty()) {
            chapterCount.setText("");
            return;
        }
        int done = 0;
        for (StudySlot.Chapter c : chapters) {
            if (c.done) done++;
        }
        chapterCount.setText(done + "/" + chapters.size());
    }

    // ---------------- auto draft ----------------

    // one draft for "New session", and one per session that is being edited
    private String draftKey() {
        return DraftStore.studyKey(existing == null ? 0 : existing.id);
    }

    // Back, Home, a call, the app being closed: all of them end here, so the draft is always kept
    @Override
    public void onPause() {
        super.onPause();
        if (!skipDraft) saveDraft();
    }

    private boolean sameChapters(List<StudySlot.Chapter> a, List<StudySlot.Chapter> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).text.equals(b.get(i).text) || a.get(i).done != b.get(i).done) {
                return false;
            }
        }
        return true;
    }

    private boolean isDirty(String name, String pending) {
        if (!pending.trim().isEmpty()) return true;
        if (existing == null) {
            return !name.isEmpty()
                    || !chapters.isEmpty()
                    || lockBox.isChecked()
                    || dayMask != (1 << StudyScheduler.todayIndex())
                    || startMin != 18 * 60
                    || endMin != 19 * 60
                    || !notifyBox.isChecked();
        }
        return !name.equals(existing.subject)
                || dayMask != existing.dayMask
                || startMin != existing.startMin
                || endMin != existing.endMin
                || notifyBox.isChecked() != existing.notify
                || lockBox.isChecked() != existing.lockIn
                || !sameChapters(chapters, existing.chapters);
    }

    private void saveDraft() {
        Context ctx = getContext();
        if (ctx == null || subject == null) return;

        String name = subject.getText().toString().trim();
        String pending = newChapter.getText().toString();
        if (!isDirty(name, pending)) {
            DraftStore.clear(ctx, draftKey());
            return;
        }
        try {
            JSONObject o = new JSONObject();
            o.put("subject", subject.getText().toString());
            o.put("days", dayMask);
            o.put("start", startMin);
            o.put("end", endMin);
            o.put("notify", notifyBox.isChecked());
            o.put("lock", lockBox.isChecked());
            o.put("lockOk", lockOkKey);
            o.put("pending", pending);
            JSONArray arr = new JSONArray();
            for (StudySlot.Chapter c : chapters) {
                JSONObject co = new JSONObject();
                co.put("t", c.text);
                co.put("d", c.done);
                arr.put(co);
            }
            o.put("chapters", arr);
            DraftStore.put(ctx, draftKey(), o.toString());
        } catch (JSONException ignored) {
        }
    }

    private void restoreDraft(boolean announce) {
        Context ctx = requireContext();
        String json = DraftStore.get(ctx, draftKey());
        if (json == null) return;
        try {
            JSONObject o = new JSONObject(json);
            subject.setText(o.optString("subject"));
            dayMask = o.optInt("days", dayMask);
            startMin = o.optInt("start", startMin);
            endMin = o.optInt("end", endMin);
            notifyBox.setChecked(o.optBoolean("notify", true));
            lockBox.setChecked(o.optBoolean("lock", false));
            lockOkKey = o.optInt("lockOk", -1);
            newChapter.setText(o.optString("pending"));

            chapters.clear();
            JSONArray arr = o.optJSONArray("chapters");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject co = arr.getJSONObject(i);
                    chapters.add(new StudySlot.Chapter(co.optString("t"), co.optBoolean("d")));
                }
            }
            subject.setSelection(subject.getText().length());
            if (announce) {
                Toast.makeText(ctx, "Draft restored", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            DraftStore.clear(ctx, draftKey());
        }
    }

    // ---------------- days, times, save ----------------

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

        // the time changed after "Alright!": show the warning again with the new time
        if (lockBox.isChecked() && lockOkKey != lockKey()) {
            askLock(true);
            return;
        }

        // a chapter that was typed but not added yet
        String pending = newChapter.getText().toString().trim();
        if (!pending.isEmpty()) {
            chapters.add(new StudySlot.Chapter(pending, false));
            newChapter.setText("");
        }

        long id = (existing != null) ? existing.id : System.currentTimeMillis();
        StudySlot slot = new StudySlot(id, name, dayMask, startMin, endMin,
                notifyBox.isChecked(), lockBox.isChecked(), new ArrayList<>(chapters));

        StudyStore.upsert(ctx, slot);
        StudyScheduler.cancel(ctx, id);       // old alarms out, new ones in
        StudyScheduler.schedule(ctx, slot);

        if (slot.lockIn) {
            // saved while the session is already running: the lock starts from its next day
            StudyLock.skipIfRunning(ctx, slot);
            Toast.makeText(ctx, "Lock-in is on for " + StudyScheduler.timeText(startMin)
                    + " – " + StudyScheduler.timeText(endMin), Toast.LENGTH_SHORT).show();
        }

        skipDraft = true;
        DraftStore.clear(ctx, draftKey());   // saved for real: the draft is not needed
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