package com.wird.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            SilentModeManager.restore(context);
        } else if (!Intent.ACTION_TIME_CHANGED.equals(action)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                && !"android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(action)) {
            return;
        }
        // Android cancels alarms on reboot or after exact-alarm permission is revoked.
        // The grant broadcast restores them even if the user does not reopen the app.
        HabousMonthlySync.ensure(context);
        PrayerScheduler.rescheduleSaved(context, true);
        ReminderScheduler.rescheduleSaved(context);
        PrayerWidgetProvider.updateAll(context);
    }
}
