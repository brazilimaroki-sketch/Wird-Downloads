"""Run the real voice catalogue and saved-choice logic against in-memory Android prefs."""
from prayer_alarm_test import sources,run_test,ROOT
import re
s=dict(sources)
s.pop('com/wird/app/PrayerPrefs.java');s.pop('com/wird/app/TestPrayerAlarm.java')
s['android/content/SharedPreferences.java']=s['android/content/SharedPreferences.java'].replace('public void apply(){}','public Editor clear(){v.clear();return this;} public void apply(){}')
s['org/json/JSONObject.java']=s['org/json/JSONObject.java'].replace('Map<String,Object> v=new HashMap<>();','Map<String,Object> v=new HashMap<>(); static Map<String,Map<String,Object>> registry=new HashMap<>(); public JSONObject(){} public JSONObject(String text){v=new HashMap<>(registry.get(text));} public String toString(){String id="object"+registry.size();registry.put(id,new HashMap<>(v));return id;}')
s['org/json/JSONArray.java']='package org.json;public class JSONArray {public JSONArray put(Object x){return this;}public String toString(){return "[]";}}'
resources=re.findall(r'R.raw.(\w+)',(ROOT/'app/src/main/java/com/wird/app/AdhanCatalog.java').read_text())
s['com/wird/app/R.java']='package com.wird.app;public class R {public static class raw {'+';'.join('public static final int '+v+'='+str(i+1) for i,v in enumerate(resources))+';}}'
s['com/wird/app/TestAdhanVoices.java']='''package com.wird.app;
import android.content.Context;import org.json.JSONObject;
public class TestAdhanVoices {
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 public static void main(String[] args){
  Context c=new Context();check(PrayerPrefs.adhanResource(c,"Fajr")==R.raw.adhan,"default before save");
  JSONObject settings=new JSONObject().put("adhanVoice","mishary_afasi").put("fajrVoice","same");PrayerPrefs.save(c,settings);
  check(PrayerPrefs.adhanResource(c,"Dhuhr")==R.raw.adhan_mishary_afasi,"regular saved choice");
  check(PrayerPrefs.adhanResource(c,"Fajr")==R.raw.adhan_mishary_afasi,"same dawn voice");
  settings.put("fajrVoice","malek_fajr");PrayerPrefs.save(c,settings);
  check(PrayerPrefs.adhanResource(c,"Fajr")==R.raw.adhan_malek_fajr,"independent dawn voice");
  check(PrayerPrefs.adhanResource(c,"Isha")==R.raw.adhan_mishary_afasi,"dawn does not affect Isha");
  check(AdhanCatalog.resource("malek_fajr",false)==R.raw.adhan,"fajr recording excluded from regular prayers");
  check(AdhanCatalog.resource("malek_fajr",true)==R.raw.adhan_malek_fajr,"preview may play dawn voice");
  check(AdhanCatalog.resource("../../unknown",true)==R.raw.adhan,"unknown IDs use local default");
  settings.put("adhanVoice","two_takbeers").put("fajrVoice","same");PrayerPrefs.save(c,settings);
  check(PrayerPrefs.adhanResource(c,"Dhuhr")==R.raw.adhan_two_takbeers,"two takbeers regular resource");
  check(PrayerPrefs.adhanResource(c,"Fajr")==R.raw.adhan_two_takbeers,"two takbeers also allowed at dawn");
  check(AdhanCatalog.name("two_takbeers").contains("تكبيرتان"),"short clip label");
  settings.put("adhanVoice","unknown").put("fajrVoice","same");PrayerPrefs.save(c,settings);
  check(PrayerPrefs.adhanResource(c,"Fajr")==R.raw.adhan,"invalid persisted choice falls back");
  PrayerPrefs.clear(c);check(PrayerPrefs.adhanResource(c,"Maghrib")==R.raw.adhan,"cleared settings fall back");
  System.out.println("Saved voice selection, independent dawn selection, dawn-only exclusion, preview and invalid-ID fallback passed");
 }
}'''
run_test(s,'com.wird.app.TestAdhanVoices',('PrayerPrefs.java','AdhanCatalog.java'))
