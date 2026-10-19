package com.fastlauncher.tv;

import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.Map;

/**
 * Closes user apps that have sat unused in the background for idle_kill_minutes.
 * Leaves the TV stack alone: HDMI/Live TV, Chromecast, Play Services, IME, remote.
 */
final class IdleReaper {

    static void tick(Context ctx) {
        int minutes = ctx.getResources().getInteger(R.integer.idle_kill_minutes);
        if (minutes <= 0) return;
        long now = System.currentTimeMillis();
        long maxIdle = minutes * 60_000L;
        UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return;
        Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(now - 24L * 60 * 60 * 1000, now);
        if (stats == null || stats.isEmpty()) return;

        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return;
        for (Map.Entry<String, UsageStats> e : stats.entrySet()) {
            String pkg = e.getKey();
            if (keep(pkg)) continue;
            long last = e.getValue().getLastTimeUsed();
            if (last <= 0 || now - last < maxIdle) continue;
            am.killBackgroundProcesses(pkg);
        }
    }

    /** Kill every non-system user app that is sitting in the background. Returns how many packages we asked Android to stop. */
    static int freeNow(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return 0;
        PackageManager pm = ctx.getPackageManager();
        int n = 0;
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if (keep(ai.packageName)) continue;
            am.killBackgroundProcesses(ai.packageName);
            n++;
        }
        return n;
    }

    private static boolean keep(String pkg) {
        if (pkg == null) return true;
        if (pkg.equals("com.fastlauncher.tv")) return true;
        if (pkg.startsWith("com.android.")) return true;
        if (pkg.startsWith("com.mediatek.")) return true;
        if (pkg.startsWith("com.vestel.")) return true;
        if (pkg.startsWith("com.dolby.")) return true;
        if (pkg.startsWith("com.google.android.gms")) return true;
        if (pkg.startsWith("com.google.android.gsf")) return true;
        if (pkg.startsWith("com.google.android.ext.")) return true;
        if (pkg.startsWith("com.google.android.overlay")) return true;
        if (pkg.equals("com.google.android.webview")) return true;
        if (pkg.equals("com.google.android.permissioncontroller")) return true;
        if (pkg.startsWith("com.google.android.inputmethod.")) return true;
        if (pkg.equals("com.google.android.apps.mediashell")) return true;
        if (pkg.startsWith("com.google.android.tv.remote")) return true;
        if (pkg.equals("com.google.android.packageinstaller")) return true;
        if (pkg.equals("com.google.android.providers.media.module")) return true;
        return pkg.startsWith("android.");
    }
}
