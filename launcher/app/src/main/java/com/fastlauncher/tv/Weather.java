package com.fastlauncher.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One weather line, cached ~45 minutes. Fetched on resume, never polled.
 */
final class Weather {

    static final String PREFS = "ambient";
    private static final long STALE_MS = 45 * 60 * 1000L;
    private static final AtomicBoolean fetching = new AtomicBoolean(false);

    static String line(Context ctx) {
        String s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("line", "");
        return s == null ? "" : s;
    }

    static void refresh(Context ctx, Runnable onDone) {
        Context app = ctx.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long age = System.currentTimeMillis() - p.getLong("at", 0L);
        if (age < STALE_MS && !line(app).isEmpty()) {
            if (onDone != null) onDone.run();
            return;
        }
        if (!fetching.compareAndSet(false, true)) {
            if (onDone != null) onDone.run();
            return;
        }
        new Thread(() -> {
            try {
                String next = fetch(app);
                if (next != null && !next.isEmpty()) {
                    p.edit().putString("line", next).putLong("at", System.currentTimeMillis()).apply();
                }
            } finally {
                fetching.set(false);
                if (onDone != null) new Handler(Looper.getMainLooper()).post(onDone);
            }
        }, "weather").start();
    }

    private static String fetch(Context ctx) {
        double lat = Double.NaN, lon = Double.NaN;
        String city = "";
        try {
            LocationManager lm = ctx.getSystemService(LocationManager.class);
            if (lm != null) {
                Location loc = firstLocation(lm);
                if (loc != null) {
                    lat = loc.getLatitude();
                    lon = loc.getLongitude();
                }
            }
        } catch (SecurityException ignored) {
        }
        if (Double.isNaN(lat)) {
            String geo = http("https://ipwho.is/?fields=latitude,longitude,city,success");
            if (geo == null) return null;
            try {
                JSONObject j = new JSONObject(geo);
                if (!j.optBoolean("success", true)) return null;
                lat = j.getDouble("latitude");
                lon = j.getDouble("longitude");
                city = j.optString("city", "");
            } catch (Exception e) {
                return null;
            }
        }
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat
                + "&longitude=" + lon + "&current=temperature_2m,weather_code&timezone=auto";
        String body = http(url);
        if (body == null) return null;
        try {
            JSONObject cur = new JSONObject(body).getJSONObject("current");
            int temp = (int) Math.round(cur.getDouble("temperature_2m"));
            String cond = condition(ctx, cur.getInt("weather_code"));
            if (city == null || city.isEmpty()) return temp + "°  " + cond;
            return temp + "°  " + cond + "  ·  " + city;
        } catch (Exception e) {
            return null;
        }
    }

    private static Location firstLocation(LocationManager lm) {
        String[] providers = {LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER,
                LocationManager.PASSIVE_PROVIDER};
        for (String p : providers) {
            try {
                Location loc = lm.getLastKnownLocation(p);
                if (loc != null) return loc;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String http(String spec) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(spec).openConnection();
            c.setConnectTimeout(2500);
            c.setReadTimeout(2500);
            c.setUseCaches(true);
            if (c.getResponseCode() != 200) return null;
            BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            in.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String condition(Context ctx, int code) {
        int id;
        if (code == 0) id = R.string.wx_clear;
        else if (code <= 3) id = R.string.wx_cloud;
        else if (code <= 48) id = R.string.wx_fog;
        else if (code <= 67) id = R.string.wx_rain;
        else if (code <= 77) id = R.string.wx_snow;
        else if (code <= 82) id = R.string.wx_showers;
        else id = R.string.wx_storm;
        return ctx.getString(id);
    }
}
