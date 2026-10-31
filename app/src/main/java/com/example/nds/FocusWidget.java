package com.example.nds;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

// Focus mode: shows the time left, or starts it
public class FocusWidget extends NdsWidget {

    @Override
    RemoteViews build(Context c, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_focus);

        if (FocusStore.isActive(c)) {
            long min = (FocusStore.remainingMs(c) + 59999) / 60000;
            v.setTextViewText(R.id.wState, UsageRepository.formatMinutes((int) Math.max(1, min)));
            v.setTextViewText(R.id.wSub, "left in Focus");
            v.setTextViewText(R.id.wBtn, "Open");

            Intent i = new Intent(c, FocusActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            v.setOnClickPendingIntent(R.id.widgetRoot, PendingIntent.getActivity(c, 104, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } else {
            v.setTextViewText(R.id.wState, "Focus");
            v.setTextViewText(R.id.wSub, "Block the whole phone");
            v.setTextViewText(R.id.wBtn, "Start");
            v.setOnClickPendingIntent(R.id.widgetRoot, openApp(c, 105, 0, true));
        }
        return v;
    }
}
