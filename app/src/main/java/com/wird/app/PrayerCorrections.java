package com.wird.app;
import android.content.Context;
import org.json.*;
import java.util.*;
final class PrayerCorrections {
    static String key(int cityId,String label){return cityId>0?"city:"+cityId:"place:"+label;}
    static JSONObject get(Context c,int cityId,String label){try{return checked(new JSONObject(c.getSharedPreferences("wird_prayer_corrections",0).getString(key(cityId,label),"{}")));}catch(Exception e){return zeros();}}
    static JSONObject zeros(){JSONObject out=new JSONObject();try{for(String k:PrayerScheduler.NAMES)out.put(k,0);}catch(Exception ignored){}return out;}
    static JSONObject checked(JSONObject values)throws Exception{JSONObject out=zeros();for(String k:PrayerScheduler.NAMES){Object raw=values.opt(k);if(raw==null)continue;if(!(raw instanceof Number)||((Number)raw).doubleValue()!=((Number)raw).intValue())throw new IllegalArgumentException("التصحيح يجب أن يكون عدداً صحيحاً بالدقائق");int value=((Number)raw).intValue();if(value < -60 || value > 60)throw new IllegalArgumentException("التصحيح بين -60 و+60 دقيقة");out.put(k,value);}return out;}
    static JSONObject apply(JSONObject raw,JSONObject offsets)throws Exception{JSONObject t=new JSONObject(raw.toString());for(String k:PrayerScheduler.NAMES){String value=t.optString(k);if(!value.matches("[0-9]{2}:[0-9]{2}"))throw new IllegalArgumentException("مواقيت غير متوفرة");String[] p=value.split(":");int minutes=Integer.parseInt(p[0])*60+Integer.parseInt(p[1])+offsets.optInt(k,0);if(minutes<0||minutes>=1440)throw new IllegalArgumentException("التصحيح يتجاوز حدود اليوم");t.put(k,String.format(Locale.US,"%02d:%02d",minutes/60,minutes%60));}
        if(!HabousMonthParser.validTimes(t))throw new IllegalArgumentException("التصحيح يخل بترتيب الصلوات أو وقت الشروق");return t;}
    static JSONArray applyDays(JSONArray raw,JSONObject values)throws Exception{JSONArray out=new JSONArray();for(int i=0;i<raw.length();i++){JSONObject day=new JSONObject(raw.getJSONObject(i).toString()),base=day.optJSONObject("baseTimes");if(base==null)base=day.getJSONObject("times");day.put("baseTimes",new JSONObject(base.toString())).put("times",apply(base,values)).put("corrections",values);out.put(day);}return out;}
    static void save(Context c,int cityId,String label,JSONObject input,JSONArray baseDays)throws Exception{JSONObject values=checked(input);if(baseDays==null||baseDays.length()==0)throw new IllegalArgumentException("حدّث المواقيت قبل ضبط التصحيح");applyDays(baseDays,values);if(!c.getSharedPreferences("wird_prayer_corrections",0).edit().putString(key(cityId,label),values.toString()).commit())throw new IllegalStateException("تعذر حفظ التصحيح");}
    private PrayerCorrections(){}
}
