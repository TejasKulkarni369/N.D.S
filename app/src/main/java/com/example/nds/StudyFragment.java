package com.example.nds;

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
import java.util.Collections;
import java.util.List;

public class StudyFragment extends Fragment {

    private static final String[] DAYS = {
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

    private RecyclerView list;
    private View empty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_study, container, false);
        list = v.findViewById(R.id.studyList);
        empty = v.findViewById(R.id.studyEmpty);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));

        v.findViewById(R.id.studyAdd).setOnClickListener(
                b -> ((MainActivity) requireActivity()).openStudyEditor(0));
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();

        List<StudyAdapter.Item> items = build(StudyStore.load(requireContext()));
        list.setAdapter(new StudyAdapter(items,
                s -> ((MainActivity) requireActivity()).openStudyEditor(s.id)));
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // day titles with their sessions, Monday first; days without sessions are left out
    private List<StudyAdapter.Item> build(List<StudySlot> all) {
        int today = StudyScheduler.todayIndex();
        List<StudyAdapter.Item> out = new ArrayList<>();

        for (int d = 0; d < 7; d++) {
            List<StudySlot> day = new ArrayList<>();
            for (StudySlot s : all) {
                if (s.onDay(d)) day.add(s);
            }
            if (day.isEmpty()) continue;

            Collections.sort(day, (a, b) -> Integer.compare(a.startMin, b.startMin));
            out.add(StudyAdapter.Item.header(
                    DAYS[d] + (d == today ? " · Today" : ""), d == today));
            for (StudySlot s : day) {
                out.add(StudyAdapter.Item.slot(s));
            }
        }
        return out;
    }
}