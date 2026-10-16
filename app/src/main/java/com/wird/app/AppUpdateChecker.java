package com.wird.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.HttpsURLConnection;

final class AppUpdateChecker {
    private static final String API = "https://api.github.com/repos/brazilimaroki-sketch/Wird-Downloads/releases/latest";
    private static final String DOWNLOAD = "https://wird-apk.pages.dev/";
    private static final String CHANNEL = "wird_app_updates_v1";
    private static final int ID = 9050;
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    interface Listener { void onResult(JSONObject notice); }

    static void check(Context context, boolean force, Listener listener) {
        Context app = context.getApplicationContext();
        if (!RUNNING.compareAndSet(false, true)) { listener.onResult(notice(app, cached(app), false)); return; }
        new Thread(() -> {
            JSONObject release = cached(app); boolean checked = release != null;
            SharedPreferences prefs = app.getSharedPreferences("wird_updates", Context.MODE_PRIVATE);
            long now = System.currentTimeMillis();
            try {
                boolean fresh = release != null && now - prefs.getLong("success", 0) < 21600000L;
                if (force || (!fresh && now - prefs.getLong("attempt", 0) > 900000L)) {
                    prefs.edit().putLong("attempt", now).apply();
                    release = fetch(); checked = true;
                    prefs.edit().putString("release", release.toString()).putLong("success", now).apply();
                }
                JSONObject result = notice(app, release, checked);
                notifyUpdate(app, prefs, result); listener.onResult(result);
            } catch (Exception ignored) {
                listener.onResult(notice(app, release, false));
            } finally { RUNNING.set(false); }
        }, "wird-update-check").start();
    }
    private static JSONObject cached(Context context) {
        try { return new JSONObject(context.getSharedPreferences("wird_updates", Context.MODE_PRIVATE).getString("release", "")); }
        catch (Exception ignored) { return null; }
    }
    private static JSONObject fetch() throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(API).openConnection();
        connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "Wird-Android-Update-Checker");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        try {
            if (connection.getResponseCode() != 200) throw new Exception("Update unavailable");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 524288) throw new Exception("Release response too large");
                    output.write(buffer, 0, count);
                }
                return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); }
    }
    private static JSONObject notice(Context context, JSONObject release, boolean checked) {
        JSONObject result = new JSONObject();
        try {
            String current = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            String version = release == null ? "" : UpdateVersion.normalize(release.optString("tag_name"));
            boolean apk = false; JSONArray assets = release == null ? null : release.optJSONArray("assets");
            for (int i = 0; assets != null && i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset != null && asset.optString("name").toLowerCase(java.util.Locale.ROOT).endsWith(".apk")
                        && "uploaded".equals(asset.optString("state")) && asset.optLong("size") > 0) apk = true;
            }
            boolean available = release != null && !release.optBoolean("draft") && !release.optBoolean("prerelease")
                    && apk && UpdateVersion.compare(version, current) > 0;
            result.put("available", available).put("version", version).put("checked", checked);
        } catch (Exception ignored) { /* A malformed release never announces an update. */ }
        return result;
    }
    private static void notifyUpdate(Context context, SharedPreferences prefs, JSONObject result) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (!result.optBoolean("available")) { if (result.optBoolean("checked")) manager.cancel(ID); return; }
        String version = result.optString("version");
        if (version.equals(prefs.getString("notified", "")) || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
                    new NotificationChannel(CHANNEL, "تحديثات تطبيق وِرد", NotificationManager.IMPORTANCE_DEFAULT));
            PendingIntent open = PendingIntent.getActivity(context, ID, new Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD)),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            manager.notify(ID, new NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_app_update)
                    .setContentTitle("تحديث وِرد " + version + " متوفر")
                    .setContentText("اضغط لفتح موقع التحميل والتحديث")
                    .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).build());
            prefs.edit().putString("notified", version).apply();
        } catch (SecurityException ignored) { /* The in-app notice remains available without notification permission. */ }
    }
}
