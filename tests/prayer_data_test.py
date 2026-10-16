"""Compile/run the actual native engine, day cache and scheduler with JSON and a fake Android boundary."""
from pathlib import Path
import tempfile,subprocess
from prayer_alarm_test import sources,ROOT
s={k:v for k,v in sources.items() if k.startswith('android/')}
s['android/content/Context.java']=s['android/content/Context.java'].replace('public Object getSystemService','public Context getApplicationContext(){return this;} public Object getSystemService')
s['android/content/SharedPreferences.java']=s['android/content/SharedPreferences.java'].replace('public void apply(){}','public Editor clear(){v.clear();return this;}public void apply(){}')
s['android/content/SharedPreferences.java']=s['android/content/SharedPreferences.java'].replace('public void apply(){}','public boolean commit(){return true;}public void apply(){}')
s['com/wird/app/HabousCityCatalog.java']='package com.wird.app;import android.content.Context;public class HabousCityCatalog {static String name(Context c,int id){return id==158?"بوجدور":"الرباط";}}'
s['com/wird/app/PrayerWidgetProvider.java']='package com.wird.app;import android.content.Context;public class PrayerWidgetProvider {public static void updateAll(Context c){}}'
s['com/wird/app/MainActivity.java']='package com.wird.app;public class MainActivity {}'
s['com/wird/app/PrayerAlarmReceiver.java']='package com.wird.app;public class PrayerAlarmReceiver {}'
s['com/wird/app/AdhanCatalog.java']='package com.wird.app;public class AdhanCatalog {public static int resource(String s,boolean f){return 1;}}'
s['com/wird/app/TestPrayerData.java']='''package com.wird.app;
import org.json.*;import android.content.*;import java.util.*;import java.text.*;import java.lang.reflect.*;
public class TestPrayerData {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static long at(String x)throws Exception{return new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).parse(x).getTime();}
 static JSONObject times(String fajr)throws Exception{return new JSONObject().put("Fajr",fajr).put("Sunrise","06:53").put("Dhuhr","12:50").put("Asr","16:04").put("Maghrib","18:37").put("Isha","19:45");}
 public static void main(String[] args)throws Exception {
  TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  JSONArray days=NativePrayerEngine.days(26.1265,-14.4846,"21","UTC",false,at("2026-12-31 12:00"));
  check(days.length()==7,"week length");check(days.getJSONObject(1).getString("dateKey").equals("2027-01-01"),"year boundary");
  check(days.getJSONObject(0).getJSONObject("times").getString("Fajr").matches("[0-9]{2}:[0-9]{2}"),"valid clocks");
  JSONArray synthetic=new JSONArray().put(new JSONObject().put("dateKey","2026-10-13").put("times",times("05:34"))).put(new JSONObject().put("dateKey","2026-10-14").put("times",times("05:35")));
  check(NativePrayerEngine.next(synthetic,"Fajr",at("2026-10-13 20:00"))==at("2026-10-14 05:35"),"uses tomorrow's own Fajr");
  check(NativePrayerEngine.next(synthetic,"Asr",at("2026-10-13 15:00"))==at("2026-10-13 16:04"),"upcoming today");
  JSONArray utc=NativePrayerEngine.days(21.3891,39.8579,"4","UTC",false,at("2026-06-13 12:00"));JSONArray riyadh=NativePrayerEngine.days(21.3891,39.8579,"4","Asia/Riyadh",false,at("2026-06-13 12:00"));
  long a=at("2026-06-13 "+utc.getJSONObject(0).getJSONObject("times").getString("Dhuhr")),b=at("2026-06-13 "+riyadh.getJSONObject(0).getJSONObject("times").getString("Dhuhr"));check(b-a==3*60*60*1000,"timezone conversion");
  boolean invalid=false;try{NativePrayerEngine.days(100,0,"3","UTC",false,0);}catch(IllegalArgumentException e){invalid=true;}check(invalid,"invalid coordinates rejected");
  Context c=new Context();JSONObject root=new JSONObject().put("method","21").put("timezone","UTC").put("cityId",158).put("city","بوجدور").put("lat",26.1265).put("lon",-14.4846).put("enabled",true).put("dateKey","2000-01-01").put("official",true).put("times",times("01:11"));
  PrayerDayData.refresh(c,root);check(!root.getBoolean("official"),"stale official flag cleared");check(!root.getJSONObject("times").getString("Fajr").equals("01:11"),"yesterday's data not reused");
  String today=root.getString("dateKey");JSONObject official=new JSONObject().put("success",true).put("cityId",158).put("dateKey",today).put("times",times("05:34"));
  Method cache=HabousPrayerClient.class.getDeclaredMethod("cache",Context.class,int.class,JSONObject.class);cache.setAccessible(true);cache.invoke(null,c,158,official);
  PrayerDayData.refresh(c,root);check(root.getBoolean("official")&&root.getJSONObject("times").getString("Fajr").equals("05:34"),"official row preferred");check(HabousPrayerClient.cachedDay(c,158,"2000-01-01")==null,"different date rejected");check(HabousPrayerClient.cachedDay(c,1,today)==null,"different city rejected");
  int dom=Calendar.getInstance().get(Calendar.DAY_OF_MONTH);String html="<table><tr><td>اليوم</td><td>1</td><td>"+dom+"</td><td>05:34</td><td>06:53</td><td>12:50</td><td>16:04</td><td>18:37</td><td>19:45</td></tr></table>";check(HabousPrayerClient.parse(html,"بوجدور",158).getJSONObject("times").getString("Asr").equals("16:04"),"ministry table parser");
  PrayerPrefs.save(c,root);PrayerScheduler.scheduleFromJson(c,root);check(PrayerScheduler.scheduledCount(c)==5,"dated prayers scheduled");int calls=c.alarm.clockCalls;PrayerScheduler.scheduleFromJson(c,root);check(c.alarm.clockCalls==calls,"dated sync idempotent");
  root.put("reminderMinutes",5);PrayerScheduler.scheduleFromJson(c,root);check(c.alarm.scheduled.containsKey(7100),"pre-prayer reminder scheduled");root.put("reminderMinutes",0);PrayerScheduler.scheduleFromJson(c,root);check(!c.alarm.scheduled.containsKey(7100),"pre-prayer reminder disabled");
  System.out.println("Native Adhan, year rollover, tomorrow Fajr, timezone conversion, ministry preference, stale cache rejection and dated scheduling/reminders passed");
 }
}'''
jar=ROOT/'tests/support/json-20240303.jar'
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp)
 for name,text in s.items():f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text)
 native=ROOT/'app/src/main/java/com/wird/app';names=['PrayerAlarmTime.java','PrayerScheduler.java','PrayerDayData.java','PrayerSources.java','PrayerCorrections.java','ImportedPrayerStore.java','PrayerPrefs.java','HabousPrayerClient.java','HabousMonthParser.java','HabousMonthStore.java','NativePrayerEngine.java']
 vendor=list((ROOT/'app/src/main/java/com/batoulapps').rglob('*.java'))
 subprocess.run(['java','com.sun.tools.javac.Main','-cp',str(jar),'-d',str(p/'classes'),*[str(f) for f in p.rglob('*.java')],*[str(native/n) for n in names],*[str(f) for f in vendor]],check=True)
 subprocess.run(['java','-cp',str(p/'classes')+':'+str(jar),'com.wird.app.TestPrayerData'],check=True)
