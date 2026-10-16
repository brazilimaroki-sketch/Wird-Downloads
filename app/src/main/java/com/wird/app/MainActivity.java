package com.wird.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private int pendingPrayerImportCity;
    private boolean keepReadingScreenOn;
    private boolean activityResumed;
    private boolean readerFullscreen = false;
    private boolean webAppReady = false;
    private String pendingLaunchTarget;
    private static final int REQ_NOTIFICATIONS = 9001;
    private static final int REQ_MICROPHONE = 9002;
    private PermissionRequest pendingAudioPermissionRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        splashScreen.setKeepOnScreenCondition(() -> !webAppReady);
        splashScreen.setOnExitAnimationListener(provider ->
                provider.getView().animate()
                        .alpha(0f)
                        .setDuration(180L)
                        .withEndAction(provider::remove)
                        .start());

        android.content.SharedPreferences appearance =
                getSharedPreferences("wird_appearance", MODE_PRIVATE);
        String savedTheme = appearance.getString("app_theme", "blue");
        // Builds prior to this startup fix could leave the native preference on
        // green while the WebView was already using blue. Migrate that stale
        // value once; the WebView bridge keeps later user choices in sync.
        if (!appearance.getBoolean("launch_theme_synced_v2", false)) {
            savedTheme = "blue";
            appearance.edit()
                    .putString("app_theme", savedTheme)
                    .putBoolean("launch_theme_synced_v2", true)
                    .apply();
        }
        int launchBg = launchBackground(savedTheme);
        getWindow().setBackgroundDrawable(new ColorDrawable(launchBg));
        getWindow().setStatusBarColor(themeDark(savedTheme));
        getWindow().setNavigationBarColor(themeDark(savedTheme));
        super.onCreate(savedInstanceState);
        if(savedInstanceState!=null)pendingPrayerImportCity=savedInstanceState.getInt("prayerImportCity",0);
        setContentView(R.layout.activity_main);
        HabousMonthlySync.ensure(this);
        installSystemBarInsets();
        applyAppSystemBars();

        webView = findViewById(R.id.webView);
        captureLaunchTarget(getIntent());
        webView.setBackgroundColor(launchBg);

        // WebViewAssetLoader: يحمّل ملفات assets عبر HTTPS آمن
        // بدلاً من file:// — يتفادى مخاطر Universal File Access
        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLoadsImagesAutomatically(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        // مغلقان: مع addJavascriptInterface فتحهما يشكّل ثغرة UXSS
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        // NEVER_ALLOW: التطبيق يعمل كله على HTTPS (appassets + api.aladhan)
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        // احترم حجم الخط الذي اختاره المستخدم من إعدادات Android.
        // وضع حدين يمنع انهيار الواجهة مع إبقاء النص قابلاً للتكبير.
        int textZoom = Math.max(85, Math.min(150,
                Math.round(getResources().getConfiguration().fontScale * 100f)));
        s.setTextZoom(textZoom);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> handleWebPermissionRequest(request));
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingAudioPermissionRequest == request) {
                    pendingAudioPermissionRequest = null;
                }
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(
                    WebView view, android.webkit.WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view, android.webkit.WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("https".equalsIgnoreCase(u.getScheme())
                        && "appassets.androidplatform.net".equalsIgnoreCase(u.getHost())) {
                    return false; // روابط داخلية — يفتحها WebView
                }
                startActivity(new Intent(Intent.ACTION_VIEW, u)); // روابط خارجية → المتصفح
                return true;
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                // Do not reveal here: the first WebView frame can still be an
                // empty/default-colour frame while the large offline document
                // is being parsed.
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Do not reveal the WebView merely because the document has
                // loaded: the home boot runs just after DOMContentLoaded and
                // an older page could otherwise flash for one frame.
                if (webAppReady) dispatchPendingLaunchTarget();
            }
        });

        webView.addJavascriptInterface(new WirdBridge(this), "WirdNative");
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                webView.evaluateJavascript(
                        "(function(){try{" +
                        "if(typeof appBackV68==='function')return appBackV68()?'handled':'none';" +
                        "if(typeof closeReader==='function'" +
                        "&&!document.getElementById('readerView')?.classList.contains('hidden'))" +
                        "{closeReader();return 'handled'}" +
                        "}catch(e){} return 'none'})()",
                        value -> {
                            if (!"\"handled\"".equals(value)) {
                                if (webView.canGoBack()) webView.goBack();
                                else finish();
                            }
                        });
            }
        });
    }

    private void revealWebApp() {
        if (webView == null) return;
        webView.setVisibility(View.VISIBLE);
    }

    /** يمنح WebView التقاط الصوت فقط للمحتوى المحلي الموثوق الخاص بالتطبيق. */
    private void handleWebPermissionRequest(PermissionRequest request) {
        if (request == null) return;
        Uri origin = request.getOrigin();
        boolean trustedOrigin = origin != null
                && "https".equalsIgnoreCase(origin.getScheme())
                && "appassets.androidplatform.net".equalsIgnoreCase(origin.getHost());
        boolean asksForAudio = false;
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                asksForAudio = true;
                break;
            }
        }
        if (!trustedOrigin || !asksForAudio) {
            request.deny();
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            return;
        }
        pendingAudioPermissionRequest = request;
        requestMicrophonePermission();
    }

    private void requestMicrophonePermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            notifyMicrophonePermissionResult(true);
            return;
        }
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MICROPHONE);
    }

    private void notifyMicrophonePermissionResult(boolean granted) {
        if (webView == null) return;
        webView.evaluateJavascript(
                "if(window.onNativeMicrophonePermissionResult){" +
                        "window.onNativeMicrophonePermissionResult(" + granted + ");}", null);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS) {
            refreshPrayerPermissionStatus();
            return;
        }
        if (requestCode != REQ_MICROPHONE) return;
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        PermissionRequest webRequest = pendingAudioPermissionRequest;
        pendingAudioPermissionRequest = null;
        if (webRequest != null) {
            if (granted) {
                webRequest.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            } else {
                webRequest.deny();
            }
        }
        notifyMicrophonePermissionResult(granted);
    }

    @Override protected void onSaveInstanceState(Bundle out){out.putInt("prayerImportCity",pendingPrayerImportCity);super.onSaveInstanceState(out);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=PrayerImportController.REQUEST)return;
        int city=pendingPrayerImportCity;pendingPrayerImportCity=0;
        if(resultCode==RESULT_OK&&data!=null&&data.getData()!=null&&city>0)PrayerImportController.handle(this,data.getData(),city);
    }
    void prayerToolsChanged(String message){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;
        if(message!=null&&!message.isEmpty())Toast.makeText(this,message,Toast.LENGTH_LONG).show();
        if(webView!=null)webView.evaluateJavascript("window.onPrayerToolsChangedV7581?.()",null);
    });}

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        if (webView != null) webView.setKeepScreenOn(keepReadingScreenOn);
        applyAppSystemBars();
        PrayerScheduler.rescheduleSaved(this);
        refreshPrayerPermissionStatus();
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        if (webView != null) webView.setKeepScreenOn(false);
        super.onPause();
    }

    private void refreshPrayerPermissionStatus() {
        if (webView == null || !webAppReady) return;
        webView.evaluateJavascript(
                "if(window.refreshNativeStatusV71){window.refreshNativeStatusV71();}", null);
    }

    private static int launchBackground(String id) {
        switch (id == null ? "blue" : id) {
            case "green": return Color.parseColor("#F4F1E8");
            case "gold": return Color.parseColor("#F8F3E7");
            case "purple": return Color.parseColor("#F5F1F9");
            case "burgundy": return Color.parseColor("#F8F1F2");
            case "teal": return Color.parseColor("#EEF7F6");
            default: return Color.parseColor("#F1F5FA");
        }
    }

    private static int themePrimary(String id) {
        switch (id == null ? "blue" : id) {
            case "green": return Color.parseColor("#0C6B59");
            case "gold": return Color.parseColor("#9A731D");
            case "purple": return Color.parseColor("#7253A3");
            case "burgundy": return Color.parseColor("#8B3545");
            case "teal": return Color.parseColor("#11878A");
            default: return Color.parseColor("#245F9E");
        }
    }

    private static int themeDark(String id) {
        switch (id == null ? "blue" : id) {
            case "green": return Color.parseColor("#075244");
            case "gold": return Color.parseColor("#6D4F0F");
            case "purple": return Color.parseColor("#4D3478");
            case "burgundy": return Color.parseColor("#5D2030");
            case "teal": return Color.parseColor("#075F67");
            default: return Color.parseColor("#163F73");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        captureLaunchTarget(intent);
        dispatchPendingLaunchTarget();
    }

    private void captureLaunchTarget(Intent intent) {
        if (intent == null) return;
        String target = intent.getStringExtra("open_target");
        if (target == null) return;
        pendingLaunchTarget = target;
        intent.removeExtra("open_target");
    }

    private void dispatchPendingLaunchTarget() {
        if (webView == null || !webAppReady || pendingLaunchTarget == null) return;
        String target = pendingLaunchTarget;
        String js;
        switch (target) {
            case "kahf":
                js = "if(typeof openSurah==='function'){openSurah(18,1);return 'handled'}";
                break;
            case "mulk":
                js = "if(typeof openSurah==='function'){openSurah(67,1);return 'handled'}";
                break;
            case "baqarah":
                js = "if(typeof openSurah==='function'){openSurah(2,1);return 'handled'}";
                break;
            case "azkarMorning":
                js = "if(typeof v60OpenAzkar==='function'){v60OpenAzkar('الصباح');return 'handled'}";
                break;
            case "azkarEvening":
                js = "if(typeof v60OpenAzkar==='function'){v60OpenAzkar('المساء');return 'handled'}";
                break;
            case "prayer":
                js = "if(typeof showPrayer==='function'){showPrayer(document.querySelector('.prayerNavBtn'));return 'handled'}";
                break;
            default:
                js = "if(typeof openLast==='function'){openLast();return 'handled'}";
        }
        webView.evaluateJavascript(
                "(function(){try{" + js + "}catch(e){}return 'pending'})()",
                result -> {
                    if ("\"handled\"".equals(result) && target.equals(pendingLaunchTarget)) {
                        pendingLaunchTarget = null;
                    }
                });
    }

    private void onWebAppReady() {
        revealWebApp();
        webAppReady = true;
        dispatchPendingLaunchTarget();
    }

    /** أول ضغطة داخل التطبيق توقف الأذان ثم تواصل وظيفتها العادية. */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event != null && event.getActionMasked() == MotionEvent.ACTION_DOWN
                && AdhanPlayerService.isActive()) {
            AdhanPlayerService.requestStop(this);
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null && event.getAction() == KeyEvent.ACTION_DOWN
                && AdhanPlayerService.isActive()) {
            AdhanPlayerService.requestStop(this);
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    private void installSystemBarInsets() {
        View surface = findViewById(R.id.appSurface);
        if (Build.VERSION.SDK_INT >= 35) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(surface, (view, insets) -> {
                androidx.core.graphics.Insets bars = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                // The native container owns the insets; the WebView sees the usable area.
                view.setPadding(bars.left, readerFullscreen ? 0 : bars.top,
                        bars.right, Math.max(readerFullscreen ? 0 : bars.bottom,
                                insets.getInsets(WindowInsetsCompat.Type.ime()).bottom));
                return WindowInsetsCompat.CONSUMED;
            });
            androidx.core.view.ViewCompat.requestApplyInsets(surface);
        }
    }

    private void applyAppSystemBars() {
        String id = getSharedPreferences("wird_appearance", Context.MODE_PRIVATE)
                .getString("app_theme", "blue");
        int color = themeDark(id);
        getWindow().setStatusBarColor(color);
        getWindow().setNavigationBarColor(color);
        View surface = findViewById(R.id.appSurface);
        if (surface != null) surface.setBackgroundColor(color);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setStatusBarContrastEnforced(false);
        }
        applySystemBarContrast();
    }

    private void applySystemBarContrast() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
    }

    private void applyReaderFullscreen(boolean enabled) {
        readerFullscreen = enabled;
        applySystemBarContrast();
        WindowCompat.setDecorFitsSystemWindows(getWindow(), Build.VERSION.SDK_INT < 35 && !enabled);
        View surface = findViewById(R.id.appSurface);
        if (surface != null) androidx.core.view.ViewCompat.requestApplyInsets(surface);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        if (enabled) {
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars());
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyAppSystemBars();
            if (readerFullscreen) applyReaderFullscreen(true);
        }
    }

    // ─────────────────────────────────── JS Bridge ───────────────────────────
    // static + WeakReference: يمنع تسرب MainActivity من JS thread

    public static final class WirdBridge {
        private final WeakReference<MainActivity> ref;

        WirdBridge(MainActivity activity) {
            this.ref = new WeakReference<>(activity);
        }

        private MainActivity get() { return ref.get(); }

        /** Keep the display awake only while reading Quran or adhkar in this Activity. */
        @JavascriptInterface
        public void setReadingScreenOn(boolean enabled) {
            MainActivity a = get(); if (a == null) return;
            a.runOnUiThread(() -> {
                a.keepReadingScreenOn = enabled;
                if (a.webView != null) a.webView.setKeepScreenOn(enabled && a.activityResumed);
            });
        }

        /** يحفظ ثيم الواجهة في Android حتى تكون شاشة الإقلاع بنفس اللون المختار. */
        @JavascriptInterface
        public void saveAppTheme(String requestedId) {
            MainActivity a = get(); if (a == null) return;
            String id;
            if ("green".equals(requestedId) || "blue".equals(requestedId)
                    || "gold".equals(requestedId) || "purple".equals(requestedId)
                    || "burgundy".equals(requestedId) || "teal".equals(requestedId)) {
                id = requestedId;
            } else {
                id = "blue";
            }
            android.content.SharedPreferences appearance = a.getSharedPreferences(
                    "wird_appearance", Context.MODE_PRIVATE);
            String previousId = appearance.getString("app_theme", "blue");
            boolean changed = !id.equals(previousId);
            if (changed) appearance.edit().putString("app_theme", id).apply();
            a.runOnUiThread(() -> {
                a.applyAppSystemBars();
            });
            // RemoteViews work can be expensive on older launchers.  Never run it on
            // the activity UI thread, and do not refresh widgets during every boot
            // when the selected theme did not actually change.
            if (changed) {
                Context appContext = a.getApplicationContext();
                new Thread(() -> PrayerWidgetProvider.updateAll(appContext),
                        "wird-widget-theme").start();
            }
        }

        /** يكشف الواجهة فقط بعد اكتمال DOM وأول رسم، لتفادي الشاشة السوداء/الخضراء. */
        @JavascriptInterface
        public void appReady() {
            MainActivity a = get(); if (a == null) return;
            a.runOnUiThread(a::onWebAppReady);
        }

        /** يملأ قارئ المصحف الشاشة عند إخفاء أدوات القراءة. */
        @JavascriptInterface
        public void setReaderFullscreen(boolean enabled) {
            MainActivity a = get(); if (a == null) return;
            a.runOnUiThread(() -> a.applyReaderFullscreen(enabled));
        }

        /** حالة إذن الميكروفون قبل بدء التعرف الصوتي. */
        @JavascriptInterface
        public boolean hasMicrophonePermission() {
            MainActivity a = get();
            return a != null && ActivityCompat.checkSelfPermission(
                    a, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        }

        /** يفتح نافذة أندرويد الرسمية لطلب إذن الميكروفون. */
        @JavascriptInterface
        public void requestMicrophonePermission() {
            MainActivity a = get(); if (a == null) return;
            a.runOnUiThread(a::requestMicrophonePermission);
        }

        @JavascriptInterface
        public void syncPrayerTimes(String json) {
            MainActivity a = get(); if (a == null) return;
            try {
                JSONObject obj = PrayerDayData.refresh(a, new JSONObject(json));
                PrayerPrefs.save(a, obj);
                PrayerScheduler.scheduleFromJson(a, obj);
                PrayerWidgetProvider.updateAll(a);
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public String calculatePrayerDays(double lat, double lon, String method, String zone, boolean hanafi) {
            try { return NativePrayerEngine.days(lat,lon,method,zone,hanafi,System.currentTimeMillis()).toString(); }
            catch(Exception e) { return "[]"; }
        }

        @JavascriptInterface
        public String cachedOfficialPrayerTimes(int cityId) {
            MainActivity a=get();if(a==null)return "null";
            try{JSONObject d=PrayerSources.daily(a,cityId);return d==null?"null":d.toString();}catch(Exception e){return "null";}
        }
        @JavascriptInterface
        public String prayerMonthStatus(int cityId) {MainActivity a=get();return a==null?"{}":HabousMonthlySync.status(a,cityId).toString();}
        @JavascriptInterface
        public void refreshPrayerMonths() {MainActivity a=get();if(a!=null)HabousMonthlySync.request(a,true);}

        @JavascriptInterface public String preparePrayerView(String json){MainActivity a=get();if(a==null)return "{}";try{
            JSONObject v=new JSONObject(json);org.json.JSONArray days=v.getJSONArray("days");int cityId=v.optInt("cityId");
            if("21".equals(v.optString("method")))days=PrayerSources.overlay(a,cityId,days);
            JSONObject correction=PrayerCorrections.get(a,cityId,v.optString("city"));
            return new JSONObject().put("days",PrayerCorrections.applyDays(days,correction)).put("corrections",correction).toString();
        }catch(Exception e){try{return new JSONObject().put("error",e.getMessage()).toString();}catch(Exception ignored){return "{}";}}}
        @JavascriptInterface public String prayerToolsState(int cityId,String label){MainActivity a=get();if(a==null)return "{}";try{
            JSONObject d=PrayerDiagnostics.snapshot(a);d.put("offsets",PrayerCorrections.get(a,cityId,label));
            JSONObject table=ImportedPrayerStore.load(a,cityId);d.put("importActive",ImportedPrayerStore.active(a,cityId)).put("importFirst",table.optString("firstDate")).put("importLast",table.optString("lastDate"));return d.toString();
        }catch(Exception e){return "{}";}}
        @JavascriptInterface public String setPrayerCorrections(int cityId,String label,String json){MainActivity a=get();if(a==null)return "تعذر فتح الإعدادات";try{
            JSONObject root=PrayerPrefs.load(a);if(root==null||!PrayerCorrections.key(cityId,label).equals(PrayerCorrections.key(root.optInt("cityId"),root.optString("city"))))return "حدّث المواقيت ثم أعد المحاولة";
            PrayerCorrections.save(a,cityId,label,new JSONObject(json),PrayerDayData.baseDays(a,root));PrayerScheduler.rescheduleSaved(a,true);PrayerWidgetProvider.updateAll(a);return "";
        }catch(Exception e){return e.getMessage()==null?"تعذر حفظ التصحيح":e.getMessage();}}
        @JavascriptInterface public void importPrayerTable(int cityId){MainActivity a=get();if(a==null)return;a.runOnUiThread(()->{try{
            HabousCityCatalog.name(a,cityId);if(a.pendingPrayerImportCity!=0)return;a.pendingPrayerImportCity=cityId;PrayerImportController.choose(a,cityId);
        }catch(Exception e){a.pendingPrayerImportCity=0;a.prayerToolsChanged("الاستيراد متاح للمدن المرتبطة بفهرس الوزارة");}});}
        @JavascriptInterface public void clearImportedPrayerTable(int cityId){MainActivity a=get();if(a==null)return;ImportedPrayerStore.clear(a,cityId);PrayerImportController.refresh(a);a.prayerToolsChanged("تمت العودة للمصدر التلقائي");}
        @JavascriptInterface public void openPrayerAppSettings(){MainActivity a=get();if(a!=null)a.runOnUiThread(()->a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+a.getPackageName()))));}
        @JavascriptInterface public void copyPrayerDiagnostics(){MainActivity a=get();if(a==null)return;try{String report=PrayerDiagnostics.snapshot(a).toString(2)+"\n"+getNativeStatus();
            a.runOnUiThread(()->{android.content.ClipboardManager cb=(android.content.ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE);if(cb!=null)cb.setPrimaryClip(android.content.ClipData.newPlainText("تشخيص أذان وِرد",report));Toast.makeText(a,"تم نسخ التشخيص",Toast.LENGTH_SHORT).show();});
        }catch(Exception ignored){}}

        /** يجلب الحصة اليومية الرسمية للمغرب ثم يعيدها للواجهة بشكل غير متزامن. */
        @JavascriptInterface
        public void requestOfficialPrayerTimes(String cityName, int cityId) { requestMonth(cityName,cityId,false); }
        @JavascriptInterface
        public void requestOfficialPrayerMonth(String cityName,int cityId,boolean force) {requestMonth(cityName,cityId,force);}
        private void requestMonth(String cityName,int cityId,boolean force) {
            MainActivity a = get(); if (a == null) return;
            HabousPrayerClient.request(a, cityName, cityId, force, result -> {
                MainActivity current = get();
                if (current == null || current.webView == null) return;
                String quoted = JSONObject.quote(result.toString());
                current.runOnUiThread(() -> {
                    if (current.webView != null) {
                        current.webView.evaluateJavascript(
                                "if(window.onOfficialPrayerTimesV75X){" +
                                        "window.onOfficialPrayerTimesV75X(" + quoted + ");}", null);
                    }
                });
            });
        }

        /** يحفظ ويجدول تذكيرات الورد والكهف والملك. */
        @JavascriptInterface
        public void syncReadingReminders(String json) {
            MainActivity a = get(); if (a == null) return;
            try { ReminderScheduler.saveAndSchedule(a, new JSONObject(json)); }
            catch (Exception ignored) {}
        }

        /** يطلب من المشغل إضافة ويدجت وِرد إلى الشاشة الرئيسية. */
        @JavascriptInterface
        public void requestPinPrayerWidget() {
            MainActivity a = get(); if (a == null) return;
            a.runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 26) {
                    AppWidgetManager manager = AppWidgetManager.getInstance(a);
                    if (manager.isRequestPinAppWidgetSupported()) {
                        String[] labels = {a.getString(R.string.widget_small),
                                a.getString(R.string.widget_medium), a.getString(R.string.widget_large)};
                        Class<?>[] types = {PrayerWidgetSmallProvider.class,
                                PrayerWidgetProvider.class, PrayerWidgetLargeProvider.class};
                        new androidx.appcompat.app.AlertDialog.Builder(a)
                                .setTitle("اختر حجم الويدجت")
                                .setItems(labels, (dialog, which) -> manager.requestPinAppWidget(
                                        new ComponentName(a, types[which]), null, null))
                                .setNegativeButton("إلغاء", null).show();
                        return;
                    }
                }
                Toast.makeText(a, "اضغط مطولاً على الشاشة الرئيسية ثم اختر ويدجت وِرد",
                        Toast.LENGTH_LONG).show();
            });
        }

        @JavascriptInterface
        public void requestPinPrayerWidgetSize(String size) {
            MainActivity a = get(); if (a == null) return;
            Class<?> type = "small".equals(size) ? PrayerWidgetSmallProvider.class
                    : "large".equals(size) ? PrayerWidgetLargeProvider.class : PrayerWidgetProvider.class;
            a.runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 26) {
                    AppWidgetManager manager = AppWidgetManager.getInstance(a);
                    if (manager.isRequestPinAppWidgetSupported()) {
                        manager.requestPinAppWidget(new ComponentName(a, type), null, null);
                        return;
                    }
                }
                Toast.makeText(a, "اضغط مطولاً على الشاشة الرئيسية ثم اختر ويدجت وِرد",
                        Toast.LENGTH_LONG).show();
            });
        }

        @JavascriptInterface
        public void cancelPrayerAlarms() {
            MainActivity a = get(); if (a == null) return;
            PrayerScheduler.cancelAll(a);
            PrayerPrefs.clear(a);
            SilentModeManager.restore(a);
            PrayerWidgetProvider.updateAll(a);
        }

        @JavascriptInterface
        public String getNativeStatus() {
            MainActivity a = get();
            if (a == null) return "{\"exact\":false,\"notifications\":false}";
            try {
                JSONObject o = new JSONObject();
                AlarmManager am = (AlarmManager) a.getSystemService(ALARM_SERVICE);
                boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
                NotificationManager nm = (NotificationManager) a.getSystemService(NOTIFICATION_SERVICE);
                boolean notif = Build.VERSION.SDK_INT < 33 ||
                        ActivityCompat.checkSelfPermission(a, Manifest.permission.POST_NOTIFICATIONS)
                                == PackageManager.PERMISSION_GRANTED;
                o.put("exact", exact);
                o.put("notifications", notif && nm.areNotificationsEnabled());
                if (Build.VERSION.SDK_INT >= 34) {
                    o.put("fullScreenAdhan", nm.canUseFullScreenIntent());
                } else o.put("fullScreenAdhan", true);
                o.put("dnd", nm.isNotificationPolicyAccessGranted());
                o.put("silentActive", SilentModeManager.isActive(a));
                o.put("adhanActive", AdhanPlayerService.isActive());
                JSONObject saved = PrayerPrefs.load(a);
                boolean scheduled = saved != null && saved.optBoolean("enabled", false)
                        && PrayerScheduler.scheduledCount(a) > 0
                        && (exact || "notification".equals(PrayerPrefs.alertMode(a)));
                o.put("scheduledCount", PrayerScheduler.scheduledCount(a));
                o.put("scheduleError", PrayerScheduler.scheduleError(a));
                o.put("playbackError", PrayerPrefs.playbackError(a));
                android.media.AudioManager audio = (android.media.AudioManager) a.getSystemService(AUDIO_SERVICE);
                o.put("alarmVolume", audio.getStreamVolume(android.media.AudioManager.STREAM_ALARM));
                android.os.PowerManager power=(android.os.PowerManager)a.getSystemService(Context.POWER_SERVICE);
                o.put("batteryUnrestricted",power!=null&&power.isIgnoringBatteryOptimizations(a.getPackageName()));
                o.put("scheduled", scheduled);
                if (saved != null) {
                    o.put("city", saved.optString("city", ""));
                    JSONObject times = saved.optJSONObject("times");
                    if (scheduled && times != null) {
                        String[] keys = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};
                        String[] labels = {"الفجر", "الظهر", "العصر", "المغرب", "العشاء"};
                        java.util.Calendar now = java.util.Calendar.getInstance();
                        int nowMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60
                                + now.get(java.util.Calendar.MINUTE);
                        int bestMinutes = Integer.MAX_VALUE;
                        int bestIndex = -1;
                        for (int i = 0; i < keys.length; i++) {
                            if (!PrayerPrefs.isPrayerEnabled(a, keys[i])) continue;
                            String raw = times.optString(keys[i], "");
                            String[] parts = raw.split(":");
                            if (parts.length < 2) continue;
                            try {
                                int candidate = Integer.parseInt(parts[0]) * 60
                                        + Integer.parseInt(parts[1].replaceAll("[^0-9].*$", ""));
                                if (candidate <= nowMinutes) candidate += 24 * 60;
                                if (candidate < bestMinutes) {
                                    bestMinutes = candidate;
                                    bestIndex = i;
                                }
                            } catch (Exception ignored) {}
                        }
                        if (bestIndex >= 0) {
                            o.put("nextPrayer", labels[bestIndex]);
                            o.put("nextTime", times.optString(keys[bestIndex], ""));
                        }
                    }
                }
                if(saved!=null && saved.optJSONArray("days")!=null && scheduled) {
                    long soonest=Long.MAX_VALUE;String nextName="";
                    for(String key:PrayerScheduler.NAMES) {
                        if(!PrayerPrefs.isPrayerEnabled(a,key))continue;
                        try{long at=NativePrayerEngine.next(saved.getJSONArray("days"),key,System.currentTimeMillis());
                            if(at<soonest){soonest=at;nextName=key;}}
                        catch(Exception ignored){}
                    }
                    if(soonest!=Long.MAX_VALUE){
                        String[] labels={"الفجر","الظهر","العصر","المغرب","العشاء"};
                        for(int i=0;i<PrayerScheduler.NAMES.length;i++)if(PrayerScheduler.NAMES[i].equals(nextName))o.put("nextPrayer",labels[i]);
                        o.put("nextTime",new java.text.SimpleDateFormat("HH:mm",java.util.Locale.US).format(new java.util.Date(soonest)));
                    }
                }
                return o.toString();
            } catch (Exception e) {
                return "{\"exact\":false,\"notifications\":false}";
            }
        }

        @JavascriptInterface
        public void requestAlarmPermissions() {
            MainActivity a = get(); if (a == null) return;
            a.runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 &&
                        ActivityCompat.checkSelfPermission(a, Manifest.permission.POST_NOTIFICATIONS)
                                != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(a,
                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                            REQ_NOTIFICATIONS);
                    return;
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    AlarmManager am = (AlarmManager) a.getSystemService(ALARM_SERVICE);
                    if (!am.canScheduleExactAlarms()) {
                        a.startActivity(new Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:" + a.getPackageName())));
                        return;
                    }
                }
                if (Build.VERSION.SDK_INT >= 34) {
                    NotificationManager nm =
                            (NotificationManager) a.getSystemService(NOTIFICATION_SERVICE);
                    if (!nm.canUseFullScreenIntent()) {
                        a.startActivity(new Intent(
                                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.parse("package:" + a.getPackageName())));
                        return;
                    }
                }
                a.refreshPrayerPermissionStatus();
            });
        }

        /** يفتح شاشة السماح بالتحكم في وضع عدم الإزعاج/الصامت. */
        @JavascriptInterface
        public void requestDndAccess() {
            MainActivity a = get(); if (a == null) return;
            a.runOnUiThread(() -> {
                NotificationManager nm =
                        (NotificationManager) a.getSystemService(NOTIFICATION_SERVICE);
                if (!nm.isNotificationPolicyAccessGranted()) {
                    a.startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                }
            });
        }

        /** إلغاء الصامت المؤقت فوراً وإرجاع حالة النظام السابقة. */
        @JavascriptInterface
        public void restoreSilentMode() {
            MainActivity a = get(); if (a == null) return;
            SilentModeManager.restore(a);
        }

        @JavascriptInterface
        public void openQibla(double lat, double lon) {
            MainActivity a = get(); if (a == null) return;
            Intent i = new Intent(a, QiblaActivity.class);
            i.putExtra("lat", lat);
            i.putExtra("lon", lon);
            a.startActivity(i);
        }

        /** تشغيل صوت الأذان يدوياً (زر «تجربة الأذان» في الواجهة) */
        @JavascriptInterface
        public void testAdhan() {
            MainActivity a = get(); if (a == null) return;
            Intent i = new Intent(a, AdhanPlayerService.class)
                    .setAction(AdhanPlayerService.ACTION_PLAY)
                    .putExtra("prayer", "test");
            if (Build.VERSION.SDK_INT >= 26) a.startForegroundService(i);
            else a.startService(i);
            a.startActivity(new Intent(a, AdhanActivity.class)
                    .putExtra("prayer", "test")
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }

        @JavascriptInterface
        public String getAdhanVoices() { return AdhanCatalog.json(); }

        /** Preview only: does not alter the saved voice or the prayer schedule. */
        @JavascriptInterface
        public void previewAdhan(String voice) {
            MainActivity a = get(); if (a == null) return;
            a.runOnUiThread(() -> {
                Intent play = new Intent(a, AdhanPlayerService.class)
                        .setAction(AdhanPlayerService.ACTION_PLAY)
                        .putExtra("prayer", "test").putExtra("voice", voice)
                        .putExtra("city", AdhanCatalog.name(voice));
                if (Build.VERSION.SDK_INT >= 26) a.startForegroundService(play);
                else a.startService(play);
                a.startActivity(new Intent(a, AdhanActivity.class)
                        .putExtra("prayer", "test").putExtra("city", AdhanCatalog.name(voice))
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            });
        }

        /** Tests the same alarm receiver/service path after the app is backgrounded. */
        @JavascriptInterface
        public boolean scheduleAdhanTest() {
            MainActivity a = get();
            return a != null && PrayerScheduler.scheduleTest(a);
        }

        /** إيقاف الأذان الجاري */
        @JavascriptInterface
        public void stopAdhan() {
            MainActivity a = get(); if (a == null) return;
            AdhanPlayerService.requestStop(a);
        }

        @JavascriptInterface
        public String getAppVersion() {
            MainActivity a = get(); if (a == null) return "{}";
            try {
                android.content.pm.PackageInfo p = a.getPackageManager()
                        .getPackageInfo(a.getPackageName(), 0);
                JSONObject o = new JSONObject();
                o.put("versionName", p.versionName == null ? "" : p.versionName);
                if (Build.VERSION.SDK_INT >= 28) o.put("versionCode", p.getLongVersionCode());
                else o.put("versionCode", p.versionCode);
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface
        public void checkAppUpdate(boolean force) {
            MainActivity a = get(); if (a == null) return;
            AppUpdateChecker.check(a.getApplicationContext(), force, notice -> {
                MainActivity active = get(); if (active == null) return;
                active.runOnUiThread(() -> {
                    if (active.webView != null && !active.isFinishing() && !active.isDestroyed())
                        active.webView.evaluateJavascript("if(window.onAppUpdateAvailableV7574)window.onAppUpdateAvailableV7574(" + notice.toString() + ");", null);
                });
            });
        }

        @JavascriptInterface
        public void openUpdateWebsite() {
            MainActivity a = get(); if (a == null) return;
            try {
                a.startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://wird-apk.pages.dev/")));
            } catch (Exception e) {
                a.runOnUiThread(() -> Toast.makeText(a,
                        "تعذر فتح صفحة التحديث", Toast.LENGTH_SHORT).show());
            }
        }

        /** رنة واهتزاز واضحان مرة واحدة عند اكتمال هدف التسبيح. */
        @JavascriptInterface
        public void tasbihGoalFeedback() {
            MainActivity a = get(); if (a == null) return;
            a.runOnUiThread(() -> {
                try {
                    Vibrator vibrator = (Vibrator) a.getSystemService(Context.VIBRATOR_SERVICE);
                    if (vibrator != null && vibrator.hasVibrator()) {
                        long[] pattern = {0L, 180L, 90L, 260L};
                        if (Build.VERSION.SDK_INT >= 26) {
                            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                        } else {
                            vibrator.vibrate(pattern, -1);
                        }
                    }
                } catch (Exception ignored) {}

                try {
                    ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
                    tone.startTone(ToneGenerator.TONE_PROP_ACK, 420);
                    new Handler(Looper.getMainLooper()).postDelayed(tone::release, 650L);
                } catch (Exception ignored) {}
            });
        }
    }
}
