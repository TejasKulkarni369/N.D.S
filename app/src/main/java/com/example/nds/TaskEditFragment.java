package com.example.nds;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.graphics.Paint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class TaskEditFragment extends Fragment {

    private static final String ARG_ID = "id";

    public static TaskEditFragment newInstance(long id) {
        TaskEditFragment f = new TaskEditFragment();
        Bundle b = new Bundle();
        b.putLong(ARG_ID, id);
        f.setArguments(b);
        return f;
    }

    private static class Item {
        String text;
        boolean done;

        Item(String text, boolean done) {
            this.text = text;
            this.done = done;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private final Calendar cal = Calendar.getInstance();
    private Task existing;
    private long originalTime;
    private boolean skipDraft = false;   // true after Save / Delete: nothing to keep

    private EditText title, desc, newCheck;
    private TextView dateBtn, timeBtn, count;
    private LinearLayout checkContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_task_edit, container, false);

        TextView heading = v.findViewById(R.id.editHeading);
        title = v.findViewById(R.id.editTitle);
        desc = v.findViewById(R.id.editDesc);
        newCheck = v.findViewById(R.id.editNewCheck);
        dateBtn = v.findViewById(R.id.editDate);
        timeBtn = v.findViewById(R.id.editTime);
        count = v.findViewById(R.id.editCount);
        checkContainer = v.findViewById(R.id.checkContainer);
        View delete = v.findViewById(R.id.editDelete);

        long id = getArguments() == null ? 0 : getArguments().getLong(ARG_ID, 0);
        existing = (id == 0) ? null : TaskStore.get(requireContext(), id);

        if (existing != null) {
            heading.setText("Edit task");
            title.setText(existing.title);
            desc.setText(existing.description);
            cal.setTimeInMillis(existing.timeMs);
            for (Task.Check c : existing.checks) {
                items.add(new Item(c.text, c.done));
            }
            delete.setVisibility(View.VISIBLE);
        } else {
            heading.setText("New task");
            // default: the next full hour
            cal.add(Calendar.HOUR_OF_DAY, 1);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
        }
        originalTime = cal.getTimeInMillis();
        restoreDraft(savedInstanceState == null);   // continue where the person stopped

        updateLabels();
        renderChecks();

        v.findViewById(R.id.editBack).setOnClickListener(b -> close());

        dateBtn.setOnClickListener(b -> new DatePickerDialog(requireContext(),
                (view, y, m, day) -> {
                    cal.set(y, m, day);
                    updateLabels();
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)).show());

        timeBtn.setOnClickListener(b -> new TimePickerDialog(requireContext(),
                (view, hour, minute) -> {
                    cal.set(Calendar.HOUR_OF_DAY, hour);
                    cal.set(Calendar.MINUTE, minute);
                    updateLabels();
                },
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show());

        v.findViewById(R.id.editAddCheck).setOnClickListener(b -> addCheck());
        newCheck.setOnEditorActionListener((tv, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addCheck();
                return true;
            }
            return false;
        });

        v.findViewById(R.id.editSave).setOnClickListener(b -> save());
        delete.setOnClickListener(b -> {
            Context ctx = requireContext();
            if (existing != null) {
                TaskScheduler.cancel(ctx, existing);
                TaskStore.delete(ctx, existing.id);
            }
            skipDraft = true;
            DraftStore.clear(ctx, draftKey());
            close();
        });

        return v;
    }

    // ---------------- auto draft ----------------

    // one draft for "New task", and one per task that is being edited
    private String draftKey() {
        return DraftStore.taskKey(existing == null ? 0 : existing.id);
    }

    // Back, Home, a call, the app being closed: all of them end here, so the draft is always kept
    @Override
    public void onPause() {
        super.onPause();
        if (!skipDraft) saveDraft();
    }

    private boolean isDirty(String t, String ds, String pending) {
        if (!pending.trim().isEmpty()) return true;
        if (existing == null) {
            return !t.trim().isEmpty() || !ds.trim().isEmpty() || !items.isEmpty();
        }
        if (!t.trim().equals(existing.title)
                || !ds.trim().equals(existing.description)
                || cal.getTimeInMillis() != originalTime
                || items.size() != existing.checks.size()) {
            return true;
        }
        for (int i = 0; i < items.size(); i++) {
            Task.Check c = existing.checks.get(i);
            if (!items.get(i).text.equals(c.text) || items.get(i).done != c.done) return true;
        }
        return false;
    }

    private void saveDraft() {
        Context ctx = getContext();
        if (ctx == null || title == null) return;

        String t = title.getText().toString();
        String ds = desc.getText().toString();
        String pending = newCheck.getText().toString();

        // nothing typed (or nothing changed): no draft
        if (!isDirty(t, ds, pending)) {
            DraftStore.clear(ctx, draftKey());
            return;
        }
        try {
            JSONObject o = new JSONObject();
            o.put("title", t);
            o.put("desc", ds);
            o.put("time", cal.getTimeInMillis());
            o.put("pending", pending);
            JSONArray arr = new JSONArray();
            for (Item it : items) {
                JSONObject c = new JSONObject();
                c.put("t", it.text);
                c.put("d", it.done);
                arr.put(c);
            }
            o.put("checks", arr);
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
            title.setText(o.optString("title"));
            desc.setText(o.optString("desc"));
            newCheck.setText(o.optString("pending"));

            // a time that already passed is not restored for a new task: keep the default
            long time = o.optLong("time", cal.getTimeInMillis());
            if (existing != null || time > System.currentTimeMillis()) {
                cal.setTimeInMillis(time);
            }

            items.clear();
            JSONArray arr = o.optJSONArray("checks");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject c = arr.getJSONObject(i);
                    items.add(new Item(c.optString("t"), c.optBoolean("d")));
                }
            }
            title.setSelection(title.getText().length());
            if (announce) {
                Toast.makeText(ctx, "Draft restored", Toast.LENGTH_SHORT).show();
            }
        } catch (JSONException e) {
            DraftStore.clear(ctx, draftKey());
        }
    }

    private void updateLabels() {
        dateBtn.setText(DateFormat.format("EEE, d MMM yyyy", cal));
        timeBtn.setText(DateFormat.format("h:mm a", cal));
    }

    private void addCheck() {
        String t = newCheck.getText().toString().trim();
        if (t.isEmpty()) return;
        items.add(new Item(t, false));
        newCheck.setText("");
        renderChecks();
    }

    private void renderChecks() {
        checkContainer.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(requireContext());

        for (Item it : items) {
            View row = inf.inflate(R.layout.item_check, checkContainer, false);
            CheckBox box = row.findViewById(R.id.checkBox);
            TextView text = row.findViewById(R.id.checkText);
            View del = row.findViewById(R.id.checkDelete);

            text.setText(it.text);
            box.setChecked(it.done);
            strike(text, it.done);

            box.setOnCheckedChangeListener((b, checked) -> {
                it.done = checked;
                strike(text, checked);
                updateCount();
            });
            text.setOnClickListener(x -> box.toggle());
            del.setOnClickListener(x -> {
                items.remove(it);
                renderChecks();
            });

            checkContainer.addView(row);
        }
        updateCount();
    }

    private void strike(TextView t, boolean done) {
        t.setAlpha(done ? 0.5f : 1f);
        if (done) {
            t.setPaintFlags(t.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            t.setPaintFlags(t.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        }
    }

    private void updateCount() {
        if (items.isEmpty()) {
            count.setText("");
            return;
        }
        int done = 0;
        for (Item it : items) {
            if (it.done) done++;
        }
        count.setText(done + "/" + items.size());
    }

    private void save() {
        String t = title.getText().toString().trim();
        if (t.isEmpty()) {
            Toast.makeText(requireContext(), "Write a title", Toast.LENGTH_SHORT).show();
            return;
        }

        // a checkpoint that was typed but not added yet
        String pending = newCheck.getText().toString().trim();
        if (!pending.isEmpty()) {
            items.add(new Item(pending, false));
            newCheck.setText("");
        }

        long time = cal.getTimeInMillis();
        boolean timeChanged = existing == null || time != originalTime;
        if (timeChanged && time <= System.currentTimeMillis()) {
            Toast.makeText(requireContext(), "Pick a time in the future",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        List<Task.Check> checks = new ArrayList<>();
        boolean allDone = !items.isEmpty();
        for (Item it : items) {
            checks.add(new Task.Check(it.text, it.done));
            if (!it.done) allDone = false;
        }
        // all checkpoints ticked = the task is done
        boolean done = items.isEmpty() ? (existing != null && existing.done) : allDone;

        long id = (existing != null) ? existing.id : System.currentTimeMillis();
        Task task = new Task(id, t, desc.getText().toString().trim(), time, done, checks);

        Context ctx = requireContext();
        TaskStore.upsert(ctx, task);
        TaskScheduler.cancel(ctx, task);
        TaskScheduler.schedule(ctx, task);   // skipped if done or the time has passed
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