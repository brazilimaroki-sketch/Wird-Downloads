package com.wird.app;
import android.content.Context;
import org.json.*;
final class PrayerSources {
    static JSONObject day(Context c,int id,String key){JSONObject imported=ImportedPrayerStore.day(c,id,key);if(imported!=null)return imported;JSONObject official=HabousPrayerClient.cachedDay(c,id,key);if(official!=null)try{return new JSONObject(official.toString()).put("source","official");}catch(Exception ignored){}return null;}
    static JSONObject daily(Context c,int id)throws Exception{String today=HabousMonthParser.today();JSONObject row=day(c,id,today);if(row==null)return null;row.put("city",HabousCityCatalog.name(c,id)).put("success",true);JSONArray days=new JSONArray();for(int i=0;i<7;i++){JSONObject d=day(c,id,HabousMonthParser.plus(today,i));if(d!=null)days.put(d);}row.put("days",days);JSONObject tomorrow=day(c,id,HabousMonthParser.plus(today,1));if(tomorrow!=null)row.put("tomorrow",tomorrow);return row;}
    static JSONArray overlay(Context c,int id,JSONArray days)throws Exception{JSONArray out=new JSONArray();for(int i=0;i<days.length();i++){JSONObject d=new JSONObject(days.getJSONObject(i).toString()),row=day(c,id,d.getString("dateKey"));if(row!=null)d.put("times",row.getJSONObject("times")).put("source",row.optString("source"));out.put(d);}return out;}
    private PrayerSources(){}
}
