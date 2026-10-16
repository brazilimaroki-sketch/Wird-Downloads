package com.wird.app;

import java.util.Calendar;

/** Displayed wall-clock times, using the same device clock as prior builds. */
final class PrayerAlarmTime {
    static long nextTrigger(String hhmm, long nowMillis) {
        if (hhmm == null || !hhmm.matches("[0-9]{1,2}:[0-9]{2}")) {
            throw new IllegalArgumentException("Invalid prayer time");
        }
        String[] parts = hhmm.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        if (hour > 23 || minute > 59) throw new IllegalArgumentException("Invalid prayer time");
        Calendar time = Calendar.getInstance();
        time.setTimeInMillis(nowMillis);
        time.set(Calendar.HOUR_OF_DAY, hour);
        time.set(Calendar.MINUTE, minute);
        time.set(Calendar.SECOND, 0);
        time.set(Calendar.MILLISECOND, 0);
        if (time.getTimeInMillis() <= nowMillis) time.add(Calendar.DAY_OF_YEAR, 1);
        return time.getTimeInMillis();
    }

    private PrayerAlarmTime() {}
}
