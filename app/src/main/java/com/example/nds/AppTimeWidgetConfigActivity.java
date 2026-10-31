package com.example.nds;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

// shown when the "App screen time" widget is added: pick the app
public class AppTimeWidgetConfigActivity extends AppCompatActivity {

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(Activity.RESULT_CANCELED);   // closing without a choice cancels the widget

        Intent in = getIntent();
        if (in.getExtras() != null) {
            widgetId = in.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        setContentView(R.layout.activity_widget_config);
        RecyclerView list = findViewById(R.id.cfgList);
        list.setLayoutManager(new LinearLayoutManager(this));

        Context app = getApplicationContext();
        new Thread(() -> {
            List<AppList.Item> apps = AppList.load(app);
            runOnUiThread(() -> {
                if (!isFinishing()) list.setAdapter(new Adapter(apps));
            });
        }).start();
    }

    private void choose(String pkg) {
        AppTimeWidget.save(this, widgetId, pkg);
        WidgetUpdater.update(this, AppTimeWidget.class);

        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        setResult(Activity.RESULT_OK, result);
        finish();
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private final List<AppList.Item> apps;

        Adapter(List<AppList.Item> apps) {
            this.apps = apps;
        }

        class VH extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView name;

            VH(View v) {
                super(v);
                icon = v.findViewById(R.id.setupIcon);
                name = v.findViewById(R.id.setupName);
                v.findViewById(R.id.setupHint).setVisibility(View.GONE);
                v.findViewById(R.id.setupCheck).setVisibility(View.GONE);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_setup_app, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            AppList.Item it = apps.get(position);
            h.name.setText(it.label);
            try {
                h.icon.setImageDrawable(getPackageManager().getApplicationIcon(it.pkg));
            } catch (PackageManager.NameNotFoundException e) {
                h.icon.setImageDrawable(null);
            }
            h.itemView.setOnClickListener(x -> choose(it.pkg));
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }
    }
}
