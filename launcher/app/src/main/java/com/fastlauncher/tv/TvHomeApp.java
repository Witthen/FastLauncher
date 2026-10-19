package com.fastlauncher.tv;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

/**
 * Lightweight idle reaper: one Handler on the main looper while the launcher process is alive
 * (it usually stays resident as the Home app). No extra Service.
 */
public class TvHomeApp extends Application {

    private static final long CHECK_MS = 60_000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            IdleReaper.tick(TvHomeApp.this);
            handler.postDelayed(this, CHECK_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler.postDelayed(loop, CHECK_MS);
    }
}
