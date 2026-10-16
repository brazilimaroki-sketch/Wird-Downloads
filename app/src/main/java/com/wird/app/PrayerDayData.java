package com.wird.app;
import android.content.Context;
import org.json.*;
/** One dated source/correction pipeline shared by UI, alarms and widgets. Raw tables are never modified. */
public final class PrayerDayData {
    static JSONArray baseDays(Context c,JSONObject root)throws Exception{
        String method=root.optString("method","3");boolean coordinates=!root.isNull("lat")&&!root.isNull("lon");
        JSONArray days=coordinates?NativePrayerEngine.days(root.getDouble("lat"),root.getDouble("lon"),method,root.optString("timezone","UTC"),root.optBoolean("hanafi",false),System.currentTimeMillis()):new JSONArray();
        if(!"21".equals(method))return days;
        if(!coordinates){String today=HabousMonthParser.today();for(int i=0;i<7;i++){JSONObject row=PrayerSources.day(c,root.optInt("cityId"),HabousMonthParser.plus(today,i));if(row!=null)days.put(row);}return days;}
        days=PrayerSources.overlay(c,root.optInt("cityId"),days);
        // Preserve an old official payload only for its exact day and offset, using raw values.
        for(int i=0;i<days.length();i++){JSONObject d=days.getJSONObject(i),raw=root.optJSONObject("baseTimes");if(raw==null)raw=root.optJSONObject("times");
            if(!"official".equals(d.optString("source"))&&!"imported".equals(d.optString("source"))&&root.optBoolean("official",false)&&d.optString("dateKey").equals(root.optString("dateKey"))&&HabousMonthParser.validTimes(raw)&&root.optInt("clockOffset",java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()))==java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()))d.put("times",raw).put("source","official");}
        return days;
    }
    public static JSONObject refresh(Context c,JSONObject root){try{
        if(!root.has("method"))return root;
        JSONArray base=baseDays(c,root);JSONObject correction=PrayerCorrections.get(c,root.optInt("cityId"),root.optString("city"));JSONArray days=PrayerCorrections.applyDays(base,correction);
        root.put("baseDays",base).put("days",days).put("corrections",correction).put("clockOffset",java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()));
        java.text.SimpleDateFormat todayFormat=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US);todayFormat.setTimeZone(java.util.TimeZone.getTimeZone(root.optString("timezone","UTC")));String today=todayFormat.format(new java.util.Date());JSONObject first=days.optJSONObject(0);
        if(first!=null&&today.equals(first.optString("dateKey")))root.put("times",first.getJSONObject("times")).put("baseTimes",first.getJSONObject("baseTimes")).put("dateKey",today).put("official","official".equals(first.optString("source"))).put("source",first.optString("source"));
        else root.put("times",new JSONObject()).put("baseTimes",new JSONObject()).put("dateKey",today).put("official",false).put("source","unavailable");
        root.remove("dataError");
    }catch(Exception e){android.util.Log.e("WirdPrayer","Could not refresh dated prayer data",e);try{root.put("days",new JSONArray()).put("times",new JSONObject()).put("baseTimes",new JSONObject()).put("dataError",e.getMessage());}catch(Exception ignored){}}
        return root;
    }
    private PrayerDayData(){}
}
