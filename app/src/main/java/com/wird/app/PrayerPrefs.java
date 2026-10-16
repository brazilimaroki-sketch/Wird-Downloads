package com.wird.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

public final class PrayerPrefs {
    private static final String PREF = "wird_prayer_native";
    private static final String KEY = "payload";

    public static void save(Context c, JSONObject obj) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY, obj.toString()).apply();
    }

    public static JSONObject load(Context c) {
        try {
            String s = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null);
            return s == null ? null : new JSONObject(s);
        } catch (Exception e) { return null; }
    }

    public static void clear(Context c) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply();
    }

    public static boolean isPrayerEnabled(Context c, String prayer) {
        try {
            JSONObject root = load(c);
            if (root == null || !root.optBoolean("enabled", false)) return false;
            JSONObject prayers = root.optJSONObject("prayers");
            return prayers == null || prayers.optBoolean(prayer, true);
        } catch (Exception e) { return false; }
    }

    public static void recordPlaybackError(Context c, String message) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString("playback_error", message).apply();
    }

    public static String playbackError(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString("playback_error", "");
    }

    public static String alertMode(Context c) {
        JSONObject root = load(c);
        String mode = root == null ? "adhan" : root.optString("alertMode", "adhan");
        return "notification".equals(mode) ? "notification" : "adhan";
    }

    public static int adhanResource(Context c, String prayer) {
        JSONObject root = load(c);
        String main = root == null ? "default" : root.optString("adhanVoice", "default");
        boolean fajr = "Fajr".equals(prayer);
        String choice = fajr && root != null ? root.optString("fajrVoice", "same") : "same";
        if ("same".equals(choice)) choice = main;
        return AdhanCatalog.resource(choice, fajr);
    }

    public static boolean silentEnabled(Context c) {
        JSONObject root = load(c);
        return root != null && root.optBoolean("silentEnabled", false);
    }

    public static int silentMinutes(Context c) {
        JSONObject root = load(c);
        int minutes = root == null ? 25 : root.optInt("silentMinutes", 25);
        return Math.max(1, Math.min(180, minutes));
    }
}
