package com.wird.app;

import android.content.Context;
import org.json.*;
import java.util.*;

/** One atomic preference payload per city, with at most 75 days retained. */
final class HabousMonthStore {
    private static String pref(int id){return "wird_habous_month_v1_"+id;}
    static synchronized JSONObject load(Context c,int id){try{return new JSONObject(c.getSharedPreferences(pref(id),0).getString("table","{}"));}catch(Exception e){return new JSONObject();}}
    static synchronized void save(Context c,JSONObject table)throws Exception {
        int id=table.getInt("cityId");JSONArray rows=table.getJSONArray("days");if(rows.length()<29||rows.length()>30)throw new IllegalArgumentException("Partial table");
        TreeMap<String,JSONObject> keep=new TreeMap<>();JSONObject previous=load(c,id);JSONArray old=previous.optJSONArray("days");String min=HabousMonthParser.plus(HabousMonthParser.today(),-35);
        if(old!=null)for(int i=0;i<old.length();i++){JSONObject d=old.getJSONObject(i);if(d.optString("dateKey").compareTo(min)>=0)keep.put(d.getString("dateKey"),d);}
        for(int i=0;i<rows.length();i++){JSONObject d=rows.getJSONObject(i);if(d.optInt("cityId")!=id||!HabousMonthParser.validTimes(d.optJSONObject("times")))throw new IllegalArgumentException("Invalid city/day");keep.put(d.getString("dateKey"),d);}
        while(keep.size()>75)keep.pollFirstEntry();JSONArray merged=new JSONArray();for(JSONObject d:keep.values())merged.put(d);
        JSONObject payload=new JSONObject(table.toString()).put("days",merged);
        if(!c.getSharedPreferences(pref(id),0).edit().putString("table",payload.toString()).commit())throw new IllegalStateException("Month save failed");
    }
    static JSONObject day(Context c,int id,String key){JSONObject table=load(c,id);if(table.optInt("cityId")!=id)return null;JSONArray rows=table.optJSONArray("days");if(rows==null)return null;
        for(int i=0;i<rows.length();i++){JSONObject d=rows.optJSONObject(i);if(d!=null&&key.equals(d.optString("dateKey"))&&d.optInt("cityId")==id&&HabousMonthParser.validTimes(d.optJSONObject("times")))return d;}return null;}
    static boolean needsRefresh(Context c,int id,long now){try{JSONObject table=load(c,id);String today=HabousMonthParser.today();
        if(day(c,id,today)==null||day(c,id,HabousMonthParser.plus(today,7))==null)return true;
        Calendar fetched=Calendar.getInstance(TimeZone.getTimeZone("UTC"));fetched.setTimeInMillis(table.optLong("fetchedAt"));
        return !HabousMonthParser.key(fetched).substring(0,7).equals(today.substring(0,7));
    }catch(Exception e){return true;}}
    static JSONObject daily(Context c,int id)throws Exception{String today=HabousMonthParser.today();JSONObject d=day(c,id,today);if(d==null)return null;
        JSONObject table=load(c,id);d.put("cached",true).put("fetchedAt",table.optLong("fetchedAt")).put("coverageEnd",table.optString("lastDate"));
        JSONObject tomorrow=day(c,id,HabousMonthParser.plus(today,1));if(tomorrow!=null)d.put("tomorrow",tomorrow);
        JSONArray days=new JSONArray();for(int i=0;i<7;i++){JSONObject row=day(c,id,HabousMonthParser.plus(today,i));if(row!=null)days.put(new JSONObject(row.toString()).put("source","official"));}d.put("days",days);return d;}
    private HabousMonthStore(){}
}
