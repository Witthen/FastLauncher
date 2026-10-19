package com.fastlauncher.tv;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.content.pm.ApplicationInfo;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.StatFs;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Home de FastLauncher: dock inferior, rejilla de apps y un borde de foco que sigue al mando.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    private static final long MINUTE_MS = 60_000L;
    private static final long STATS_MS = 15_000L;
    private String liveTvPkg;

    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private GlassView glass;
    private View ambient;
    private TextView heroName;
    private GradientDrawable ambientGlow;
    private int ambientColor;
    private boolean heroShown;
    private boolean firstPaint = true;
    private TextView greeting;
    private TextView weatherView;
    private TextView memStatsView;
    private TextView nowPlayingView;
    private TextView btnWifi;
    private MediaSessionManager sessionMgr;
    private ComponentName sessionListener;
    private MediaController playingCtrl;
    private MediaController.Callback playingCb;
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = this::onSessions;
    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    private boolean glassShown;
    // Static so they survive the activity being destroyed while another app is open (low-memory TVs,
    // or Developer options → "Don't keep activities"); they only reset when the process dies.
    private static int lastFocused = 0;
    /** App of the last focused tile, so focus follows it when the app list changes. */
    private static String lastFocusedKey;
    private float density;

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Refreshes the network pill when the connection changes: at boot Wi-Fi often connects after Home is shown. */
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateWifiLabel();
            showWeather();
            handler.postDelayed(this, MINUTE_MS);
        }
    };
    private final Runnable statsTick = new Runnable() {
        @Override
        public void run() {
            updateMemStats();
            handler.postDelayed(this, STATS_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        COLUMNS = Math.max(1, getResources().getInteger(R.integer.columns));
        liveTvPkg = getString(R.string.live_tv_package).trim();

        root = findViewById(R.id.root);
        glass = findViewById(R.id.glass);
        ambient = findViewById(R.id.ambient);
        heroName = findViewById(R.id.heroName);
        ambientGlow = new GradientDrawable();
        ambientGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambientGlow.setGradientCenter(0.32f, 0.42f);
        ambient.setBackground(ambientGlow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                ambientGlow.setGradientRadius((r - l) * 0.62f));
        greeting = findViewById(R.id.greeting);
        weatherView = findViewById(R.id.weather);
        memStatsView = findViewById(R.id.memStats);
        nowPlayingView = findViewById(R.id.nowPlaying);
        btnWifi = findViewById(R.id.btnWifi);
        sessionMgr = getSystemService(MediaSessionManager.class);
        sessionListener = new ComponentName(this, SessionListener.class);

        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        grid.addItemDecoration(new DockDecoration(COLUMNS, density));
        grid.setLayoutAnimation(new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f));
        adapter = new AppAdapter(this, this, COLUMNS);
        grid.setAdapter(adapter);

        // El dock queda abajo; la rejilla queda fuera hasta pulsar Abajo.
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            int dockRow = (int) ((145 + 6 + 12) * density);   // item height plus DockDecoration offsets
            int want = Math.max(0, h - dockRow - (int) (30 * density));
            if (grid.getPaddingTop() != want) {
                grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), grid.getPaddingBottom());
            }
        });

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!glassShown) return;
                glassTargetY -= dy;
                if (springY.isRunning()) springY.animateToFinalPosition(glassTargetY);
                else glass.setTranslationY(glassTargetY);
            }
        });

        // Liquid feel: soft springs on the highlight's position.
        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_Y);
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            s.setSpring(new SpringForce().setStiffness(260f).setDampingRatio(0.58f));
        }

        View btnInputs = findViewById(R.id.btnInputs);
        View btnTools = findViewById(R.id.btnTools);
        View btnSettings = findViewById(R.id.btnSettings);
        btnInputs.setOnClickListener(v -> showInputs());
        btnWifi.setOnClickListener(v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        btnTools.setOnClickListener(v -> showTools());
        btnSettings.setOnClickListener(v -> open(new Intent(Settings.ACTION_SETTINGS)));
        btnSettings.setOnLongClickListener(v -> {
            unhideAllApps();
            return true;
        });

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v.getId() == R.id.btnInputs) return true;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnSettings;
        };
        btnInputs.setOnKeyListener(pillKeys);
        btnWifi.setOnKeyListener(pillKeys);
        btnTools.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);

        // Hide the glass when focus leaves the tiles.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) hideGlass();
        });

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.registerDefaultNetworkCallback(networkCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();
        glassShown = false;
        glass.setAlpha(0f);
        heroShown = false;
        heroName.setAlpha(0f);
        ambient.setAlpha(0f);
        // Read these before reload(): rebinding can move focus to the first tile and overwrite them.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        adapter.reload();
        if (firstPaint) {
            grid.scheduleLayoutAnimation();
            firstPaint = false;
        }
        updateGreeting();
        updateWifiLabel();
        showWeather();
        Weather.refresh(this, this::showWeather);
        watchNowPlaying(true);
        updateMemStats();
        IdleReaper.tick(this);
        handler.postDelayed(minuteTick, MINUTE_MS);
        handler.postDelayed(statsTick, STATS_MS);

        // Return to the app we left from, or the same slot if that app is gone. Always refocus: after
        // reload() the view that holds focus may no longer show the app it had.
        grid.post(() -> {
            int pos = adapter.positionOf(returnKey);
            focusTile(pos >= 0 ? pos : returnSlot);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(minuteTick);
        handler.removeCallbacks(statsTick);
        watchNowPlaying(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.unregisterNetworkCallback(networkCallback);
    }

    @Override
    public void onBackPressed() {
        // Home screen: Back goes nowhere.
    }

    // ---- glass highlight -------------------------------------------------------------------------

    @Override
    public void onTileFocused(int position, View card) {
        lastFocused = position;
        lastFocusedKey = adapter.keyAt(position);
        // Tile rect in root coordinates, using layout positions (ignores the scale animation).
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float pad = 6f * density;        // matches GlassView's glow inset
        float w = card.getWidth() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float h = card.getHeight() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float y = r.exactCenterY() - h / 2f;

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
            glass.setRadius(16f * density * AppAdapter.FOCUS_SCALE + pad);
        }
        glassTargetX = x;
        glassTargetY = y;
        if (!glassShown) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
            glass.animate().alpha(1f).setDuration(180).start();
            glassShown = true;
        } else {
            springX.animateToFinalPosition(x);
            springY.animateToFinalPosition(y);
        }
        View item = (View) card.getParent();
        if (position < COLUMNS) {
            // Dock: park it at the bottom of the screen and show the top shelf for this app.
            int decoratedTop = item.getTop() - (int) (6 * density);
            int dy = decoratedTop - grid.getPaddingTop();
            if (Math.abs(dy) > 1) grid.smoothScrollBy(0, dy);
            showHero(position, r.top - dy);
        } else {
            hideHero();
            // Scroll just enough so the whole glass (not only the tile) is visible.
            Rect want = new Rect(0, -(int) pad * 2, item.getWidth(), item.getHeight() + (int) pad);
            grid.requestChildRectangleOnScreen(item, want, false);
        }
    }

    // ---- top shelf -------------------------------------------------------------------------------

    private void showHero(int position, float dockTopInRoot) {
        String label = adapter.labelAt(position);
        int color = adapter.ambientColorAt(position);
        float y = dockTopInRoot - 14 * density - 26 * density - heroName.getHeight();
        boolean changed = !label.contentEquals(heroName.getText());

        if (!heroShown) {
            heroName.setText(label);
            heroName.setY(y);
            heroName.setTranslationX(0f);
            heroName.animate().alpha(1f).setDuration(260).start();
            setAmbient(color);
            ambient.animate().alpha(1f).setDuration(600).start();
            heroShown = true;
        } else if (changed) {
            heroName.animate().alpha(0f).translationX(-12 * density).setDuration(120).withEndAction(() -> {
                heroName.setText(label);
                heroName.setY(y);
                heroName.setTranslationX(12 * density);
                heroName.animate().alpha(1f).translationX(0f).setDuration(200).start();
            }).start();
            if (color != ambientColor) {
                ambient.animate().alpha(0.35f).setDuration(200).withEndAction(() -> {
                    setAmbient(color);
                    ambient.animate().alpha(1f).setDuration(500).start();
                }).start();
            }
        } else {
            heroName.setY(y);
        }
    }

    private void setAmbient(int color) {
        ambientColor = color;
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] *= 0.40f;
        hsv[2] = Math.min(hsv[2], 0.52f);
        int c = Color.HSVToColor(hsv) & 0x00FFFFFF;
        ambientGlow.setColors(new int[]{0x3A000000 | c, 0x14000000 | c, 0x00000000});
    }

    private void hideHero() {
        if (!heroShown) return;
        heroShown = false;
        heroName.animate().alpha(0f).setDuration(160).start();
        ambient.animate().alpha(0f).setDuration(300).start();
    }

    private void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        glass.animate().alpha(0f).setDuration(160).start();
    }

    // ---- navigation ------------------------------------------------------------------------------

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        int count = adapter.getItemCount();
        int target;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < COLUMNS) return false;          // dock row: let focus move to the top bar
            target = position - COLUMNS;
        } else {
            int lastRowStart = ((count - 1) / COLUMNS) * COLUMNS;
            if (position >= lastRowStart) return true;     // already on the last row: stay put
            target = Math.min(position + COLUMNS, count - 1);
        }
        focusTile(target);
        return true;
    }

    private void focusTile(int position) {
        if (adapter.getItemCount() == 0) return;
        final int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            View card = vh.itemView.findViewById(R.id.card);
            // Already focused (e.g. back from an app): no focus change fires, so restore the glass by hand.
            if (card.isFocused()) onTileFocused(pos, card);
            else card.requestFocus();
            return;
        }
        // Not laid out yet: scroll there, then focus once the scroll settles.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pos);
                View target = h != null ? h.itemView.findViewById(R.id.card) : rv.getChildAt(0);
                if (target != null) target.requestFocus();
            }
        });
        grid.smoothScrollToPosition(pos);
    }

    // ---- top bar ---------------------------------------------------------------------------------

    private void updateMemStats() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (am != null) am.getMemoryInfo(mi);
        long diskFree = 0, diskTotal = 0;
        try {
            StatFs fs = new StatFs("/data");
            diskFree = fs.getAvailableBytes();
            diskTotal = fs.getTotalBytes();
        } catch (Exception ignored) {
        }
        memStatsView.setText(getString(R.string.mem_stats,
                humanSize(mi.availMem), humanSize(mi.totalMem),
                humanSize(diskFree), humanSize(diskTotal)));
    }

    private static String humanSize(long bytes) {
        if (bytes >= 1_000_000_000L) {
            return String.format(Locale.getDefault(), "%.1f GB", bytes / 1_000_000_000.0);
        }
        return String.format(Locale.getDefault(), "%.0f MB", bytes / 1_000_000.0);
    }

    private void showWeather() {
        String line = Weather.line(this);
        int want = line.isEmpty() ? View.GONE : View.VISIBLE;
        boolean changed = weatherView.getVisibility() != want
                || (want == View.VISIBLE && !line.contentEquals(weatherView.getText()));
        if (line.isEmpty()) {
            weatherView.setVisibility(View.GONE);
        } else {
            weatherView.setText(line);
            weatherView.setVisibility(View.VISIBLE);
        }
        if (changed) weatherView.post(this::rebindGlass);
    }

    private void rebindGlass() {
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(lastFocused);
        if (vh == null) return;
        View card = vh.itemView.findViewById(R.id.card);
        if (card != null && card.isFocused()) onTileFocused(lastFocused, card);
    }

    private void watchNowPlaying(boolean on) {
        if (sessionMgr == null) return;
        if (!on) {
            detachPlaying();
            try {
                sessionMgr.removeOnActiveSessionsChangedListener(sessionsChanged);
            } catch (Exception ignored) {
            }
            return;
        }
        try {
            sessionMgr.addOnActiveSessionsChangedListener(sessionsChanged, sessionListener, handler);
            onSessions(sessionMgr.getActiveSessions(sessionListener));
        } catch (SecurityException e) {
            nowPlayingView.setVisibility(View.GONE);
        }
    }

    private void onSessions(List<MediaController> sessions) {
        detachPlaying();
        MediaController best = null;
        if (sessions != null) {
            for (MediaController c : sessions) {
                PlaybackState st = c.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                    best = c;
                    break;
                }
            }
        }
        if (best == null) {
            nowPlayingView.setVisibility(View.GONE);
            return;
        }
        playingCtrl = best;
        playingCb = new MediaController.Callback() {
            @Override
            public void onMetadataChanged(MediaMetadata metadata) {
                handler.post(() -> paintPlaying());
            }

            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                handler.post(() -> paintPlaying());
            }
        };
        best.registerCallback(playingCb, handler);
        paintPlaying();
    }

    private void paintPlaying() {
        if (playingCtrl == null) return;
        PlaybackState st = playingCtrl.getPlaybackState();
        if (st == null || st.getState() != PlaybackState.STATE_PLAYING) {
            nowPlayingView.setVisibility(View.GONE);
            return;
        }
        MediaMetadata md = playingCtrl.getMetadata();
        String title = md != null ? md.getString(MediaMetadata.METADATA_KEY_TITLE) : null;
        String artist = md != null ? md.getString(MediaMetadata.METADATA_KEY_ARTIST) : null;
        if (title == null || title.isEmpty()) {
            nowPlayingView.setVisibility(View.GONE);
            return;
        }
        String line = (artist != null && !artist.isEmpty()) ? title + "  ·  " + artist : title;
        nowPlayingView.setText(getString(R.string.now_playing, line));
        nowPlayingView.setVisibility(View.VISIBLE);
    }

    private void detachPlaying() {
        if (playingCtrl != null && playingCb != null) {
            try {
                playingCtrl.unregisterCallback(playingCb);
            } catch (Exception ignored) {
            }
        }
        playingCtrl = null;
        playingCb = null;
    }

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.good_night;
        else if (hour < 12) res = R.string.good_morning;
        else if (hour < 17) res = R.string.good_afternoon;
        else if (hour < 22) res = R.string.good_evening;
        else res = R.string.good_night;
        greeting.setText(res);
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    private void updateWifiLabel() {
        String text = getString(R.string.wifi);
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm != null ? cm.getActiveNetwork() : null;
            NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
            if (caps == null) {
                text = getString(R.string.offline);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                text = getString(R.string.ethernet);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    && !text.equals(getString(R.string.offline))) {
                text = getString(R.string.vpn_ssid, text);
            }
        } catch (Exception ignored) {
        }
        btnWifi.setText(text);
    }

    @Override
    public void onTileLongPress(int position, View card) {
        String pkg = adapter.pkgAt(position);
        if (pkg == null) return;
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        int width = Math.max((int) (220 * density), card.getWidth());
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        List<String> dock = AppAdapter.loadDock(this, COLUMNS);
        int dockAt = dock.indexOf(pkg);
        if (dockAt >= 0) {
            if (dockAt > 0) {
                addMenuRow(rows, popup, getString(R.string.dock_left), () -> moveDock(pkg, -1));
            }
            if (dockAt < dock.size() - 1) {
                addMenuRow(rows, popup, getString(R.string.dock_right), () -> moveDock(pkg, 1));
            }
            addMenuRow(rows, popup, getString(R.string.dock_remove), () -> removeFromDock(pkg));
        } else {
            addMenuRow(rows, popup, getString(R.string.dock_add), () -> addToDock(pkg));
        }
        addMenuRow(rows, popup, getString(R.string.hide_app), () -> hideApp(pkg));
        addMenuRow(rows, popup, getString(R.string.app_info), () -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
            open(i);
        });
        popup.showAsDropDown(card, 0, (int) (8 * density));
        if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocus();
    }

    private void addMenuRow(LinearLayout rows, PopupWindow popup, String title, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        row.findViewById(R.id.icon).setVisibility(View.GONE);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        ((TextView) row.findViewById(R.id.status)).setVisibility(View.GONE);
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }

    private void addToDock(String pkg) {
        List<String> dock = AppAdapter.loadDock(this, COLUMNS);
        dock.remove(pkg);
        if (dock.size() >= COLUMNS) dock.remove(dock.size() - 1);
        dock.add(pkg);
        AppAdapter.saveDock(this, dock);
        adapter.reload();
        grid.post(() -> focusTile(Math.max(0, dock.indexOf(pkg))));
    }

    private void removeFromDock(String pkg) {
        List<String> dock = AppAdapter.loadDock(this, COLUMNS);
        dock.remove(pkg);
        AppAdapter.saveDock(this, dock);
        adapter.reload();
        grid.post(() -> focusTile(lastFocused));
    }

    private void moveDock(String pkg, int dir) {
        List<String> dock = AppAdapter.loadDock(this, COLUMNS);
        int i = dock.indexOf(pkg);
        int j = i + dir;
        if (i < 0 || j < 0 || j >= dock.size()) return;
        Collections.swap(dock, i, j);
        AppAdapter.saveDock(this, dock);
        adapter.reload();
        grid.post(() -> focusTile(j));
    }

    private void hideApp(String pkg) {
        SharedPreferences p = getSharedPreferences("launcher", MODE_PRIVATE);
        Set<String> hidden = new HashSet<>(p.getStringSet("user_hidden", Collections.emptySet()));
        hidden.add(pkg);
        p.edit().putStringSet("user_hidden", hidden).apply();
        adapter.reload();
        Toast.makeText(this, R.string.hidden_toast, Toast.LENGTH_LONG).show();
        grid.post(() -> focusTile(Math.min(lastFocused, Math.max(0, adapter.getItemCount() - 1))));
    }

    private void unhideAllApps() {
        getSharedPreferences("launcher", MODE_PRIVATE).edit().remove("user_hidden").apply();
        adapter.reload();
        Toast.makeText(this, R.string.unhidden_toast, Toast.LENGTH_SHORT).show();
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    /** Shortcuts a user launcher can actually run: RAM, Bluetooth, sound, screensaver. Reboot is attempted and explained if Android blocks it. */
    private void showTools() {
        View anchor = findViewById(R.id.btnTools);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        int width = Math.max(anchor.getWidth(), (int) (220 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);

        addMenuRow(rows, popup, getString(R.string.free_ram), this::freeRam);
        if (playingCtrl != null) {
            addMenuRow(rows, popup, getString(R.string.pause_media), () -> {
                try {
                    playingCtrl.getTransportControls().pause();
                } catch (Exception ignored) {
                }
            });
        }
        addMenuRow(rows, popup, getString(R.string.bluetooth),
                () -> open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        addMenuRow(rows, popup, getString(R.string.sound),
                () -> open(new Intent(Settings.ACTION_SOUND_SETTINGS)));
        addMenuRow(rows, popup, getString(R.string.apps),
                () -> open(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)));
        addMenuRow(rows, popup, getString(R.string.screensaver), this::startDream);
        addMenuRow(rows, popup, getString(R.string.reboot_tv), this::tryReboot);

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max(width, panel.getMeasuredWidth()));
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), Gravity.START);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocus();
    }

    private void freeRam() {
        final long before = ramAvail();
        IdleReaper.freeNow(this);
        handler.postDelayed(() -> {
            updateMemStats();
            long after = ramAvail();
            long gained = after - before;
            if (gained > 8_000_000L) {
                Toast.makeText(this, getString(R.string.ram_freed, humanSize(gained), humanSize(after)),
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.ram_already_free, Toast.LENGTH_LONG).show();
            }
        }, 700);
    }

    private long ramAvail() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (am != null) am.getMemoryInfo(mi);
        return mi.availMem;
    }

    private void startDream() {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            IBinder binder = (IBinder) sm.getMethod("getService", String.class).invoke(null, "dreams");
            Class<?> stub = Class.forName("android.service.dreams.IDreamManager$Stub");
            Object mgr = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
            mgr.getClass().getMethod("dream").invoke(mgr);
            return;
        } catch (Exception ignored) {
        }
        try {
            open(new Intent(Intent.ACTION_MAIN)
                    .setClassName("com.android.systemui", "com.android.systemui.Somnambulator"));
        } catch (Exception e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void tryReboot() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) pm.reboot(null);
            return;
        } catch (Exception ignored) {
        }
        Toast.makeText(this, R.string.reboot_denied, Toast.LENGTH_LONG).show();
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private void showInputs() {
        View anchor = findViewById(R.id.btnInputs);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        // Compact: as wide as the Inputs pill (a touch wider so "HDMI 1" fits), hanging right below it.
        int width = Math.max(anchor.getWidth(), (int) (150 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        String tunerId = null;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            // HDMI ports first in port order, then AV and the rest.
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            // Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in,
            // so drop the HDMI status then. A TV with every port really in use loses it too.
            int hdmiPorts = 0, hdmiConnected = 0;
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
                hdmiPorts++;
                if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
            }
            boolean hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts;
            for (TvInputInfo info : inputs) {
                if (info.getType() == TvInputInfo.TYPE_TUNER
                        && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (tunerId == null || info.getId().compareTo(tunerId) < 0)) {
                    tunerId = info.getId();
                }
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase().contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = hdmi && hdmiStateBogus ? -1 : tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                                TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel")));
            }
        }
        Intent liveTv = liveTvPkg.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (liveTv != null && tunerId != null) {
            liveTv = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(tunerId), "vnd.android.cursor.item/channel")
                    .setPackage(liveTvPkg);
        }
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            final Intent liveTvIntent = liveTv;
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(liveTvIntent));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        // Fit the widest row: TVs that report a status on every port (Sony) would otherwise squeeze the names out.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max(width, panel.getMeasuredWidth()));

        // Drop in: fade and unfold from the top edge.
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), Gravity.START);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }
}
