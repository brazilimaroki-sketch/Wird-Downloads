package com.wird.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONObject;

import java.util.Calendar;

/** جدولة تذكيرات القراءة وأذكار الصباح والمساء. */
public final class ReminderScheduler {
    private static final String PREF = "wird_reading_reminders";
    private static final String KEY_PAYLOAD = "payload";
    private static final int RC_WIRD = 7300, RC_KAHF = 7301, RC_MULK = 7302,
            RC_BAQARAH = 7303, RC_AZKAR_MORNING = 7304, RC_AZKAR_EVENING = 7305;

    private ReminderScheduler() {}

    public static void saveAndSchedule(Context context, JSONObject payload) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(KEY_PAYLOAD, payload.toString()).apply();
        schedule(context, payload);
    }

    public static JSONObject load(Context context) {
        try {
            SharedPreferences p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String raw = p.getString(KEY_PAYLOAD, null);
            return raw == null ? null : new JSONObject(raw);
        } catch (Exception e) { return null; }
    }

    public static void rescheduleSaved(Context context) {
        JSONObject p = load(context);
        if (p != null) schedule(context, p);
    }

    public static boolean isEnabled(Context context, String type) {
        JSONObject p = load(context);
        return p != null && p.optBoolean(type + "Enabled", false);
    }

    public static void schedule(Context context, JSONObject payload) {
        cancelAll(context);
        if (payload.optBoolean("wirdEnabled", true))
            scheduleOne(context, RC_WIRD, "wird", payload.optString("wirdTime", "09:00"), 0);
        if (payload.optBoolean("kahfEnabled", true))
            scheduleOne(context, RC_KAHF, "kahf", payload.optString("kahfTime", "09:00"), Calendar.FRIDAY);
        if (payload.optBoolean("mulkEnabled", true))
            scheduleOne(context, RC_MULK, "mulk", payload.optString("mulkTime", "20:30"), 0);
        if (payload.optBoolean("baqarahEnabled", false))
            scheduleOne(context, RC_BAQARAH, "baqarah",
                    payload.optString("baqarahTime", "09:00"), 0);
        if (payload.optBoolean("azkarMorningEnabled", false))
            scheduleOne(context, RC_AZKAR_MORNING, "azkarMorning",
                    payload.optString("azkarMorningTime", "07:00"), 0);
        if (payload.optBoolean("azkarEveningEnabled", false))
            scheduleOne(context, RC_AZKAR_EVENING, "azkarEvening",
                    payload.optString("azkarEveningTime", "18:00"), 0);
    }

    private static void scheduleOne(Context context, int requestCode, String type,
                                    String hhmm, int requiredDay) {
        try {
            String[] parts = hhmm.split(":");
            int hour = Math.max(0, Math.min(23, Integer.parseInt(parts[0])));
            int minute = Math.max(0, Math.min(59, Integer.parseInt(parts[1])));
            Calendar now = Calendar.getInstance();
            Calendar when = Calendar.getInstance();
            when.set(Calendar.HOUR_OF_DAY, hour);
            when.set(Calendar.MINUTE, minute);
            when.set(Calendar.SECOND, 0);
            when.set(Calendar.MILLISECOND, 0);
            if (requiredDay == 0) {
                if (!when.after(now)) when.add(Calendar.DAY_OF_YEAR, 1);
            } else {
                int delta = (requiredDay - when.get(Calendar.DAY_OF_WEEK) + 7) % 7;
                when.add(Calendar.DAY_OF_YEAR, delta);
                if (!when.after(now)) when.add(Calendar.DAY_OF_YEAR, 7);
            }

            Intent i = new Intent(context, ReadingReminderReceiver.class)
                    .setAction("com.wird.app.READING_REMINDER." + type)
                    .putExtra("type", type);
            PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when.getTimeInMillis(), pi);
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when.getTimeInMillis(), pi);
        } catch (Exception ignored) {}
    }

    public static void cancelAll(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        int[] codes = {RC_WIRD, RC_KAHF, RC_MULK, RC_BAQARAH,
                RC_AZKAR_MORNING, RC_AZKAR_EVENING};
        String[] types = {"wird", "kahf", "mulk", "baqarah",
                "azkarMorning", "azkarEvening"};
        for (int n = 0; n < codes.length; n++) {
            Intent i = new Intent(context, ReadingReminderReceiver.class)
                    .setAction("com.wird.app.READING_REMINDER." + types[n]);
            PendingIntent pi = PendingIntent.getBroadcast(context, codes[n], i,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi != null) am.cancel(pi);
        }
    }
}
