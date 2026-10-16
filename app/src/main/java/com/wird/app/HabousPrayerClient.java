package com.wird.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the official daily Moroccan prayer times published by the Ministry of
 * Habous. Results are cached per city and GMT day so a transient network error
 * never replaces a valid official result with stale data.
 */
public final class HabousPrayerClient {
    public interface Callback { void onResult(JSONObject result); }

    private static final String PREF = "wird_habous_prayer_cache_v1";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Pattern TIME = Pattern.compile("([0-2]?[0-9]:[0-5][0-9])");
    private static final Pattern HIJRI = Pattern.compile(
            "(الأحد|الإثنين|الاثنين|الثلاثاء|الأربعاء|الاربعاء|الخميس|الجمعة|السبت)\\s+" +
            "(\\d{1,2})\\s+([\\u0600-\\u06FF ]{2,35}?)\\s+(14\\d{2})\\s*هـ");

    private HabousPrayerClient() {}

    public static void request(Context context, String cityName, int cityId, Callback callback) {
        request(context,cityName,cityId,false,callback);
    }
    public static void request(Context context,String cityName,int cityId,boolean force,Callback callback) {
        final Context app=context.getApplicationContext();
        EXECUTOR.execute(()->{
            JSONObject result=null;
            try {
                // Canonical Ministry name, including aliases selected by older versions.
                String city=HabousCityCatalog.name(app,cityId);
                result=HabousMonthStore.daily(app,cityId);
                if(result!=null&&!force&&!HabousMonthStore.needsRefresh(app,cityId,System.currentTimeMillis())) {callback.onResult(result);return;}
                if(result!=null)callback.onResult(result); // valid offline rows are available immediately
                fetchAndStoreMonth(app,city,cityId);
                result=HabousMonthStore.daily(app,cityId);
                JSONObject root=PrayerPrefs.load(app);
                if(root!=null&&root.optInt("cityId")==cityId){PrayerScheduler.rescheduleSaved(app,true);PrayerWidgetProvider.updateAll(app);}
            }catch(Exception error) {result=cached(app,cityId);}
            if(result==null) {result=new JSONObject();try{result.put("success",false).put("city",cityName).put("cityId",cityId).put("dateKey",gmtDay()).put("error","official_source_unavailable");}catch(Exception ignored){}}
            callback.onResult(result);
        });
    }

    static void fetchAndStoreMonth(Context app,String cityName,int cityId)throws Exception {
        if(cityId<1||cityId>999)throw new IllegalArgumentException("Invalid city id");
        Exception last=null;
        // Only monthly Ministry pages. A labelled daily page cannot fill a month.
        for(String host:new String[]{"https://www.habous.gov.ma","https://habous.gov.ma"}) {
            HttpURLConnection connection=null;
            try {
                URL url=new URL(host+"/prieres/horaire_hijri_2.php?ville="+cityId);
                connection=(HttpURLConnection)url.openConnection();connection.setConnectTimeout(6000);connection.setReadTimeout(8000);
                connection.setInstanceFollowRedirects(true);connection.setRequestProperty("Accept","text/html,application/xhtml+xml");
                connection.setRequestProperty("Accept-Language","ar-MA,ar;q=0.9");connection.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36 Wird/75.80");
                int code=connection.getResponseCode();if(code<200||code>=300)throw new IllegalStateException("HTTP "+code);
                if(!"https".equals(connection.getURL().getProtocol())||!connection.getURL().getHost().matches("(?:www\\.)?habous\\.gov\\.ma"))throw new IllegalStateException("Unexpected host");
                String html=read(connection.getInputStream(),charset(connection.getContentType()));
                JSONObject month=HabousMonthParser.parse(html,cityName,cityId,System.currentTimeMillis());
                HabousMonthStore.save(app,month);return;
            }catch(Exception e){last=e;}finally{if(connection!=null)connection.disconnect();}
        }
        throw last==null?new IllegalStateException("Official month unavailable"):last;
    }

    static JSONObject parse(String html, String cityName, int cityId) throws Exception {
        JSONObject officialRow = parseOfficialDailyRow(html, cityName, cityId);
        if (officialRow != null) return officialRow;

        String text = html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&#160;", " ")
                .replace("&rlm;", " ").replace("&lrm;", " ")
                .replaceAll("\\s+", " ").trim();

        JSONObject times = new JSONObject();
        times.put("Fajr", findTime(text, "(?:الفجر|الصبح)"));
        times.put("Sunrise", findTime(text, "الشروق"));
        times.put("Dhuhr", findTime(text, "الظهر"));
        times.put("Asr", findTime(text, "العصر"));
        times.put("Maghrib", findTime(text, "المغرب"));
        times.put("Isha", findTime(text, "العشاء"));

        JSONObject result = new JSONObject();
        result.put("city", cityName);
        result.put("cityId", cityId);
        result.put("times", times);

        Matcher hijri = HIJRI.matcher(toLatinDigits(text));
        if (hijri.find()) {
            JSONObject h = new JSONObject();
            h.put("weekday", hijri.group(1));
            h.put("day", Integer.parseInt(hijri.group(2)));
            h.put("month", hijri.group(3).trim());
            h.put("year", Integer.parseInt(hijri.group(4)));
            h.put("label", hijri.group(1) + "، " + hijri.group(2) + " " +
                    hijri.group(3).trim() + " " + hijri.group(4) + " هـ");
            result.put("hijri", h);
        }
        return result;
    }

    /**
     * The Ministry monthly page is a table, not a labelled daily API. Each
     * data row contains: weekday, Hijri day, Gregorian day, then the six
     * prayer times. Select today's Gregorian row so values come directly from
     * the official table instead of accidentally reading its first row.
     */
    private static JSONObject parseOfficialDailyRow(
            String html, String cityName, int cityId) throws Exception {
        return parseOfficialDailyRow(html,cityName,cityId,gmtDayOfMonth());
    }

    private static JSONObject parseOfficialDailyRow(String html,String cityName,int cityId,int today) throws Exception {
        Pattern rowPattern = Pattern.compile("(?is)<tr[^>]*>(.*?)</tr>");
        Pattern cellPattern = Pattern.compile("(?is)<t[dh][^>]*>(.*?)</t[dh]>");
        Matcher rows = rowPattern.matcher(html);

        while (rows.find()) {
            Matcher cellsMatcher = cellPattern.matcher(rows.group(1));
            List<String> cells = new ArrayList<>();
            while (cellsMatcher.find()) cells.add(cleanCell(cellsMatcher.group(1)));
            if (cells.size() < 8) continue;

            int firstTimeCell = -1;
            List<String> times = new ArrayList<>();
            for (int i = 0; i < cells.size(); i++) {
                Matcher tm = TIME.matcher(toLatinDigits(cells.get(i)));
                if (tm.find()) {
                    if (firstTimeCell < 0) firstTimeCell = i;
                    times.add(normalizeTime(tm.group(1)));
                }
            }
            if (times.size() < 6 || firstTimeCell < 2) continue;

            // The Gregorian day is the cell immediately before Fajr.
            int gregorianDay = integerCell(cells.get(firstTimeCell - 1));
            if (gregorianDay != today) continue;

            JSONObject prayerTimes = new JSONObject();
            prayerTimes.put("Fajr", times.get(0));
            prayerTimes.put("Sunrise", times.get(1));
            prayerTimes.put("Dhuhr", times.get(2));
            prayerTimes.put("Asr", times.get(3));
            prayerTimes.put("Maghrib", times.get(4));
            prayerTimes.put("Isha", times.get(5));

            JSONObject result = new JSONObject();
            result.put("city", cityName);
            result.put("cityId", cityId);
            result.put("times", prayerTimes);
            result.put("officialTable", true);
            attachTableHijri(result, html, integerCell(cells.get(firstTimeCell - 2)), cells.get(0));
            return result;
        }

        // Some versions of the Ministry page use div/span based rows instead
        // of table rows. Parse the visible monthly text as a second route.
        String flat = toLatinDigits(html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&#160;", " ")
                .replace("&rlm;", " ").replace("&lrm;", " ")
                .replaceAll("\\s+", " ").trim());
        String separator = "\\s*[,|;،]?\\s*";
        Pattern flatRow = Pattern.compile(
                "(الأحد|الإثنين|الاثنين|الثلاثاء|الأربعاء|الاربعاء|الخميس|الجمعة|السبت)" +
                separator + "(\\d{1,2})" + separator + "(\\d{1,2})" + separator +
                TIME.pattern() + separator + TIME.pattern() + separator +
                TIME.pattern() + separator + TIME.pattern() + separator +
                TIME.pattern() + separator + TIME.pattern());
        Matcher flatRows = flatRow.matcher(flat);
        while (flatRows.find()) {
            if (Integer.parseInt(flatRows.group(3)) != today) continue;
            JSONObject prayerTimes = new JSONObject();
            prayerTimes.put("Fajr", normalizeTime(flatRows.group(4)));
            prayerTimes.put("Sunrise", normalizeTime(flatRows.group(5)));
            prayerTimes.put("Dhuhr", normalizeTime(flatRows.group(6)));
            prayerTimes.put("Asr", normalizeTime(flatRows.group(7)));
            prayerTimes.put("Maghrib", normalizeTime(flatRows.group(8)));
            prayerTimes.put("Isha", normalizeTime(flatRows.group(9)));
            JSONObject result = new JSONObject();
            result.put("city", cityName);
            result.put("cityId", cityId);
            result.put("times", prayerTimes);
            result.put("officialTable", true);
            attachTableHijri(result, html, Integer.parseInt(flatRows.group(2)), flatRows.group(1));
            return result;
        }
        return null;
    }

    private static void attachTableHijri(JSONObject result, String html, int day, String weekday) {
        if (day < 1 || day > 30) return;
        String text = toLatinDigits(cleanCell(html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")));
        Pattern monthYear = Pattern.compile(
                "(محرم|صفر|ربيع\\s+(?:الأول|الاول|الآخر|الاخر|الثاني)|" +
                "جمادى\\s+(?:الأولى|الاولي|الآخرة|الاخرة|الثانية)|رجب|شعبان|رمضان|شوال|" +
                "ذو\\s+(?:القعدة|الحجة))\\s+(?:(?:لعام|عام|لسنة|سنة)\\s+)?(14\\d{2})");
        Matcher month = monthYear.matcher(text);
        if (!month.find()) return;
        try {
            JSONObject hijri = new JSONObject();
            hijri.put("day", day); hijri.put("month", month.group(1).trim());
            hijri.put("year", Integer.parseInt(month.group(2)));
            hijri.put("weekday", weekday.trim());
            result.put("hijri", hijri);
        } catch (Exception ignored) {}
    }

    private static String cleanCell(String html) {
        return html.replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&#160;", " ")
                .replace("&rlm;", " ").replace("&lrm;", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private static String toLatinDigits(String value) {
        return value.replace('٠', '0').replace('١', '1').replace('٢', '2')
                .replace('٣', '3').replace('٤', '4').replace('٥', '5')
                .replace('٦', '6').replace('٧', '7').replace('٨', '8')
                .replace('٩', '9');
    }

    private static String normalizeTime(String value) {
        String[] parts = toLatinDigits(value).split(":");
        return String.format(Locale.US, "%02d:%02d",
                Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    private static int integerCell(String value) {
        Matcher number = Pattern.compile("\\d{1,2}").matcher(toLatinDigits(value));
        return number.find() ? Integer.parseInt(number.group()) : -1;
    }

    private static String findTime(String text, String labelRegex) {
        Pattern p = Pattern.compile(labelRegex + "\\s*[:：]?\\s*[^0-9]{0,24}" + TIME.pattern());
        Matcher m = p.matcher(text);
        if (!m.find()) throw new IllegalArgumentException("Missing prayer time: " + labelRegex);
        String[] parts = m.group(1).split(":");
        return String.format(Locale.US, "%02d:%02d",
                Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    private static Charset charset(String contentType) {
        if (contentType != null) {
            Matcher m = Pattern.compile("charset=([^; ]+)", Pattern.CASE_INSENSITIVE)
                    .matcher(contentType);
            if (m.find()) {
                try { return Charset.forName(m.group(1).replace("\"", "")); }
                catch (Exception ignored) {}
            }
        }
        return Charset.forName("UTF-8");
    }

    private static String read(InputStream stream, Charset charset) throws Exception {
        StringBuilder out = new StringBuilder(32768);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, charset))) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) { if(out.length()+count>524288)throw new java.io.IOException("Page too large"); out.append(buffer, 0, count); }
        }
        return out.toString();
    }

    private static String cacheKey(int cityId,String date) {
        return cityId+"_"+date+"_"+TimeZone.getDefault().getOffset(System.currentTimeMillis());
    }

    private static void cache(Context context, int cityId, JSONObject result) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(cacheKey(cityId,result.optString("dateKey",gmtDay())), result.toString()).apply();
        JSONObject tomorrow=result.optJSONObject("tomorrow");
        if(tomorrow!=null)context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                .putString(cacheKey(cityId,tomorrow.optString("dateKey")),tomorrow.toString()).apply();
    }

    private static JSONObject cached(Context context,int cityId) {try{JSONObject d=HabousMonthStore.daily(context,cityId);if(d!=null)return d;}catch(Exception ignored){}return cachedDay(context,cityId,gmtDay());}

    public static JSONObject cachedDay(Context context, int cityId, String key) {
        JSONObject monthly=HabousMonthStore.day(context,cityId,key);if(monthly!=null)return monthly;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String raw = prefs.getString(cacheKey(cityId,key), null);
            if (raw == null) return null;
            JSONObject result = new JSONObject(raw);
            if(!key.equals(result.optString("dateKey")) || result.optInt("cityId")!=cityId)return null;
            result.put("success", true);
            return result;
        } catch (Exception e) { return null; }
    }

    private static String gmtDay() {return dayKey(new Date());}
    private static String dayKey(Date date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        return format.format(date);
    }

    private static int gmtDayOfMonth() {
        SimpleDateFormat format = new SimpleDateFormat("d", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        return Integer.parseInt(format.format(new Date()));
    }
}
