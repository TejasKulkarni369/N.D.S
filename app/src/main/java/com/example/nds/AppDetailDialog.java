package com.example.nds;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppDetailDialog {

    public static void show(Context ctx, AppUsage u, int opens) {
        Dialog d = new Dialog(ctx, R.style.NdsDialog);
        d.setContentView(R.layout.dialog_app_detail);

        boolean hasLimit = u.limitMin > 0;
        boolean locked = hasLimit && u.usedMin >= u.limitMin;

        ImageView iconView = d.findViewById(R.id.detailIcon);
        Drawable icon;
        try {
            icon = ctx.getPackageManager().getApplicationIcon(u.pkg);
        } catch (PackageManager.NameNotFoundException e) {
            icon = null;
        }
        if (icon != null && locked) icon = LockedIcon.of(ctx, icon);
        iconView.setImageDrawable(icon);

        ((TextView) d.findViewById(R.id.detailName)).setText(u.name);

        TextView limitTv = d.findViewById(R.id.detailLimit);
        if (!hasLimit) {
            limitTv.setText("No limit · " + catName(CategoryStore.get(ctx, u.pkg)));
        } else if (locked) {
            limitTv.setText("Locked until tomorrow");
            limitTv.setTextColor(ContextCompat.getColor(ctx, R.color.locked));
        } else {
            limitTv.setText("Limit " + UsageRepository.formatMinutes(u.limitMin));
        }

        ((TextView) d.findViewById(R.id.detailOpens)).setText(String.valueOf(opens));
        ((TextView) d.findViewById(R.id.detailTime))
                .setText(UsageRepository.formatMinutes(u.usedMin));

        LinearProgressIndicator bar = d.findViewById(R.id.detailBar);
        TextView usedTv = d.findViewById(R.id.detailUsed);
        if (hasLimit) {
            bar.setProgress((int) Math.min(100, u.usedMs * 100 / (u.limitMin * 60000L)));
            if (locked) bar.setIndicatorColor(ContextCompat.getColor(ctx, R.color.locked));
            usedTv.setText(UsageRepository.formatMinutes(u.usedMin) + " of "
                    + UsageRepository.formatMinutes(u.limitMin) + " used");
        } else {
            bar.setVisibility(View.GONE);
            usedTv.setVisibility(View.GONE);
        }

        TextView notifs = d.findViewById(R.id.detailNotifs);
        View note = d.findViewById(R.id.detailNote);
        View enable = d.findViewById(R.id.detailEnable);

        if (NotificationStore.hasAccess(ctx)) {
            notifs.setText(String.valueOf(NotificationStore.today(ctx, u.pkg)));
            note.setVisibility(View.GONE);
            enable.setVisibility(View.GONE);
        } else {
            notifs.setText("–");
            note.setVisibility(View.VISIBLE);
            enable.setVisibility(View.VISIBLE);
            enable.setOnClickListener(v -> {
                d.dismiss();
                NotificationStore.openSettings(ctx);
            });
        }

        // History: last 7 days (read off the main thread, then filled in)
        LinearLayout box = d.findViewById(R.id.detailHistory);
        TextView loading = new TextView(ctx);
        loading.setText("Loading…");
        loading.setTextSize(12);
        loading.setTextColor(ContextCompat.getColor(ctx, R.color.text_dim));
        loading.setPadding(0, (int) (10 * ctx.getResources().getDisplayMetrics().density), 0, 0);
        box.addView(loading);

        final Context app = ctx.getApplicationContext();
        final String pkg = u.pkg;
        new Thread(() -> {
            List<UsageRepository.DayStat> hist = UsageRepository.history(app, pkg, 7);
            if (!(ctx instanceof Activity)) return;
            ((Activity) ctx).runOnUiThread(() -> {
                if (d.isShowing()) fillHistory(ctx, box, hist);
            });
        }).start();

        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setDimAmount(0.3f);
        }

        // blur the screen behind the card
        if (ctx instanceof Activity) {
            BlurHelper.blurBehind(((Activity) ctx).findViewById(R.id.main), d);
        }
        d.show();
    }

    private static String catName(int cat) {
        if (cat == CategoryStore.PRODUCTIVE) return "Productive";
        if (cat == CategoryStore.DISTRACTING) return "Distracting";
        return "Other";
    }

    // one row per day, newest first: times opened, screen time and a bar
    private static void fillHistory(Context ctx, LinearLayout box,
                                    List<UsageRepository.DayStat> hist) {
        box.removeAllViews();

        long max = 1;
        for (UsageRepository.DayStat day : hist) {
            max = Math.max(max, day.millis);
        }

        LayoutInflater inf = LayoutInflater.from(ctx);
        SimpleDateFormat fmt = new SimpleDateFormat("EEE, d MMM", Locale.getDefault());

        for (int i = hist.size() - 1; i >= 0; i--) {
            UsageRepository.DayStat day = hist.get(i);
            View row = inf.inflate(R.layout.item_history_day, box, false);

            String label;
            if (i == hist.size() - 1) label = "Today";
            else if (i == hist.size() - 2) label = "Yesterday";
            else label = fmt.format(new Date(day.dayStart));

            ((TextView) row.findViewById(R.id.histDay)).setText(label);
            ((TextView) row.findViewById(R.id.histOpened)).setText("Opened " + day.opens + "×");
            ((TextView) row.findViewById(R.id.histTime)).setText(
                    day.millis > 0 && day.millis < 60000 ? "<1m"
                            : UsageRepository.formatMinutes((int) (day.millis / 60000)));
            ((GlowBarView) row.findViewById(R.id.histBar)).setProgress(day.millis / (float) max);

            box.addView(row);
        }
    }
}