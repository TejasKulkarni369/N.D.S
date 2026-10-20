package com.example.nds;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BlockService extends Service {

    private static final String CHANNEL_ID = "guard";
    private static final int NOTIF_ID = 1;
    private static final long FAST_MS = 150;              // how often a new open is looked for
    private static final long WARNING_TIMEOUT_MS = 20000; // unanswered warning counts as "No"
    private static final long AFTER_NO_MS = 3000;         // time left to close after "No"

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private View overlay;
    private String overlayPkg;
    private long holdUntil = 0;
    private long closeAt = 0;            // when "Time's up" will close the app
    private boolean warningOpen = false; // the "Are you sure?" card is showing

    // apps whose limit is used up right now (refreshed once per second)
    private final Map<String, AppUsage> locked = new HashMap<>();

    // Focus mode
    private Set<String> focusAllowed = new HashSet<>();   // empty = Focus is off
    private long focusHoldUntil = 0;
    private boolean focusWas = false;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            check();
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable fast = new Runnable() {
        @Override
        public void run() {
            fastCheck();
            handler.postDelayed(this, FAST_MS);
        }
    };

    private final Runnable autoClose = this::closeBlockedApp;
    private final Runnable warnTimeout = () -> hideWarning(true);

    // "Time's up" stays longer when the person still has the emergency bypass to think about
    private long autoCloseMs() {
        return BypassStore.available(this) ? 6000 : 2000;
    }

    private void scheduleClose(long ms) {
        closeAt = System.currentTimeMillis() + ms;
        handler.removeCallbacks(autoClose);
        handler.postDelayed(autoClose, ms);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        handler.removeCallbacks(tick);
        handler.removeCallbacks(fast);
        handler.post(tick);
        handler.post(fast);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(fast);
        hideOverlay(false);
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

    // once per second: totals, icon, Focus, which apps are locked, overlay bookkeeping
    private void check() {
        UsageRepository.Snapshot s = UsageRepository.snapshot(this);
        IconManager.apply(this, s);

        // Focus mode on/off
        boolean focus = FocusStore.isActive(this);
        if (focus) {
            focusAllowed = FocusStore.allowed(this);
        } else {
            if (focusWas) {
                Toast.makeText(this, "Focus complete", Toast.LENGTH_LONG).show();
            }
            focusAllowed = new HashSet<>();
        }
        focusWas = focus;

        locked.clear();
        for (AppUsage u : UsageRepository.usageList(this, s)) {
            if (u.usedMin >= u.limitMin) locked.put(u.pkg, u);
        }

        // during Focus anything that is not allowed gets the Focus screen
        if (focus && s.foreground != null && !focusAllowed.contains(s.foreground)) {
            if (overlay != null) hideOverlay(false);
            enforceFocus(s.foreground);
            return;
        }

        AppUsage hit = (s.foreground == null) ? null : locked.get(s.foreground);
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

    // every 150 ms: did a blocked app just open?
    private void fastCheck() {
        // Focus mode: only allowed apps may be on the screen
        if (!focusAllowed.isEmpty()) {
            String fg = recentForeground(5000);
            if (fg != null && !focusAllowed.contains(fg)) {
                enforceFocus(fg);
                return;
            }
        }

        // limits: did a locked app just open?
        if (locked.isEmpty() || overlay != null) return;
        if (System.currentTimeMillis() <= holdUntil) return;

        String fg = recentForeground(5000);
        AppUsage u = (fg == null) ? null : locked.get(fg);
        if (u != null) showOverlay(u);
    }

    // bring the Focus screen to the front and close the app that should not be there
    private void enforceFocus(String pkg) {
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
            handler.postDelayed(() -> killApp(pkg), 1200);
        }
    }

    // the app that came to the screen most recently (null if it already left)
    @SuppressWarnings("deprecation")
    private String recentForeground(long windowMs) {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(now - windowMs, now);
        if (events == null) return null;

        UsageEvents.Event e = new UsageEvents.Event();
        String cur = null;
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            int type = e.getEventType();
            if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                cur = e.getPackageName();
            } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                if (e.getPackageName().equals(cur)) cur = null;
            } else if (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                cur = null;
            }
        }
        return cur;
    }

    private void showOverlay(AppUsage u) {
        ContextThemeWrapper themed = new ContextThemeWrapper(this, R.style.Theme_NDS_App);
        View v = LayoutInflater.from(themed).inflate(R.layout.overlay_block, null);

        v.findViewById(R.id.overlayHome).setOnClickListener(b -> closeBlockedApp());
        v.findViewById(R.id.overlayNo).setOnClickListener(b -> hideWarning(true));
        v.findViewById(R.id.overlayYes).setOnClickListener(b -> useBypass());

        overlay = v;
        warningOpen = false;
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
            scheduleClose(autoCloseMs());
        } catch (Exception ex) {
            overlay = null;
            overlayPkg = null;
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

    // blur (Android 12+) or clear the Time's up card behind the warning
    private void blurContent(boolean on) {
        if (overlay == null || Build.VERSION.SDK_INT < 31) return;
        View content = overlay.findViewById(R.id.overlayContent);
        if (content == null) return;
        content.setRenderEffect(on
                ? RenderEffect.createBlurEffect(22f, 22f, Shader.TileMode.CLAMP) : null);
    }

    // "Are you sure this is an emergency situation to use Bypass?"
    private void showWarning() {
        if (overlay == null) return;
        warningOpen = true;
        handler.removeCallbacks(autoClose);          // pause: do not close the app under the card
        handler.removeCallbacks(warnTimeout);
        handler.postDelayed(warnTimeout, WARNING_TIMEOUT_MS);
        overlay.findViewById(R.id.overlayWarning).setVisibility(View.VISIBLE);
        blurContent(true);
    }

    private void hideWarning(boolean resumeClose) {
        warningOpen = false;
        handler.removeCallbacks(warnTimeout);
        if (overlay == null) return;
        overlay.findViewById(R.id.overlayWarning).setVisibility(View.GONE);
        blurContent(false);
        if (resumeClose) scheduleClose(AFTER_NO_MS);
    }

    // "Yes, Bypass": grant today's one bypass to the app on the screen
    private void useBypass() {
        if (overlay == null || overlayPkg == null) return;
        String pkg = overlayPkg;
        String name = UsageRepository.labelFor(this, pkg);

        BypassStore.use(this, pkg);
        locked.remove(pkg);            // not locked any more: the new limit is 15 min longer
        hideOverlay(false);            // close the screen, but do NOT close the app

        Toast.makeText(this, "Bypass on: " + BypassStore.MINUTES + " more minutes for "
                + name, Toast.LENGTH_LONG).show();
    }

    private void hideOverlay(boolean kill) {
        handler.removeCallbacks(autoClose);
        handler.removeCallbacks(warnTimeout);
        warningOpen = false;
        if (overlay == null) return;

        final String pkg = overlayPkg;
        try {
            wm.removeView(overlay);
        } catch (IllegalArgumentException ignored) {
        }
        overlay = null;
        overlayPkg = null;

        if (kill && pkg != null) {
            // the app is in the background now, so Android allows closing it
            handler.postDelayed(() -> killApp(pkg), 1200);
        }
    }

    private void killApp(String pkg) {
        ActivityManager am = getSystemService(ActivityManager.class);
        if (am != null) am.killBackgroundProcesses(pkg);
    }

    private void closeBlockedApp() {
        if (overlay == null) return;
        holdUntil = System.currentTimeMillis() + 800;
        hideOverlay(true);

        Intent h = new Intent(Intent.ACTION_MAIN);
        h.addCategory(Intent.CATEGORY_HOME);
        h.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(h);
    }
}