# وِرد — قواعد ProGuard

# ─── وِرد — Proguard rules ─────────────────────────────────────────────────

# حافظ على WirdBridge ودوالها المعلّمة بـ @JavascriptInterface
-keepclassmembers class com.wird.app.MainActivity$WirdBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# حافظ على PrayerPrefs و PrayerScheduler (تُستخدم بالتسمية في BootReceiver)
-keep class com.wird.app.PrayerPrefs { *; }
-keep class com.wird.app.PrayerScheduler { *; }

# منع إزالة AdhanPlayerService
-keep class com.wird.app.AdhanPlayerService { *; }
