package com.example.nds;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class TasksFragment extends Fragment {

    private RecyclerView list;
    private View empty;
    private TaskAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_tasks, container, false);
        list = v.findViewById(R.id.taskList);
        empty = v.findViewById(R.id.taskEmpty);

        adapter = new TaskAdapter(new TaskAdapter.Listener() {
            @Override
            public void onOpen(Task t) {
                ((MainActivity) requireActivity()).openTaskEditor(t.id);
            }

            @Override
            public void onToggle(Task t) {
                Context ctx = requireContext();
                TaskStore.setDone(ctx, t.id, !t.done);
                if (t.done) {
// undo "done": set the reminder again
                    TaskScheduler.schedule(ctx, t);
                } else {
                    TaskScheduler.cancel(ctx, t);
                }
                refresh();
            }

            @Override
            public void onDelete(Task t) {
                Context ctx = requireContext();
                TaskScheduler.cancel(ctx, t);
                TaskStore.delete(ctx, t.id);
                refresh();
            }

            @Override
            public void onCheck(Task t, int index, boolean checked) {
                Context ctx = requireContext();

                List<Task.Check> checks = new ArrayList<>();
                boolean allDone = true;
                for (int i = 0; i < t.checks.size(); i++) {
                    Task.Check c = t.checks.get(i);
                    boolean d = (i == index) ? checked : c.done;
                    checks.add(new Task.Check(c.text, d));
                    if (!d) allDone = false;
                }

// all checkpoints ticked = the task is done
                Task updated = new Task(t.id, t.title, t.description, t.timeMs, allDone, checks);
                TaskStore.upsert(ctx, updated);
                TaskScheduler.cancel(ctx, updated);
                if (!allDone) TaskScheduler.schedule(ctx, updated);

                list.post(() -> {
                    if (isAdded()) refresh();
                });
            }
        });

        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

// "Add task" opens the separate task page
        v.findViewById(R.id.addTaskBtn).setOnClickListener(
                b -> ((MainActivity) requireActivity()).openTaskEditor(0));
        return v;
    }


    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        List<Task> tasks = TaskStore.load(requireContext());
        adapter.setItems(tasks);
        empty.setVisibility(tasks.isEmpty() ? View.VISIBLE : View.GONE);
    }
}