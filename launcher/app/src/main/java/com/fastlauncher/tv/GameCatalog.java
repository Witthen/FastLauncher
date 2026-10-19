package com.fastlauncher.tv;

import android.content.pm.ApplicationInfo;
import android.os.Build;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Detects games and cloud-gaming hubs on Android TV.
 * Uses {@link ApplicationInfo#CATEGORY_GAME} / {@code FLAG_IS_GAME}, plus a small hub allowlist
 * for stores/streamers that often omit the game category.
 */
final class GameCatalog {

    /** Cloud gaming, game stores and controllers that belong in Modo gaming even without CATEGORY_GAME. */
    private static final Set<String> HUBS = new HashSet<>(Arrays.asList(
            "com.google.android.play.games",
            "com.nvidia.geforcenow",
            "com.amazon.luna",
            "com.microsoft.xboxone.smartglass",
            "com.microsoft.xbox.smartglass.tv",
            "com.valvesoftware.steamlink",
            "com.moonlight_stream.moonlight_android",
            "com.limelight",
            "com.parsec.android",
            "tv.cloudrig.android",
            "com.boosteroid.app",
            "com.antutu.ABenchMark"
    ));

    private GameCatalog() {
    }

    static boolean isGame(ApplicationInfo info) {
        if (info == null) return false;
        if (HUBS.contains(info.packageName)) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (info.category == ApplicationInfo.CATEGORY_GAME) return true;
        }
        //noinspection deprecation
        if ((info.flags & ApplicationInfo.FLAG_IS_GAME) != 0) return true;
        String pkg = info.packageName == null ? "" : info.packageName.toLowerCase(Locale.US);
        return pkg.contains("geforcenow") || pkg.contains("steamlink") || pkg.contains("moonlight")
                || pkg.contains(".luna") || pkg.endsWith(".luna");
    }

    static boolean isGamingMode(android.content.Context ctx) {
        android.content.SharedPreferences p = ctx.getSharedPreferences("launcher", android.content.Context.MODE_PRIVATE);
        if (!p.contains("gaming_mode")) {
            return ctx.getResources().getInteger(R.integer.gaming_mode_default) != 0;
        }
        return p.getBoolean("gaming_mode", false);
    }

    static void setGamingMode(android.content.Context ctx, boolean on) {
        ctx.getSharedPreferences("launcher", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("gaming_mode", on).apply();
    }
}
