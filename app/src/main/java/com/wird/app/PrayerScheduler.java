package com.wird.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import org.json.JSONObject;

public final class PrayerScheduler {
    public static final String[] NAMES = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};
    private static final String STATE = "wird_alarm_schedule";

    private static SharedPreferences state(Context c) {
        return c.getSharedPreferences(STATE, Context.MODE_PRIVATE);
    }

    public static void scheduleFromJson(Context c, JSONObject root) {
        scheduleFromJson(c, root, false);
    }

    private static synchronized void scheduleFromJson(Context c, JSONObject root, boolean force) {
        int scheduled = 0;
        String error = "";
        try {
            if (!root.optBoolean("enabled", false)) { cancelAll(c); return; }
            JSONObject times = root.getJSONObject("times");
            JSONObject prayers = root.optJSONObject("prayers");
            String city = root.optString("city", "المغرب");
            boolean fullAdhan = !"notification".equals(root.optString("alertMode", "adhan"));
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            // Inexact alarms may be delayed and cannot reliably start the audio service
            // from the background on recent Android versions. Do not report them as ready.
            if (fullAdhan && Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                cancelAll(c);
                state(c).edit().putString("error", "فعّل إذن المنبّه الدقيق لتشغيل الأذان في وقته").apply();
                return;
            }
            for (int id = 0; id < NAMES.length; id++) {
                String name = NAMES[id];
                if (prayers != null && !prayers.optBoolean(name, true)) { cancelOne(c, id); continue; }
                try {
                    String time = times.optString(name, "");
                    org.json.JSONArray days = root.optJSONArray("days");
                    long at = days == null ? PrayerAlarmTime.nextTrigger(time, System.currentTimeMillis())
                            : NativePrayerEngine.next(days, name, System.currentTimeMillis());
                    if(days!=null) time=new java.text.SimpleDateFormat("HH:mm",java.util.Locale.US).format(new java.util.Date(at));
                    scheduleOne(c, am, id, name, time, city, force, at, days == null ? time : days.toString());
                    scheduleReminder(c,am,id,name,city,at,Math.max(0,Math.min(60,root.optInt("reminderMinutes",0))),force);
                    scheduled++;
                } catch (Exception e) {
                    cancelOne(c, id);
                    error = "تعذر جدولة بعض الصلوات؛ حدّث المواقيت وأعد فحص الأذونات";
                    Log.e("WirdAlarm", "Could not schedule " + name, e);
                }
            }
        } catch (Exception e) {
            error = "تعذر جدولة الأذان؛ حدّث مواقيت الصلاة";
            Log.e("WirdAlarm", "Could not schedule prayers", e);
        }
        state(c).edit().putInt("count", scheduled).putString("error", error).apply();
    }

    private static void scheduleOne(Context c, AlarmManager am, int id, String prayer,
                                    String time, String city, boolean force, long trigger, String fingerprint) {
        long now = System.currentTimeMillis();

        String key = prayer + "|" + city + "|" + fingerprint + "|"
                + (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms());
        SharedPreferences saved = state(c);
        long oldTrigger = saved.getLong("at_" + id, 0);
        // Repeated WebView syncs must not cancel an alarm while it is being delivered
        // at the start of its minute. The receiver clears this entry after delivery.
        if (!force && key.equals(saved.getString("key_" + id, ""))
                && oldTrigger > now - 60_000L) return;

        Intent i = new Intent(c, PrayerAlarmReceiver.class)
                .putExtra("id", id).putExtra("prayer", prayer)
                .putExtra("city", city).putExtra("hhmm", time).putExtra("scheduledAt", trigger);
        PendingIntent pi = PendingIntent.getBroadcast(c, 7000 + id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        setAlarm(c, am, trigger, pi);
        saved.edit().putString("key_" + id, key).putLong("at_" + id, trigger).apply();
    }

    private static void scheduleReminder(Context c,AlarmManager am,int id,String prayer,String city,long prayerAt,int minutes,boolean force) {
        long at=prayerAt-minutes*60_000L;
        if(minutes==0 || at<=System.currentTimeMillis()){cancelReminder(c,id);return;}
        if(!force && state(c).getLong("reminder_"+id,0)==at)return;
        Intent i=new Intent(c,PrayerAlarmReceiver.class).putExtra("prayer",prayer).putExtra("city",city)
                .putExtra("id",id).putExtra("reminder",true).putExtra("minutes",minutes);
        PendingIntent pi=PendingIntent.getBroadcast(c,7100+id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        setAlarm(c,am,at,pi);state(c).edit().putLong("reminder_"+id,at).apply();
    }
    private static void cancelReminder(Context c,int id) {
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi=PendingIntent.getBroadcast(c,7100+id,new Intent(c,PrayerAlarmReceiver.class),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
        if(pi!=null&&am!=null)am.cancel(pi);state(c).edit().remove("reminder_"+id).apply();
    }

    private static void setAlarm(Context c, AlarmManager am, long at, PendingIntent operation) {
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            // Notification-only mode may use an inexact reminder; full audio is gated above.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation);
            return;
        }
        PendingIntent show = PendingIntent.getActivity(c, 7098,
                new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), operation);
    }

    public static boolean scheduleTest(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null || (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())) return false;
        try {
            Intent i = new Intent(c, PrayerAlarmReceiver.class)
                    .putExtra("id", 99).putExtra("prayer", "test")
                    .putExtra("test", true).putExtra("city", "تجربة الأذان بالخلفية");
            PendingIntent pi = PendingIntent.getBroadcast(c, 7099, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            setAlarm(c, am, System.currentTimeMillis() + 60_000L, pi);
            return true;
        } catch (Exception e) {
            Log.e("WirdAlarm", "Could not schedule test", e);
            return false;
        }
    }

    /** Reject cancelled/replaced alarms, a different city, or an already-delivered timestamp. */
    public static boolean shouldDeliver(Context c,int id,String prayer,String city,long plannedAt) {
        JSONObject root=PrayerPrefs.load(c);
        if(root==null||!root.optBoolean("enabled",false)||id<0||id>=NAMES.length||!NAMES[id].equals(prayer)||!root.optString("city","").equals(city))return false;
        JSONObject enabled=root.optJSONObject("prayers");if(enabled!=null&&!enabled.optBoolean(prayer,true))return false;
        long current=state(c).getLong("at_"+id,0);
        return current>0&&(plannedAt==0||current==plannedAt);
    }

    public static int scheduledCount(Context c) { return state(c).getInt("count", 0); }
    public static String scheduleError(Context c) { return state(c).getString("error", ""); }
    public static void markDelivered(Context c, int id) {
        state(c).edit().remove("key_" + id).remove("at_" + id).apply();
    }
    public static void rescheduleSaved(Context c) { rescheduleSaved(c, false); }
    public static void rescheduleSaved(Context c, boolean force) {
        JSONObject obj = PrayerPrefs.load(c);
        if (obj != null) { obj=PrayerDayData.refresh(c,obj);PrayerPrefs.save(c,obj);scheduleFromJson(c, obj, force); }
    }
    private static void cancelOne(Context c, int id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(c, 7000 + id,
                new Intent(c, PrayerAlarmReceiver.class),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null && am != null) am.cancel(pi);
        markDelivered(c, id);
        cancelReminder(c,id);
    }
    public static void cancelAll(Context c) {
        for (int id = 0; id < NAMES.length; id++) cancelOne(c, id);
        state(c).edit().putInt("count", 0).putString("error", "").apply();
    }
}
