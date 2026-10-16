package com.wird.app;
import android.content.Context;
import org.json.*;
final class ImportedPrayerStore {
    private static String pref(int id){return "wird_imported_prayer_"+id;}
    static JSONObject load(Context c,int id){try{return new JSONObject(c.getSharedPreferences(pref(id),0).getString("table","{}"));}catch(Exception e){return new JSONObject();}}
    static void save(Context c,JSONObject table)throws Exception{int id=table.getInt("cityId");if(!c.getSharedPreferences(pref(id),0).edit().putString("table",table.toString()).putBoolean("active",true).commit())throw new IllegalStateException("تعذر حفظ الجدول");}
    static boolean active(Context c,int id){return c.getSharedPreferences(pref(id),0).getBoolean("active",false);}
    static void clear(Context c,int id){c.getSharedPreferences(pref(id),0).edit().clear().apply();}
    static JSONObject day(Context c,int id,String key){if(!active(c,id))return null;JSONObject table=load(c,id);if(table.optInt("cityId")!=id)return null;JSONArray days=table.optJSONArray("days");if(days!=null)for(int i=0;i<days.length();i++){JSONObject row=days.optJSONObject(i);if(row!=null&&key.equals(row.optString("dateKey"))&&HabousMonthParser.validTimes(row.optJSONObject("times")))return row;}return null;}
    private ImportedPrayerStore(){}
}
