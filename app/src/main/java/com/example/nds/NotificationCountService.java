package com.example.nds;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotificationCountService extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        if (sbn.getPackageName().equals(getPackageName())) return;
        if (sbn.isOngoing()) return;
        if ((sbn.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        NotificationStore.add(this, sbn.getPackageName());
    }
}