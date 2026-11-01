package com.example.nds;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

// Focus mode: shows the time left, or starts it
public class FocusWidget extends NdsWidget {

    @Override
    RemoteViews build(Context c, int id) {
        boolean active = FocusStore.isActive(c);
        String left = "";
        if (active) {
            long min = (FocusStore.remainingMs(c) + 59999) / 60000;
            left = UsageRepository.formatMinutes((int) Math.max(1, min));
        }

        RemoteViews v;
        int size = sizeClass(c, id);
        if (size == MINI) {
            v = views(c, R.layout.widget_mini);
            txt(v, R.id.wMiniValue, active ? left.replace(" ", "") : "Start");
            txt(v, R.id.wMiniLabel, "FOCUS");

        } else if (size == STRIP) {
            v = views(c, R.layout.widget_strip);
            txt(v, R.id.wStripLabel, "FOCUS MODE");
            txt(v, R.id.wStripSub, active ? "left in Focus" : "Block the whole phone");
            txt(v, R.id.wStripValue, active ? left : "Start");

        } else {
            v = views(c, R.layout.widget_focus);
            txt(v, R.id.wState, active ? left : "Focus");
            txt(v, R.id.wSub, active ? "left in Focus" : "Block the whole phone");
            txt(v, R.id.wBtn, active ? "Open" : "Start");
            show(v, R.id.wLabel, sizeDp(c, id)[1] >= 125);   // a short widget drops the title
        }

        if (active) {
            Intent i = new Intent(c, FocusActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            v.setOnClickPendingIntent(R.id.widgetRoot, PendingIntent.getActivity(c, 104, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } else {
            v.setOnClickPendingIntent(R.id.widgetRoot, openApp(c, 105, 0, true));
        }
        return v;
    }
}
