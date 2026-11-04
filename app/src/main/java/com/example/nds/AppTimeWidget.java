package com.example.nds;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.LruCache;
import android.widget.RemoteViews;

// screen time of ONE app the person picked when adding the widget
public class AppTimeWidget extends NdsWidget {

    // app icons are drawn once, not on every refresh
    private static final LruCache<String, Bitmap> ICONS = new LruCache<>(8);

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
        super.onDeleted(c, ids);
        SharedPreferences.Editor e = sp(c).edit();
        for (int id : ids) e.remove("pkg_" + id);
        e.apply();
    }

    @Override
    RemoteViews build(Context c, int id) {
        String pkg = pkgFor(c, id);
        int size = sizeClass(c, id);
        RemoteViews v;

        // not chosen yet (or the app is gone): tap to choose
        if (pkg == null || !installed(c, pkg)) {
            if (size == MINI) {
                v = views(c, R.layout.widget_mini);
                txt(v, R.id.wMiniValue, "+");
                txt(v, R.id.wMiniLabel, "Pick app");
            } else if (size == STRIP) {
                v = views(c, R.layout.widget_strip);
                txt(v, R.id.wStripLabel, "APP SCREEN TIME");
                txt(v, R.id.wStripSub, "Tap to pick the app");
                txt(v, R.id.wStripValue, "+");
            } else {
                v = views(c, R.layout.widget_app_time);
                txt(v, R.id.wName, "Choose an app");
                txt(v, R.id.wTime, "–");
                txt(v, R.id.wSub, "Tap to pick the app");
                show(v, R.id.wBar, false);
                v.setImageViewResource(R.id.wIcon, R.drawable.ic_focus);
            }
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
        String name = UsageRepository.labelFor(c, pkg);
        AppUsage u = UsageRepository.usageFor(c, s, pkg);   // only Distracting apps have a limit
        String info = (u != null ? "Limit " + UsageRepository.formatMinutes(u.limitMin) + "  ·  " : "")
                + "opened " + opens + "x";

        if (size == MINI) {
            v = views(c, R.layout.widget_mini);
            txt(v, R.id.wMiniValue, durTight(used));
            txt(v, R.id.wMiniLabel, name);
        } else if (size == STRIP) {
            v = views(c, R.layout.widget_strip);
            txt(v, R.id.wStripLabel, name.toUpperCase());
            txt(v, R.id.wStripSub, info);
            txt(v, R.id.wStripValue, dur(used));
        } else {
            v = views(c, R.layout.widget_app_time);
            txt(v, R.id.wName, name);
            txt(v, R.id.wTime, dur(used));
            note("icon:" + pkg);
            v.setImageViewBitmap(R.id.wIcon, iconOf(c, pkg));
            show(v, R.id.wBar, u != null);
            if (u != null) {
                int pct = (int) Math.min(100, used * 100 / Math.max(1L, u.limitMin * 60000L));
                prog(v, R.id.wBar, pct);
            }
            txt(v, R.id.wSub, info);
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
        Bitmap cached = ICONS.get(pkg);
        if (cached != null) return cached;

        int size = (int) (48 * c.getResources().getDisplayMetrics().density);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        try {
            Drawable d = c.getPackageManager().getApplicationIcon(pkg);
            d.setBounds(0, 0, size, size);
            d.draw(new Canvas(bmp));
        } catch (Exception ignored) {
        }
        ICONS.put(pkg, bmp);
        return bmp;
    }
}
