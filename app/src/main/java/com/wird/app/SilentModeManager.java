package com.wird.app;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/** يدير وضع الصامت المؤقت بعد انتهاء الأذان ويحفظ حالة النظام السابقة لاسترجاعها. */
public final class SilentModeManager {
    private static final String PREF = "wird_silent_mode";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_PREVIOUS = "previous_filter";
    private static final int REQUEST_CODE = 9950;

    private SilentModeManager() {}

    public static boolean enableFromPrefs(Context context) {
        if (!PrayerPrefs.silentEnabled(context)) return false;
        return enable(context, PrayerPrefs.silentMinutes(context));
    }

    public static boolean enable(Context context, int requestedMinutes) {
        try {
            NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (!nm.isNotificationPolicyAccessGranted()) return false;

            int minutes = Math.max(1, Math.min(180, requestedMinutes));
            SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            if (!prefs.getBoolean(KEY_ACTIVE, false)) {
                prefs.edit()
                        .putInt(KEY_PREVIOUS, nm.getCurrentInterruptionFilter())
                        .putBoolean(KEY_ACTIVE, true)
                        .apply();
            }

            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);

            Intent restoreIntent = new Intent(context, SilentModeReceiver.class)
                    .setAction(SilentModeReceiver.ACTION_RESTORE);
            PendingIntent restore = PendingIntent.getBroadcast(
                    context, REQUEST_CODE, restoreIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            long at = System.currentTimeMillis() + minutes * 60_000L;
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, restore);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, restore);
            }
            return true;
        } catch (Exception ignored) { return false; }
    }

    public static void restore(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            if (!prefs.getBoolean(KEY_ACTIVE, false)) return;
            int previous = prefs.getInt(
                    KEY_PREVIOUS, NotificationManager.INTERRUPTION_FILTER_ALL);
            NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            // إذا غيّر المستخدم وضع عدم الإزعاج يدوياً أثناء المؤقت فلا نتجاوز اختياره.
            if (nm.isNotificationPolicyAccessGranted()
                    && nm.getCurrentInterruptionFilter()
                    == NotificationManager.INTERRUPTION_FILTER_NONE) {
                nm.setInterruptionFilter(previous);
            }
            prefs.edit().clear().apply();
        } catch (Exception ignored) {}
    }

    public static boolean isActive(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getBoolean(KEY_ACTIVE, false);
    }
}
