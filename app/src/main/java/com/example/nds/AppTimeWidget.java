package com.example.nds;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.app.PendingIntent;
import android.view.View;
import android.widget.RemoteViews;

// screen time of ONE app the person picked when adding the widget
public class AppTimeWidget extends NdsWidget {

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("widget_app", Context.MODE_PRIVATE);
    }

    public static void save(Context c, int widgetId, String pkg) {
        sp(c).edit().putString("pkg_" + widgetId, pkg).apply();
    }

    static String pkgFor(Context c, int widgetId) {
        return sp(c).getString("pkg_" + widgetId, null);
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        SharedPreferences.Editor e = sp(c).edit();
        for (int id : ids) e.remove("pkg_" + id);
        e.apply();
    }

    @Override
    RemoteViews build(Context c, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_app_time);
        String pkg = pkgFor(c, id);

        // not chosen yet (or the app is gone): tap to choose
        if (pkg == null || !installed(c, pkg)) {
            v.setTextViewText(R.id.wName, "Choose an app");
            v.setTextViewText(R.id.wTime, "–");
            v.setTextViewText(R.id.wSub, "Tap to pick the app");
            v.setViewVisibility(R.id.wBar, View.GONE);
            v.setImageViewResource(R.id.wIcon, R.drawable.ic_focus);

            Intent i = new Intent(c, AppTimeWidgetConfigActivity.class);
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            v.setOnClickPendingIntent(R.id.widgetRoot, PendingIntent.getActivity(c, 200 + id, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            return v;
        }

        UsageRepository.Snapshot s = UsageRepository.snapshot(c);
        Long ms = s.millis.get(pkg);
        long used = ms == null ? 0L : ms;
        int opens = UsageRepository.opensFor(s, pkg);

        v.setTextViewText(R.id.wName, UsageRepository.labelFor(c, pkg));
        v.setTextViewText(R.id.wTime, dur(used));
        v.setImageViewBitmap(R.id.wIcon, iconOf(c, pkg));

        AppUsage u = UsageRepository.usageFor(c, s, pkg);   // only Distracting apps have a limit
        if (u != null) {
            int pct = (int) Math.min(100, used * 100 / Math.max(1L, u.limitMin * 60000L));
            v.setViewVisibility(R.id.wBar, View.VISIBLE);
            v.setProgressBar(R.id.wBar, 100, pct, false);
            v.setTextViewText(R.id.wSub, "Limit " + UsageRepository.formatMinutes(u.limitMin)
                    + "  ·  opened " + opens + "x");
        } else {
            v.setViewVisibility(R.id.wBar, View.GONE);
            v.setTextViewText(R.id.wSub, "Opened " + opens + (opens == 1 ? " time" : " times"));
        }

        v.setOnClickPendingIntent(R.id.widgetRoot, openApp(c, 300 + id, 0, false));
        return v;
    }

    private static boolean installed(Context c, String pkg) {
        try {
            c.getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Bitmap iconOf(Context c, String pkg) {
        int size = (int) (48 * c.getResources().getDisplayMetrics().density);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        try {
            Drawable d = c.getPackageManager().getApplicationIcon(pkg);
            d.setBounds(0, 0, size, size);
            d.draw(new Canvas(bmp));
        } catch (Exception ignored) {
        }
        return bmp;
    }
}
