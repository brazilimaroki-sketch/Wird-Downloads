package com.wird.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** شاشة الأذان الكاملة. أي زر فعلي أو مغادرة للشاشة يوقف الأذان. */
public class AdhanActivity extends AppCompatActivity {

    private static volatile boolean screenVisible;

    public static boolean isScreenVisible() { return screenVisible; }

    @Override protected void onResume() {
        super.onResume();
        screenVisible = true;
        AdhanPlayerService.dismissPopup(this);
    }

    @Override protected void onPause() {
        screenVisible = false;
        super.onPause();
    }

    private boolean receiverRegistered;
    private boolean stopping;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                stopAdhanAndClose();
            } else if (AdhanPlayerService.ACTION_STATE_CHANGED.equals(intent.getAction())
                    && !intent.getBooleanExtra("active", false)) {
                finishAndRemoveTask();
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        getWindow().setStatusBarColor(Color.parseColor("#071C2A"));
        getWindow().setNavigationBarColor(Color.parseColor("#071C2A"));
        setContentView(R.layout.activity_adhan);
        bindIntent(getIntent());
        // Keep controls clear of system bars on edge-to-edge Android versions.
        android.view.View content = findViewById(R.id.adhanContent);
        int basePadding = Math.round(24 * getResources().getDisplayMetrics().density);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                    | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            view.setPadding(basePadding + bars.left, basePadding + bars.top,
                    basePadding + bars.right, basePadding + bars.bottom);
            return insets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(content);
        findViewById(R.id.adhanPhotoCredit).setOnClickListener(v ->
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("صورة مسجد الحسن الثاني")
                        .setMessage("نور رمضان فوق الموج — Jamal jalalll4\n"
                                + "Wikimedia Commons • CC BY-SA 4.0\n"
                                + "الصورة الأصلية محفوظة دون تعديل؛ يتم اقتصاص العرض وإضافة طبقة تظليل.\n"
                                + "https://commons.wikimedia.org/wiki/File:نور_رمضان_فوق_الموج.jpg\n"
                                + "https://creativecommons.org/licenses/by-sa/4.0/")
                        .setPositiveButton("إغلاق", null).show());
        Button stop = findViewById(R.id.adhanStopButton);
        stop.setOnClickListener(v -> stopAdhanAndClose());
        registerStateReceiver();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        bindIntent(intent);
    }

    private void bindIntent(Intent intent) {
        String prayer = intent == null ? null : intent.getStringExtra("prayer");
        String city = intent == null ? null : intent.getStringExtra("city");
        TextView prayerView = findViewById(R.id.adhanPrayerName);
        TextView cityView = findViewById(R.id.adhanCity);
        boolean test = "test".equals(prayer);
        TextView statusView = findViewById(R.id.adhanStatusTitle);
        statusView.setText(test ? "تجربة الأذان" : "حان وقت الصلاة");
        prayerView.setText(test ? "الأذان" : arabicPrayerName(prayer));
        if (city == null || city.trim().isEmpty()) {
            org.json.JSONObject prefs = PrayerPrefs.load(this);
            if (prefs != null) city = prefs.optString("city", "");
        }
        if (city == null || city.trim().isEmpty()) cityView.setText("وِرد");
        else cityView.setText(city);
    }

    private void registerStateReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(AdhanPlayerService.ACTION_STATE_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else registerReceiver(stateReceiver, filter);
        receiverRegistered = true;
    }

    private void stopAdhanAndClose() {
        if (stopping) return;
        stopping = true;
        AdhanPlayerService.requestStop(this);
        finishAndRemoveTask();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
            stopAdhanAndClose();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public void onBackPressed() { stopAdhanAndClose(); }

    @Override protected void onUserLeaveHint() {
        stopAdhanAndClose();
        super.onUserLeaveHint();
    }

    @Override protected void onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(stateReceiver); } catch (Exception ignored) {}
            receiverRegistered = false;
        }
        super.onDestroy();
    }

    private String arabicPrayerName(String key) {
        if (key == null) return "الصلاة";
        switch (key) {
            case "Fajr": return "الفجر";
            case "Dhuhr": return "الظهر";
            case "Asr": return "العصر";
            case "Maghrib": return "المغرب";
            case "Isha": return "العشاء";
            case "test": return "الصلاة";
            default: return key;
        }
    }
}
