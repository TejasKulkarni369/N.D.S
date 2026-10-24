package com.example.nds;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class GuardAccessibilityService extends AccessibilityService {

    // protection turns on when any limit is this percent used
    private static final int PROTECT_PERCENT = 90;

    // screens that could undo the protection (matched on the screen's class name)
    private static final String[] SENSITIVE = {
            "InstalledAppDetails", "AppInfo", "AppDetails", "ApplicationsDetails",
            "DeviceAdmin", "Uninstall", "Accessibility", "UsageAccess",
            "DrawOverlay", "Overlay", "NotificationAccess", "NotificationListener"
    };

    // "Force stop" is given up after this long
    private static final long STOP_TIMEOUT_MS = 5000;

    public interface Done {
        void onDone(boolean pressed);   // pressed = Force stop (or an already stopped app) worked
    }

    private static GuardAccessibilityService instance;

    public static boolean isRunning() {
        return instance != null;
    }

    // BlockService calls this right before it opens a locked app's info page
    // (under its own cover screen)
    public static void expectForceStop(String pkg, Done done) {
        GuardAccessibilityService s = instance;
        if (s != null) {
            s.beginForceStop(pkg, done);
        } else if (done != null) {
            done.onDone(false);
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean watching = false;   // the current screen looks sensitive
    private final Runnable check = this::checkScreen;

    // "Force stop" automation
    private String stopPkg = null;
    private Done stopDone = null;
    private int stopStage = 0;          // 1 = looking for "Force stop", 2 = looking for the OK button
    private long stopStarted = 0;
    private final Runnable stopStep = this::forceStopStep;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

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

    // true if any Distracting app used PROTECT_PERCENT or more of its limit today,
    // if today's emergency bypass was used, or while Focus mode is on
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

    // ---------------------------------------------------------------------
    // Force stop: presses "Force stop" and "OK" on the app info page that
    // BlockService opened under its cover screen, then reports back.
    // ---------------------------------------------------------------------

    private void beginForceStop(String pkg, Done done) {
        stopPkg = pkg;
        stopDone = done;
        stopStage = 1;
        stopStarted = System.currentTimeMillis();
        handler.removeCallbacks(stopStep);
        handler.postDelayed(stopStep, 500);
    }

    private void endForceStop(boolean pressed) {
        Done d = stopDone;
        stopDone = null;
        stopPkg = null;
        stopStage = 0;
        handler.removeCallbacks(stopStep);
        performGlobalAction(GLOBAL_ACTION_HOME);
        if (d != null) d.onDone(pressed);
    }

    private void forceStopStep() {
        if (stopPkg == null) return;
        if (System.currentTimeMillis() - stopStarted > STOP_TIMEOUT_MS) {   // give up
            endForceStop(false);
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            if (stopStage == 1) {
                AccessibilityNodeInfo btn = findForceStop(root);
                if (btn != null) {
                    if (btn.isEnabled()) {
                        btn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        stopStage = 2;
                    } else {
                        endForceStop(true);     // greyed out: the app is not running
                        return;
                    }
                }
            } else if (stopStage == 2) {
                if (clickConfirm(root)) {
                    endForceStop(true);
                    return;
                }
            }
        }
        handler.postDelayed(stopStep, 200);
    }

    // the "Force stop" button, found by the text Settings itself uses (every language)
    private AccessibilityNodeInfo findForceStop(AccessibilityNodeInfo root) {
        for (String label : forceStopLabels()) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(label);
            for (AccessibilityNodeInfo n : nodes) {
                AccessibilityNodeInfo c = n;
                while (c != null && !c.isClickable()) c = c.getParent();
                if (c != null) return c;
            }
        }
        return null;
    }

    // the OK button of the "Force stop?" question
    private boolean clickConfirm(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> byId = root.findAccessibilityNodeInfosByViewId("android:id/button1");
        if (!byId.isEmpty()) {
            byId.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
            return true;
        }

        List<String> labels = new ArrayList<>(forceStopLabels());
        labels.add(getString(android.R.string.ok));
        for (String label : labels) {
            for (AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByText(label)) {
                boolean isButton = n.getClassName() != null
                        && n.getClassName().toString().endsWith("Button");
                if (isButton && n.isClickable()) {
                    n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> forceStopLabels() {
        List<String> out = new ArrayList<>();
        try {
            Resources r = getPackageManager().getResourcesForApplication("com.android.settings");
            int id = r.getIdentifier("force_stop", "string", "com.android.settings");
            if (id != 0) out.add(r.getString(id));
        } catch (Exception ignored) {
        }
        if (!out.contains("Force stop")) out.add("Force stop");
        return out;
    }

    @Override
    public void onInterrupt() {
    }
}