package com.wird.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

/** يعرض تذكيرات القراءة ويفتح الموضع المناسب عند الضغط عليها. */
public class ReadingReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "wird_reading_reminders";

    @Override public void onReceive(Context context, Intent intent) {
        String type = intent == null ? "wird" : intent.getStringExtra("type");
        if (type == null) type = "wird";
        if (!ReminderScheduler.isEnabled(context, type)) {
            ReminderScheduler.rescheduleSaved(context);
            return;
        }

        String title, text;
        int id;
        switch (type) {
            case "kahf":
                title = "تذكير الجمعة: سورة الكهف";
                text = "لا تنس قراءة سورة الكهف اليوم";
                id = 9401;
                break;
            case "mulk":
                title = "ورد المساء: سورة الملك";
                text = "حان وقت قراءة سورة الملك";
                id = 9402;
                break;
            case "baqarah":
                title = "تذكير قراءة سورة البقرة";
                text = "حان وقت قراءة سورة البقرة";
                id = 9403;
                break;
            case "azkarMorning":
                title = "أذكار الصباح";
                text = "ابدأ يومك بذكر الله — حان وقت أذكار الصباح";
                id = 9404;
                break;
            case "azkarEvening":
                title = "أذكار المساء";
                text = "اختم يومك بذكر الله — حان وقت أذكار المساء";
                id = 9405;
                break;
            default:
                title = "وردك اليومي";
                text = "دقائق مع القرآن… تابع وردك من حيث توقفت";
                id = 9400;
                type = "wird";
        }

        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL, "تذكيرات الورد والأذكار", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("الورد والقراءة وأذكار الصباح والمساء");
            nm.createNotificationChannel(channel);
        }

        Intent open = new Intent(context, MainActivity.class)
                .putExtra("open_target", type)
                .setData(android.net.Uri.parse("wird://reading/" + type))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        nm.notify(id, new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build());

        ReminderScheduler.rescheduleSaved(context);
    }
}
