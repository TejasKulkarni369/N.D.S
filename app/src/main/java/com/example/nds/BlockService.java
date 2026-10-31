package com.example.nds;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BlockService extends Service {

    private static final String CHANNEL_ID = "guard";
    private static final int NOTIF_ID = 1;

    private static final long IDLE_MS = 1000;             // nothing locked, no Focus: check once a second
    private static final long ACTIVE_MS = 250;            // something locked or Focus on: watch closer
    private static final long SLOW_MS = 1000;             // totals, icon, warnings, overlay bookkeeping
    private static final long KILL_SWEEP_MS = 15000;      // background-kill of locked apps (was every second)
    private static final long ALLOWED_REFRESH_MS = 30000; // Focus allowed-apps list is cached
    private static final long WARNING_TIMEOUT_MS = 20000; // unanswered warning counts as "No"
    private static final long AFTER_NO_MS = 3000;         // time left to close after "No"
    private static final long FORCE_STOP_GAP_MS = 8000;   // "Force stop" for one app at most every 8 s
    private static final long CLOSE_SAFETY_MS = 7000;     // the cover never stays longer than this
    private static final long BROKEN_PAUSE_MS = 10 * 60000L; // after a failed Force stop, skip it for 10 min

    // main thread: only screens (overlay, toasts, Focus screen). Never usage scans.
    private final Handler ui = new Handler(Looper.getMainLooper());

    // worker thread (low priority): all the checking
    private HandlerThread worker;
    private Handler bg;

    private WindowManager wm;
    private volatile boolean screenOn = true;

    // ---- main-thread state (the Time's up screen) ----
    private View overlay;
    private String overlayPkg;
    private long closeAt = 0;
    private boolean warningOpen = false;
    private String closingPkg = null;
    private final Map<String, Long> forceStopAt = new java.util.HashMap<>();

    // ---- shared state (written on one thread, read on another) ----
    private volatile boolean overlayUp = false;
    private volatile boolean closing = false;
    private volatile long holdUntil = 0;
    private volatile long focusHoldUntil = 0;
    private volatile long forceStopUntil = 0;
    private volatile long forceStopBrokenUntil = 0;
    private volatile Map<String, AppUsage> locked = new ConcurrentHashMap<>();
    private volatile Set<String> focusAllowed = new HashSet<>();   // empty = Focus is off
    private volatile boolean protectedNow = false;                 // uninstall protection is active

    // screens where the Accessibility service has a job (same list as accessibility_config.xml)
    private static final Set<String> WATCHED = new HashSet<>(java.util.Arrays.asList(
            "com.android.settings", "com.google.android.packageinstaller",
            "com.android.packageinstaller", "com.samsung.android.packageinstaller",
            "com.miui.packageinstaller", "com.miui.securitycenter", "com.coloros.safecenter",
            "com.iqoo.secure", "com.vivo.permissionmanager", "com.huawei.systemmanager",
            "com.android.vending"));

    // ---- worker-thread state ----
    private boolean focusWas = false;
    private long lastSlowAt = 0, lastKillAt = 0, allowedAt = 0, lastLockCheckAt = 0, lastWidgetAt = 0;

    private final Runnable autoClose = this::closeBlockedApp;
    private final Runnable warnTimeout = () -> hideWarning(true);
    private final Runnable closeSafety = () -> finishClose(closingPkg);

    // ================= the loop (worker thread) =================

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            if (!screenOn) return;   // screen off: sleep. The screen-on receiver wakes us.
            try {
                long now = System.currentTimeMillis();
                // cheap: reads only the events since the last call
                UsageRepository.Snapshot s = UsageRepository.snapshot(BlockService.this);
                if (now - lastSlowAt >= SLOW_MS) {
                    lastSlowAt = now;
                    slowWork(s, now);
                }
                fastWork(s);
                gateTick(s.foreground);
            } catch (Exception ignored) {
            }
            if (screenOn) {
                boolean busy = !locked.isEmpty() || !focusAllowed.isEmpty();
                bg.postDelayed(this, busy ? ACTIVE_MS : IDLE_MS);
            }
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                screenOn = false;
                bg.removeCallbacks(loop);
            } else if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) {
                screenOn = true;
                bg.removeCallbacks(loop);
                bg.post(loop);
            }
        }
    };

    // once per second
    private void slowWork(UsageRepository.Snapshot s, long now) {
        IconManager.apply(this, s);

        // home-screen widgets: once a minute, only while the screen is on
        if (now - lastWidgetAt >= 60000) {
            lastWidgetAt = now;
            WidgetUpdater.updateAll(this);
        }

        // Time Table lock-in: a session running right now starts Focus (the alarm does it too;
        // this catches a late alarm, a phone that was off, or a killed app)
        if (now - lastLockCheckAt >= 15000) {
            lastLockCheckAt = now;
            StudyLock.startIfDue(this);
        }

        // Focus mode on/off
        boolean focus = FocusStore.isActive(this);
        if (focus) {
            if (focusAllowed.isEmpty() || now - allowedAt > ALLOWED_REFRESH_MS) {
                focusAllowed = FocusStore.allowed(this);   // cached: it asks the system a lot
                allowedAt = now;
            }
        } else {
            if (focusWas) {
                StopwatchStore.reset(this);   // the next Focus starts with a fresh stopwatch
                // after an Emergency Exit the person already got a message
                if (!FocusStore.consumeExitFlag(this)) {
                    ui.post(() -> Toast.makeText(this, "Focus complete", Toast.LENGTH_LONG).show());
                }
            }
            if (!focusAllowed.isEmpty()) focusAllowed = new HashSet<>();
        }
        if (focus != focusWas) WidgetUpdater.update(this, FocusWidget.class);
        focusWas = focus;

        // which apps are locked right now
        List<AppUsage> usage = UsageRepository.usageList(this, s);
        Map<String, AppUsage> newLocked = new ConcurrentHashMap<>();
        for (AppUsage u : usage) {
            if (u.usedMin >= u.limitMin) newLocked.put(u.pkg, u);
        }
        locked = newLocked;

        // same rule the Accessibility service used: any limit 90% used, Focus on, or bypass used
        boolean prot = focus || BypassStore.usedToday(this);
        for (AppUsage u : usage) {
            if (!prot && u.usedMs * 100 >= u.limitMin * 60000L * 90) prot = true;
        }
        protectedNow = prot;

        LimitWarner.check(this, usage);   // 50% and 90% warnings

        // a locked app must not stay alive in the background.
        // Every 15 s is enough; every second made other apps restart and lag.
        if (now - lastKillAt >= KILL_SWEEP_MS) {
            lastKillAt = now;
            for (String pkg : newLocked.keySet()) {
                if (!pkg.equals(s.foreground)) killApp(pkg);
            }
        }

        final String fg = s.foreground;
        final AppUsage hit = (fg == null) ? null : newLocked.get(fg);
        ui.post(() -> overlayTick(fg, hit));
    }

    // watches the app on the screen (a few times a second, only when something is locked / Focus is on)
    private void fastWork(UsageRepository.Snapshot s) {
        final String fg = s.foreground;
        if (fg == null) return;
        long now = System.currentTimeMillis();

        // Focus mode: only allowed apps may be on the screen
        if (focusViolation(fg)) {
            if (now >= focusHoldUntil) ui.post(() -> enforceFocus(fg));
            return;
        }

        // limits: did a locked app just open?
        final AppUsage u = locked.get(fg);
        if (u != null && !overlayUp && !closing && now > holdUntil) {
            ui.post(() -> {
                if (overlay == null && !closing && System.currentTimeMillis() > holdUntil) {
                    showOverlay(u);
                }
            });
        }
    }

    // ================= main thread: the Time's up screen =================

    private void overlayTick(String fg, AppUsage hit) {
        // during Focus anything that is not allowed gets the Focus screen
        if (focusViolation(fg)) {
            if (overlay != null && !closing) hideOverlay(false);
            enforceFocus(fg);
            return;
        }
        if (closing) return;   // Force stop is running under the cover screen

        long now = System.currentTimeMillis();
        if (hit != null) {
            if (overlay == null) {
                if (now > holdUntil) showOverlay(hit);
            } else {
                if (!hit.pkg.equals(overlayPkg)) {
                    hideWarning(false);
                    setOverlayTexts(hit);
                    scheduleClose(autoCloseMs());
                }
                if (!warningOpen) {
                    setCountdown(Math.max(1, (closeAt - now + 999) / 1000));
                }
            }
        } else if (overlay != null) {
            hideOverlay(true);   // user left on their own: still close the app
        }
    }

    // "Time's up" stays longer while the emergency bypass is still there to think about
    private long autoCloseMs() {
        return BypassStore.available(this) ? 6000 : 1200;
    }

    private void scheduleClose(long ms) {
        closeAt = System.currentTimeMillis() + ms;
        ui.removeCallbacks(autoClose);
        ui.postDelayed(autoClose, ms);
    }

    // ================= lifecycle =================

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        worker = new HandlerThread("nds-guard", Process.THREAD_PRIORITY_BACKGROUND);
        worker.start();
        bg = new Handler(worker.getLooper());

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        screenOn = (pm == null) || pm.isInteractive();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        ContextCompat.registerReceiver(this, screenReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        bg.removeCallbacks(loop);
        if (screenOn) bg.post(loop);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(screenReceiver);
        } catch (Exception ignored) {
        }
        bg.removeCallbacksAndMessages(null);
        worker.quitSafely();
        hideOverlay(false);
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startAsForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "N.D.S guard", NotificationManager.IMPORTANCE_LOW));

        PendingIntent pi = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);

        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("N.D.S")
                .setContentText("Be in Your Limits")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    // ================= Accessibility on demand (banking-safe) =================

    // With the one-time ADB grant, the Accessibility service is ON only while it has a job:
    //  - Force stop is running, or
    //  - protection is active AND Settings / installer / Play Store is on the screen.
    // Everywhere else (banking apps, UPI, everything) it is OFF, so nobody sees it.
    private void gateTick(String fg) {
        if (!AccessGate.canToggle(this)) return;   // no grant: the person manages it by hand
        boolean want = closing
                || (protectedNow && fg != null && WATCHED.contains(fg));
        if (AccessGate.isEnabled(this) != want) AccessGate.set(this, want);
    }

    // ================= Focus =================

    // true if this app must not be on the screen during Focus
    private boolean focusViolation(String fg) {
        Set<String> allowed = focusAllowed;
        if (fg == null || allowed.isEmpty() || allowed.contains(fg)) return false;
        // Settings may show (under our cover) while N.D.S presses "Force stop"
        return !(fg.equals("com.android.settings")
                && System.currentTimeMillis() < forceStopUntil);
    }

    // bring the Focus screen to the front and close the app that should not be there
    private void enforceFocus(String pkg) {
        if (pkg == null) return;
        long now = System.currentTimeMillis();
        if (now < focusHoldUntil) return;
        focusHoldUntil = now + 800;

        try {
            Intent i = new Intent(this, FocusActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch (Exception ignored) {
        }

        // never close the launcher or the status bar
        if (!pkg.equals(SystemApps.home(this)) && !pkg.startsWith("com.android.systemui")) {
            ui.postDelayed(() -> killNow(pkg), 1000);
        }
    }

    // ================= Time's up screen =================

    private void showOverlay(AppUsage u) {
        ContextThemeWrapper themed = new ContextThemeWrapper(this, R.style.Theme_NDS_App);
        View v = LayoutInflater.from(themed).inflate(R.layout.overlay_block, null);

        v.findViewById(R.id.overlayHome).setOnClickListener(b -> closeBlockedApp());
        v.findViewById(R.id.overlayNo).setOnClickListener(b -> hideWarning(true));
        v.findViewById(R.id.overlayYes).setOnClickListener(b -> useBypass());

        overlay = v;
        warningOpen = false;
        closing = false;
        setOverlayTexts(u);
        setCountdown(autoCloseMs() / 1000);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        try {
            wm.addView(v, lp);
            overlayUp = true;
            scheduleClose(autoCloseMs());
        } catch (Exception ex) {
            overlay = null;
            overlayPkg = null;
            overlayUp = false;
        }
    }

    private void setOverlayTexts(AppUsage u) {
        overlayPkg = u.pkg;

        // the locked app's own logo, grey, with the red lock badge
        ImageView logo = overlay.findViewById(R.id.overlayLogo);
        try {
            Drawable icon = getPackageManager().getApplicationIcon(u.pkg);
            logo.setImageDrawable(LockedIcon.of(this, icon));
        } catch (PackageManager.NameNotFoundException e) {
            logo.setImageDrawable(null);
        }

        ((TextView) overlay.findViewById(R.id.overlayMsg))
                .setText(u.name + " is locked until tomorrow.");
        ((TextView) overlay.findViewById(R.id.overlaySub))
                .setText(UsageRepository.formatMinutes(u.limitMin)
                        + " limit used. No extensions.");

        // Emergency Bypass button (below "Close app"); tapping it opens the warning card
        TextView bypass = overlay.findViewById(R.id.overlayBypass);
        bypass.setVisibility(View.VISIBLE);
        if (BypassStore.available(this)) {
            bypass.setText("Emergency Bypass");
            bypass.setAlpha(1f);
            bypass.setOnClickListener(b -> showWarning());
        } else {
            bypass.setText("Emergency Bypass used today");
            bypass.setAlpha(0.45f);
            bypass.setOnClickListener(null);
            bypass.setClickable(false);
        }
    }

    private void setCountdown(long seconds) {
        if (overlay == null) return;
        ((TextView) overlay.findViewById(R.id.overlayCountdown))
                .setText("Closing in " + seconds + "s");
    }

    // the cover screen while Force stop runs: no buttons, just "Closing <app>…"
    private void showClosingState(String pkg) {
        if (overlay == null) return;
        overlay.findViewById(R.id.overlayHome).setVisibility(View.GONE);
        overlay.findViewById(R.id.overlayBypass).setVisibility(View.GONE);
        overlay.findViewById(R.id.overlayWarning).setVisibility(View.GONE);
        blurContent(false);
        ((TextView) overlay.findViewById(R.id.overlayCountdown))
                .setText("Closing " + UsageRepository.labelFor(this, pkg) + "…");
    }

    // blur (Android 12+) or clear the Time's up card behind the warning
    private void blurContent(boolean on) {
        if (overlay == null || Build.VERSION.SDK_INT < 31 || !Perf.blur(this)) return;
        View content = overlay.findViewById(R.id.overlayContent);
        if (content == null) return;
        content.setRenderEffect(on
                ? RenderEffect.createBlurEffect(22f, 22f, Shader.TileMode.CLAMP) : null);
    }

    // "Are you sure this is an emergency situation to use Bypass?"
    private void showWarning() {
        if (overlay == null || closing) return;
        warningOpen = true;
        ui.removeCallbacks(autoClose);          // pause: do not close the app under the card
        ui.removeCallbacks(warnTimeout);
        ui.postDelayed(warnTimeout, WARNING_TIMEOUT_MS);
        overlay.findViewById(R.id.overlayWarning).setVisibility(View.VISIBLE);
        blurContent(true);
    }

    private void hideWarning(boolean resumeClose) {
        warningOpen = false;
        ui.removeCallbacks(warnTimeout);
        if (overlay == null || closing) return;
        overlay.findViewById(R.id.overlayWarning).setVisibility(View.GONE);
        blurContent(false);
        if (resumeClose) scheduleClose(AFTER_NO_MS);
    }

    // "Yes, Bypass": grant today's one bypass to the app on the screen
    private void useBypass() {
        if (overlay == null || overlayPkg == null || closing) return;
        String pkg = overlayPkg;
        String name = UsageRepository.labelFor(this, pkg);

        BypassStore.use(this, pkg);
        locked.remove(pkg);            // not locked any more: the new limit is 15 min longer
        hideOverlay(false);            // close the screen, but do NOT close the app

        Toast.makeText(this, "Bypass on: " + BypassStore.MINUTES + " more minutes for "
                + name, Toast.LENGTH_LONG).show();
    }

    private void hideOverlay(boolean kill) {
        ui.removeCallbacks(autoClose);
        ui.removeCallbacks(warnTimeout);
        ui.removeCallbacks(closeSafety);
        warningOpen = false;
        closing = false;
        if (overlay == null) {
            overlayUp = false;
            return;
        }

        final String pkg = overlayPkg;
        try {
            wm.removeView(overlay);
        } catch (IllegalArgumentException ignored) {
        }
        overlay = null;
        overlayPkg = null;
        overlayUp = false;

        if (kill && pkg != null) {
            // the app is in the background now: close its processes
            ui.postDelayed(() -> killNow(pkg), 1000);
        }
    }

    // ================= closing apps =================

    private void killApp(String pkg) {
        ActivityManager am = getSystemService(ActivityManager.class);
        if (am != null) am.killBackgroundProcesses(pkg);
    }

    // close the background processes now, and again a little later
    private void killNow(String pkg) {
        killApp(pkg);
        ui.postDelayed(() -> killApp(pkg), 800);
        ui.postDelayed(() -> killApp(pkg), 2500);
    }

    private void goHome() {
        try {
            Intent h = new Intent(Intent.ACTION_MAIN);
            h.addCategory(Intent.CATEGORY_HOME);
            h.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(h);
        } catch (Exception ignored) {
        }
    }

    private boolean canForceStop(String pkg) {
        if (pkg == null || pkg.equals(getPackageName())) return false;
        // needs Accessibility: running now, or switchable on demand (ADB grant)
        if (!GuardAccessibilityService.isRunning() && !AccessGate.canToggle(this)) return false;
        long now = System.currentTimeMillis();
        if (now < forceStopBrokenUntil) return false;               // did not work on this phone
        Long last = forceStopAt.get(pkg);
        return last == null || now - last >= FORCE_STOP_GAP_MS;
    }

    // closes the blocked app for good
    private void closeBlockedApp() {
        if (overlay == null || closing) return;
        final String pkg = overlayPkg;

        // simple way (no Accessibility): home screen + close the background processes
        if (!canForceStop(pkg)) {
            holdUntil = System.currentTimeMillis() + 800;
            hideOverlay(true);
            goHome();
            return;
        }

        // Force stop, hidden under our own cover screen: Settings is never visible
        closing = true;
        closingPkg = pkg;
        ui.removeCallbacks(autoClose);
        ui.removeCallbacks(warnTimeout);
        warningOpen = false;
        showClosingState(pkg);

        long now = System.currentTimeMillis();
        forceStopAt.put(pkg, now);
        forceStopUntil = now + 8000;
        ui.postDelayed(closeSafety, CLOSE_SAFETY_MS);   // never stay here forever

        if (GuardAccessibilityService.isRunning()) {
            launchForceStop(pkg);
        } else {
            AccessGate.set(this, true);       // switched on just for this, off again right after
            awaitAccessibility(pkg, 0);
        }
    }

    // wait (up to 2 s) until Android has started the Accessibility service
    private void awaitAccessibility(String pkg, int tries) {
        if (!closing) return;
        if (GuardAccessibilityService.isRunning()) {
            launchForceStop(pkg);
            return;
        }
        if (tries >= 20) {   // it did not start: use the simple way for a while
            forceStopBrokenUntil = System.currentTimeMillis() + BROKEN_PAUSE_MS;
            finishClose(pkg);
            return;
        }
        ui.postDelayed(() -> awaitAccessibility(pkg, tries + 1), 100);
    }

    private void launchForceStop(String pkg) {
        GuardAccessibilityService.expectForceStop(pkg, pressed -> {
            if (!pressed) forceStopBrokenUntil = System.currentTimeMillis() + BROKEN_PAUSE_MS;
            finishClose(pkg);
        });

        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ex) {
            finishClose(pkg);
        }
    }

    // Force stop is done (or gave up): lift the cover, go home
    private void finishClose(String pkg) {
        if (!closing) return;
        holdUntil = System.currentTimeMillis() + 800;
        hideOverlay(false);          // also resets "closing"
        goHome();
        if (pkg != null) killNow(pkg);
    }
}
