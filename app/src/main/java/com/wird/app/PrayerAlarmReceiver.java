package com.wird.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.core.app.NotificationCompat;

public class PrayerAlarmReceiver extends BroadcastReceiver {

    private static final String CHANNEL = "wird_prayer";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String prayer = intent.getStringExtra("prayer");
        final String city   = intent.getStringExtra("city");
        final int    id     = intent.getIntExtra("id", 0);

        if(intent.getBooleanExtra("reminder",false)) {
            if(PrayerPrefs.isPrayerEnabled(context,prayer)) {
                try{showNotification(context,prayer,city,id,true);}catch(RuntimeException e){android.util.Log.e("WirdAlarm","Reminder notification failed",e);}
            }
            return; // A pre-prayer reminder never starts adhan or silent mode.
        }

        final boolean test = intent.getBooleanExtra("test", false);
        if(!test&&!PrayerScheduler.shouldDeliver(context,id,prayer,city,intent.getLongExtra("scheduledAt",0))) {
            PrayerScheduler.rescheduleSaved(context);return;
        }
        PrayerScheduler.markDelivered(context, id);

        // قد يصل PendingIntent قديم بعد تغيير الإعدادات؛ افحص التفضيلات مرة أخرى.
        if (!test && !PrayerPrefs.isPrayerEnabled(context, prayer)) {
            PrayerScheduler.rescheduleSaved(context);
            return;
        }

        if(!test)PrayerDiagnostics.recordDelivery(context,prayer,intent.getLongExtra("scheduledAt",0));
        try {
            // ① عرض إشعار الصلاة
            // Full adhan playback owns its notification; avoid a second heads-up alert.
            if (!test && !"adhan".equals(PrayerPrefs.alertMode(context)) && !AdhanActivity.isScreenVisible()) {
                showNotification(context, prayer, city, id);
            }
            vibrateOnce(context);

            // ② الأذان الكامل أو إشعار فقط حسب اختيار المستخدم.
            if (test || "adhan".equals(PrayerPrefs.alertMode(context))) {
                Intent adhanIntent = new Intent(context, AdhanPlayerService.class)
                        .setAction(AdhanPlayerService.ACTION_PLAY)
                        .putExtra("prayer", prayer)
                        .putExtra("city", city)
                        .putExtra("silent_after", !test && PrayerPrefs.silentEnabled(context));
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(adhanIntent);
                } else {
                    context.startService(adhanIntent);
                }
            } else {
                // لا يوجد صوت أذان؛ يبدأ الصامت مباشرة بعد إشعار الصلاة.
                if (PrayerPrefs.silentEnabled(context)) {
                    SilentModeManager.enableFromPrefs(context);
                }
            }

        } catch (RuntimeException e) {
            android.util.Log.e("WirdAdhan", "Could not start scheduled adhan", e);
            PrayerPrefs.recordPlaybackError(context, "تعذر بدء الأذان بالخلفية؛ افحص المنبّه الدقيق والأذونات");
            try { showNotification(context, prayer, city, id); } catch (RuntimeException ignored) {}
        } finally {
            // A rejected foreground-service start must not remove tomorrow's alarms.
            PrayerScheduler.rescheduleSaved(context);
            PrayerWidgetProvider.updateAll(context);
        }
    }

    // ─────────────────────────────────────────────────────────── notification ─

    private void showNotification(Context context,String prayer,String city,int id) {showNotification(context,prayer,city,id,false);}
    private void showNotification(Context context, String prayer, String city, int id,boolean early) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "مواقيت الصلاة", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("تنبيهات الصلاة في تطبيق وِرد");
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 320, 180, 320});
            nm.createNotificationChannel(ch);
        }

        Intent open = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                context, 8000 + id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        nm.notify((early?9100:9000) + id, new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle((early?"اقترب وقت صلاة ":"حان وقت صلاة ") + arabicName(prayer))
                .setContentText(city == null ? "وِرد" : city)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(new long[]{0, 320, 180, 320})
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build());
    }

    /** اهتزاز قصير مرة واحدة عند دخول وقت الصلاة، حتى مع صوت أذان خارجي. */
    private void vibrateOnce(Context context) {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            long[] pattern = new long[]{0, 320, 180, 320};
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        } catch (Exception ignored) {}
    }

    private String arabicName(String key) {
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
