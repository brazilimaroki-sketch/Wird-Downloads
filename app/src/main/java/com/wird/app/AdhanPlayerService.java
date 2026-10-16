package com.wird.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.PowerManager;
import android.util.Log;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

/** Plays the bundled adhan on the alarm audio stream in a foreground service. */
public class AdhanPlayerService extends Service {

    public static final String ACTION_PLAY = "com.wird.app.ADHAN_PLAY";
    public static final String ACTION_STOP = "com.wird.app.ADHAN_STOP";
    public static final String ACTION_STATE_CHANGED = "com.wird.app.ADHAN_STATE_CHANGED";

    private static final String CHANNEL_ID = "wird_adhan_playback_quiet_v3";
    private static final String ALERT_CHANNEL_ID = "wird_adhan_fullscreen_v2";
    private static final int ALERT_ID = 9903;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingAlert;
    private final Runnable playbackTimeout = this::stopSelf;
    private static final int    NOTIF_ID    = 9900;
    /** الحد الأقصى لمدة تشغيل الأذان (5 دقائق) لتفادي التشغيل الأبدي */
    private static final int    MAX_MS      = 5 * 60 * 1000;

    private MediaPlayer player;
    private boolean silentAfterPlayback;
    private static volatile boolean active;

    public static boolean isActive() { return active; }

    // ───────────────────────────────────────────────────────────── lifecycle ──

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent == null ? ACTION_PLAY : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopPlayback();
            stopSelf();
            return START_NOT_STICKY;
        }

        // ACTION_PLAY (default)
        final String prayer = intent != null ? intent.getStringExtra("prayer") : null;
        final String city = intent != null ? intent.getStringExtra("city") : null;
        silentAfterPlayback = intent != null && intent.getBooleanExtra("silent_after", false);
        ensureChannel();
        clearPendingAlert();
        dismissPopup(this);
        startForeground(NOTIF_ID, buildNotification(prayer, city, false));
        int audioResource = "test".equals(prayer) && intent != null && intent.hasExtra("voice")
                ? AdhanCatalog.resource(intent.getStringExtra("voice"), true)
                : PrayerPrefs.adhanResource(this, prayer);
        startPlayback(audioResource);
        if (active) {
            // Give a directly opened activity time to resume before showing a fallback alert.
            pendingAlert = () -> {
                pendingAlert = null;
                if (active && !AdhanActivity.isScreenVisible()) {
                    NotificationManager nm = getSystemService(NotificationManager.class);
                    if (nm != null) {
                        try { nm.notify(ALERT_ID, buildNotification(prayer, city, true)); }
                        catch (SecurityException ignored) { /* Notifications disabled by user. */ }
                    }
                }
            };
            handler.postDelayed(pendingAlert, 900);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        clearPendingAlert();
        handler.removeCallbacks(playbackTimeout);
        dismissPopup(this);
        stopForeground(true);
        stopPlayback();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ─────────────────────────────────────────────────────────────── playback ─

    private void startPlayback(int audioResource) {
        stopPlayback(false); // نظف أي مشغّل سابق دون إغلاق شاشة الأذان الجديدة

        try {
            // Audio attributes must be supplied BEFORE the resource is prepared.
            // The direct resource reference also retains the bundled adhan in release builds.
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            player = MediaPlayer.create(this, audioResource, attributes, 0);
            if (player == null) throw new IllegalStateException("Bundled adhan could not be opened");
            player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            player.setVolume(1f, 1f); // Respects the user's alarm-volume setting.

            // أوقف تلقائياً بعد الانتهاء أو بعد MAX_MS
            player.setOnCompletionListener(mp -> {
                if (silentAfterPlayback) SilentModeManager.enableFromPrefs(this);
                stopSelf();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                PrayerPrefs.recordPlaybackError(this, "تعذر تشغيل ملف الأذان");
                Log.e("WirdAdhan", "MediaPlayer error: " + what + "/" + extra);
                stopSelf();
                return true;
            });

            // ضمان التوقف عند تجاوز الحد الأقصى
            handler.removeCallbacks(playbackTimeout);
            handler.postDelayed(playbackTimeout, MAX_MS);

            player.start();
            PrayerPrefs.recordPlaybackError(this, "");
            active = true;
            broadcastState(true);

        } catch (Exception e) {
            Log.e("WirdAdhan", "Adhan playback failed", e);
            PrayerPrefs.recordPlaybackError(this, "تعذر تشغيل الأذان؛ جرّب زر تجربة الأذان");
            active = false;
            stopSelf();
        }
    }

    private void stopPlayback() {
        stopPlayback(true);
    }

    private void stopPlayback(boolean notifyScreen) {
        clearPendingAlert();
        handler.removeCallbacks(playbackTimeout);
        dismissPopup(this);
        boolean wasActive = active || player != null;
        active = false;
        if (player != null) {
            try {
                if (player.isPlaying()) player.stop();
                player.release();
            } catch (Exception ignored) {}
            player = null;
        }
        if (notifyScreen && wasActive) broadcastState(false);
    }

    public static void requestStop(android.content.Context context) {
        Intent stop = new Intent(context, AdhanPlayerService.class).setAction(ACTION_STOP);
        try { context.startService(stop); }
        catch (Exception ignored) { context.stopService(new Intent(context, AdhanPlayerService.class)); }
    }

    private void broadcastState(boolean isActive) {
        try {
            sendBroadcast(new Intent(ACTION_STATE_CHANGED)
                    .setPackage(getPackageName())
                    .putExtra("active", isActive));
        } catch (Exception ignored) {}
    }

    // ─────────────────────────────────────────────────────── notification ──

    private void clearPendingAlert() {
        if (pendingAlert != null) {
            handler.removeCallbacks(pendingAlert);
            pendingAlert = null;
        }
    }

    public static void dismissPopup(android.content.Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(ALERT_ID);
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) return;
            NotificationChannel quiet = new NotificationChannel(
                    CHANNEL_ID, "تشغيل الأذان", NotificationManager.IMPORTANCE_LOW);
            quiet.setDescription("إشعار تشغيل هادئ لا يغطي شاشة الأذان");
            quiet.setSound(null, null);
            quiet.enableVibration(false);
            nm.createNotificationChannel(quiet);
            if (nm.getNotificationChannel(ALERT_CHANNEL_ID) == null) {
                NotificationChannel alert = new NotificationChannel(
                        ALERT_CHANNEL_ID, "تنبيه الأذان خارج الشاشة", NotificationManager.IMPORTANCE_HIGH);
                alert.setSound(null, null);
                alert.enableVibration(false);
                alert.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                nm.createNotificationChannel(alert);
            }
        }
    }

    private Notification buildNotification(String prayer, String city, boolean popup) {
        String prayerAr = arabicPrayerName(prayer);

        Intent screenIntent = new Intent(this, AdhanActivity.class)
                .putExtra("prayer", prayer)
                .putExtra("city", city)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent screenPi = PendingIntent.getActivity(
                this, 9902, screenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // زر إيقاف في الإشعار
        Intent stopIntent = new Intent(this, AdhanPlayerService.class)
                .setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(
                this, 9901, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this,
                popup ? ALERT_CHANNEL_ID : CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("test".equals(prayer) ? "تجربة الأذان" : "حان وقت صلاة " + prayerAr)
                .setContentText("الأذان قيد التشغيل • اضغط لفتح شاشة الأذان")
                .setOngoing(true)
                .setCategory(popup ? NotificationCompat.CATEGORY_ALARM : NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSound(null)
                .setPriority(popup ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setContentIntent(screenPi)
                .addAction(android.R.drawable.ic_media_pause, "إيقاف", stopPi);
        if (popup) builder.setFullScreenIntent(screenPi, true);
        else builder.setSilent(true);
        return builder.build();
    }

    private String arabicPrayerName(String key) {
        if (key == null) return "الصلاة";
        switch (key) {
            case "Fajr":    return "الفجر";
            case "Dhuhr":   return "الظهر";
            case "Asr":     return "العصر";
            case "Maghrib": return "المغرب";
            case "Isha":    return "العشاء";
            default:        return key;
        }
    }
}
