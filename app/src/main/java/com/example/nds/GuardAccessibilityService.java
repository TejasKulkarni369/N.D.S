package com.example.nds;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

public class GuardAccessibilityService extends AccessibilityService {

    // protection turns on when any limit is this percent used
    private static final int PROTECT_PERCENT = 90;

    // screens that could undo the protection (matched on the screen's class name)
    private static final String[] SENSITIVE = {
            "InstalledAppDetails", "AppInfo", "AppDetails", "ApplicationsDetails",
            "DeviceAdmin", "Uninstall", "Accessibility", "UsageAccess",
            "DrawOverlay", "Overlay", "NotificationAccess", "NotificationListener"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean watching = false;   // the current screen looks sensitive
    private final Runnable check = this::checkScreen;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        int type = event.getEventType();

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String pkg = event.getPackageName().toString();
            String cls = event.getClassName() == null ? "" : event.getClassName().toString();
            watching = pkg.contains("packageinstaller") || isSensitive(cls);
            if (!watching) return;
        } else if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (!watching) return;
        } else {
            return;
        }

        // wait until the screen finished loading, then look at it once
        handler.removeCallbacks(check);
        handler.postDelayed(check, 200);
    }

    private boolean isSensitive(String cls) {
        for (String s : SENSITIVE) {
            if (cls.contains(s)) return true;
        }
        return false;
    }

    private void checkScreen() {
        if (!watching) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        // the app name is "N.D.S" in every language
        boolean mentionsUs = !root.findAccessibilityNodeInfosByText("N.D.S").isEmpty();
        if (!mentionsUs) return;

        if (!isProtectedNow()) {
            watching = false;
            return;
        }

        watching = false;
        performGlobalAction(GLOBAL_ACTION_HOME);
        Toast.makeText(this, "N.D.S is protected until tomorrow", Toast.LENGTH_SHORT).show();
    }
    private boolean isProtectedNow() {
        if (FocusStore.isActive(this)) return true;
        if (BypassStore.usedToday(this)) return true;

        UsageRepository.Snapshot s = UsageRepository.snapshot(this);
        for (AppUsage u : UsageRepository.usageList(this, s)) {
            long limitMs = u.limitMin * 60000L;
            if (u.usedMs * 100 >= limitMs * PROTECT_PERCENT) return true;
        }
        return false;
    }

    @Override
    public void onInterrupt() {
    }
}