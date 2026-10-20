package com.example.nds;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;

public class AppDetailDialog {

    public static void show(Context ctx, AppUsage u, int opens) {
        Dialog d = new Dialog(ctx, R.style.NdsDialog);
        d.setContentView(R.layout.dialog_app_detail);
        boolean locked = u.usedMin >= u.limitMin;

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
        if (locked) {
            limitTv.setText("Locked until tomorrow");
            limitTv.setTextColor(ContextCompat.getColor(ctx, R.color.locked));
        } else {
            limitTv.setText("Limit " + UsageRepository.formatMinutes(u.limitMin));
        }

        ((TextView) d.findViewById(R.id.detailOpens)).setText(String.valueOf(opens));
        ((TextView) d.findViewById(R.id.detailTime))
                .setText(UsageRepository.formatMinutes(u.usedMin));

        LinearProgressIndicator bar = d.findViewById(R.id.detailBar);
        bar.setProgress((int) Math.min(100, u.usedMs * 100 / (u.limitMin * 60000L)));
        if (locked) bar.setIndicatorColor(ContextCompat.getColor(ctx, R.color.locked));

        ((TextView) d.findViewById(R.id.detailUsed)).setText(
                UsageRepository.formatMinutes(u.usedMin) + " of "
                        + UsageRepository.formatMinutes(u.limitMin) + " used");

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
}