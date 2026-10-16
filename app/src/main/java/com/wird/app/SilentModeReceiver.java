package com.wird.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** يعيد إعداد عدم الإزعاج السابق بعد انتهاء مدة الصامت المحددة. */
public class SilentModeReceiver extends BroadcastReceiver {
    public static final String ACTION_RESTORE = "com.wird.app.RESTORE_SILENT_MODE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || ACTION_RESTORE.equals(intent.getAction())) {
            SilentModeManager.restore(context);
        }
    }
}
