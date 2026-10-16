package com.wird.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.Locale;

/** ويدجت الصلاة القادمة. يعتمد نفس المواقيت المحفوظة التي يعرضها التطبيق. */
public class PrayerWidgetProvider extends AppWidgetProvider {
    private static final long AFTER_ADHAN_MS = 20 * 60 * 1000L;
    private static final String ACTION_REFRESH = "com.wird.app.WIDGET_REFRESH";

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null) return;
        String action = intent.getAction();
        if (ACTION_REFRESH.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_LOCALE_CHANGED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            updateAll(context);
        }
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(context, manager, id);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
            int id, android.os.Bundle options) { update(context, manager, id); }

    @Override public void onEnabled(Context context) { updateAll(context); }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        Class<?>[] providers = {PrayerWidgetProvider.class, PrayerWidgetSmallProvider.class,
                PrayerWidgetLargeProvider.class};
        for (Class<?> provider : providers) {
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, provider));
            for (int id : ids) update(context, manager, id);
        }
    }

    private static void update(Context context, AppWidgetManager manager, int widgetId) {
        android.appwidget.AppWidgetProviderInfo info = manager.getAppWidgetInfo(widgetId);
        if (info == null) return;
        android.os.Bundle options = manager.getAppWidgetOptions(widgetId);
        android.content.res.Configuration configuration = context.getResources().getConfiguration();
        boolean landscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int width = options.getInt(landscape ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int height = options.getInt(landscape ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        // The chosen provider owns its layout; resizing must never turn Small into Tall.
        int layout = layoutForProvider(info.provider.getClassName());
        boolean tall = layout == R.layout.widget_prayer_tall;
        RemoteViews v = new RemoteViews(context.getPackageName(), layout);
        String theme = context.getSharedPreferences("wird_appearance", Context.MODE_PRIVATE)
                .getString("app_theme", "blue");
        v.setInt(R.id.widgetRoot, "setBackgroundResource", themeBackground(theme));
        Intent open = new Intent(context, MainActivity.class)
                .putExtra("open_target", "prayer")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 9600 + widgetId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widgetRoot, pi);

        JSONObject root = PrayerPrefs.load(context);
        if(root!=null){root=PrayerDayData.refresh(context,root);PrayerPrefs.save(context,root);}
        JSONObject times = root == null ? null : root.optJSONObject("times");
        String city = root == null ? "وِرد" : cleanCity(root.optString("city", "وِرد"));
        v.setTextViewText(R.id.widgetCity, city);
        NextPrayer upcoming = findNextDated(root,times,false);
        fillPrayerStrip(v, times, tall, upcoming == null ? null : upcoming.name);
        int accent = android.graphics.Color.rgb(255, 220, 137);
        v.setTextColor(R.id.widgetPrayerName, accent);
        v.setTextColor(R.id.widgetPrayerTime, accent);
        v.setTextColor(R.id.widgetCountdown, android.graphics.Color.rgb(237, 246, 245));

        if (tall && height < 260) {
            for (int id : new int[] {R.id.widgetFajr, R.id.widgetDhuhr, R.id.widgetAsr,
                    R.id.widgetMaghrib, R.id.widgetIsha, R.id.widgetFajrLabel, R.id.widgetDhuhrLabel,
                    R.id.widgetAsrLabel, R.id.widgetMaghribLabel, R.id.widgetIshaLabel}) {
                v.setTextViewTextSize(id, android.util.TypedValue.COMPLEX_UNIT_SP, width > 0 && width < 160 ? 12f : 13f);
            }
        }
        // Scale type with the available width; keep long prayer names on one line.
        if (width > 0) {
            float nameSize = tall ? Math.min(25f, Math.max(18f, (width - 34) / 8f))
                    : Math.min(layout == R.layout.widget_prayer_large ? 36f : 22f, Math.max(18f, (width - 38) / 11f));
            v.setTextViewTextSize(R.id.widgetPrayerName, android.util.TypedValue.COMPLEX_UNIT_SP, nameSize);
            v.setTextViewTextSize(R.id.widgetPrayerTime, android.util.TypedValue.COMPLEX_UNIT_SP,
                    nameSize);
            v.setTextViewTextSize(R.id.widgetCountdown, android.util.TypedValue.COMPLEX_UNIT_SP,
                    layout == R.layout.widget_prayer_small || width < 170 ? 12f : 14f);
        }

        NextPrayer next = findNextDated(root,times,true);
        if (next == null) {
            v.setTextViewText(R.id.widgetPrayerName, "مواقيت الصلاة");
            v.setTextViewText(R.id.widgetPrayerTime, "--:--");
            v.setTextViewText(R.id.widgetCaption, "افتح التطبيق للتحديث");
            v.setViewVisibility(R.id.widgetCountdown, View.GONE);
        } else {
            v.setTextViewText(R.id.widgetPrayerName, next.name);
            v.setTextViewText(R.id.widgetPrayerTime, next.time);
            v.setTextViewText(R.id.widgetCaption, next.elapsed ? "مرّ" : "متبقي");
            v.setViewVisibility(R.id.widgetCountdown, View.VISIBLE);
            long base = SystemClock.elapsedRealtime() + next.atMillis - System.currentTimeMillis();
            v.setChronometerCountDown(R.id.widgetCountdown, !next.elapsed);
            v.setChronometer(R.id.widgetCountdown, base, null, true);
            
        }
        long dateBoundary = nextMidnight(root);
        long prayerBoundary = next == null ? dateBoundary
                : next.atMillis + (next.elapsed ? AFTER_ADHAN_MS : 0);
        scheduleRefresh(context, Math.min(dateBoundary, prayerBoundary));
        manager.updateAppWidget(widgetId, v);
    }

    static int layoutForProvider(String providerName) {
        if (PrayerWidgetSmallProvider.class.getName().equals(providerName))
            return R.layout.widget_prayer_small;
        if (PrayerWidgetLargeProvider.class.getName().equals(providerName))
            return R.layout.widget_prayer_large;
        return R.layout.widget_prayer_tall;
    }

    private static java.util.TimeZone widgetZone(JSONObject root) {
        String zone = root == null ? "" : root.optString("timezone", "");
        return zone.isEmpty() ? java.util.TimeZone.getDefault() : java.util.TimeZone.getTimeZone(zone);
    }

    private static long nextMidnight(JSONObject root) {
        Calendar date = Calendar.getInstance(widgetZone(root));
        date.add(Calendar.DAY_OF_YEAR, 1);
        date.set(Calendar.HOUR_OF_DAY, 0); date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0); date.set(Calendar.MILLISECOND, 0);
        return date.getTimeInMillis();
    }

    /** نفس لوحة الألوان المختارة داخل التطبيق، بدون الحاجة لإزالة الويدجت وإضافته من جديد. */
    private static int themeBackground(String id) {
        if ("green".equals(id)) return R.drawable.widget_prayer_background_green;
        if ("gold".equals(id)) return R.drawable.widget_prayer_background_gold;
        if ("purple".equals(id)) return R.drawable.widget_prayer_background_purple;
        if ("burgundy".equals(id)) return R.drawable.widget_prayer_background_burgundy;
        if ("teal".equals(id)) return R.drawable.widget_background;
        return R.drawable.widget_prayer_background;
    }

    private static void fillPrayerStrip(RemoteViews v, JSONObject times, boolean tall, String upcomingName) {
        int[] ids = {R.id.widgetFajr, R.id.widgetDhuhr, R.id.widgetAsr,
                R.id.widgetMaghrib, R.id.widgetIsha};
        String[] keys = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};
        String[] labels = {"الفجر", "الظهر", "العصر", "المغرب", "العشاء"};
        for (int i = 0; i < ids.length; i++) {
            String time = times == null ? "--:--" : safeTime(times.optString(keys[i], "--:--"));
            boolean active = labels[i].equals(upcomingName);
            String text = time;
            android.text.SpannableString styled = new android.text.SpannableString(text);
            styled.setSpan(new android.text.style.StyleSpan(active ? android.graphics.Typeface.BOLD
                    : android.graphics.Typeface.NORMAL), 0, styled.length(), 0);
            v.setTextViewText(ids[i], styled);
            v.setTextColor(ids[i], active ? android.graphics.Color.rgb(32, 40, 47)
                    : android.graphics.Color.WHITE);
            // Always reset every row so the previous highlight cannot persist.
            v.setInt(ids[i], "setBackgroundResource", 0);
            {
                int[] rows = {R.id.widgetFajrRow, R.id.widgetDhuhrRow, R.id.widgetAsrRow,
                        R.id.widgetMaghribRow, R.id.widgetIshaRow};
                int[] labelsIds = {R.id.widgetFajrLabel, R.id.widgetDhuhrLabel, R.id.widgetAsrLabel,
                        R.id.widgetMaghribLabel, R.id.widgetIshaLabel};
                v.setInt(rows[i], "setBackgroundResource", active ? R.drawable.bg_prayer_active
                        : R.drawable.bg_prayer_inactive);
                v.setTextColor(labelsIds[i], active ? android.graphics.Color.rgb(32, 40, 47)
                        : android.graphics.Color.WHITE);
            }
        }
    }

    private static String cleanCity(String city) {
        if (city == null) return "وِرد";
        String clean = city.split("،")[0].trim();
        return clean.isEmpty() ? "وِرد" : clean;
    }

    private static String safeTime(String raw) {
        if (raw == null) return "--:--";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(raw);
        return m.find() ? String.format(Locale.US, "%02d:%02d",
                Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))) : "--:--";
    }

    private static void scheduleRefresh(Context context, long at) {
        if (at <= System.currentTimeMillis()) at = System.currentTimeMillis() + 1000L;
        Intent i = new Intent(context, PrayerWidgetProvider.class).setAction(ACTION_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(context, 9699, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        else {
            try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi); }
            catch (SecurityException e) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi); }
        }
    }

    private static NextPrayer findNextDated(JSONObject root,JSONObject times,boolean includeElapsed) {
        NextPrayer legacy=findNext(times,includeElapsed);
        if(legacy!=null&&legacy.elapsed)return legacy;
        org.json.JSONArray days=root==null?null:root.optJSONArray("days");if(days==null)return legacy;
        NextPrayer best=null;long now=System.currentTimeMillis();
        for(String prayer:PrayerScheduler.NAMES)try {
            long at=NativePrayerEngine.next(days,prayer,now);
            if(best==null||at<best.atMillis)best=new NextPrayer(arabicName(prayer),new java.text.SimpleDateFormat("HH:mm",Locale.US).format(new java.util.Date(at)),at,false);
        }catch(Exception ignored){}
        return best;
    }

    private static NextPrayer findNext(JSONObject times, boolean includeElapsed) {
        try {
            if (times == null) return null;
            Calendar now = Calendar.getInstance();
            NextPrayer best = null, previous = null;
            for (String key : PrayerScheduler.NAMES) {
                String[] p = times.optString(key, "").split(":");
                if (p.length < 2) continue;
                int h = Integer.parseInt(p[0].replaceAll("[^0-9]", ""));
                int m = Integer.parseInt(p[1].replaceAll("[^0-9].*$", ""));
                if (h < 0 || h > 23 || m < 0 || m > 59) continue;
                Calendar at = Calendar.getInstance();
                at.set(Calendar.HOUR_OF_DAY, h); at.set(Calendar.MINUTE, m);
                at.set(Calendar.SECOND, 0); at.set(Calendar.MILLISECOND, 0);
                Calendar past = (Calendar) at.clone();
                if (past.after(now)) past.add(Calendar.DAY_OF_YEAR, -1);
                String display = String.format(Locale.US, "%02d:%02d", h, m);
                if (previous == null || past.getTimeInMillis() > previous.atMillis)
                    previous = new NextPrayer(arabicName(key), display, past.getTimeInMillis(), true);
                if (!at.after(now)) at.add(Calendar.DAY_OF_YEAR, 1);
                if (best == null || at.getTimeInMillis() < best.atMillis)
                    best = new NextPrayer(arabicName(key), display, at.getTimeInMillis(), false);
            }
            if (includeElapsed && previous != null && now.getTimeInMillis() - previous.atMillis < AFTER_ADHAN_MS)
                return previous;
            return best;
        } catch (Exception e) { return null; }
    }

    private static String arabicName(String key) {
        switch (key) {
            case "Fajr": return "الفجر";
            case "Dhuhr": return "الظهر";
            case "Asr": return "العصر";
            case "Maghrib": return "المغرب";
            case "Isha": return "العشاء";
            default: return "الصلاة";
        }
    }

    private static final class NextPrayer {
        final String name, time; final long atMillis; final boolean elapsed;
        NextPrayer(String name, String time, long atMillis, boolean elapsed) {
            this.name = name; this.time = time; this.atMillis = atMillis; this.elapsed = elapsed;
        }
    }
}
