package com.example.nds;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

public class BootReciever extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        TaskScheduler.rescheduleAll(ctx);
        StudyScheduler.rescheduleAll(ctx);

        if (PermissionUtils.hasUsageAccess(ctx) && PermissionUtils.hasOverlayAccess(ctx)) {
            try {
                ContextCompat.startForegroundService(ctx, new Intent(ctx, BlockService.class));
            } catch (Exception ignored) {
            }
        }
    }
}