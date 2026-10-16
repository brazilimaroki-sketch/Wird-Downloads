"""Exercise the real scheduler with an in-memory Android boundary (no device required)."""
from pathlib import Path
import tempfile,subprocess,shutil
ROOT=Path(__file__).resolve().parent.parent
sources={
'android/content/SharedPreferences.java':'''package android.content; import java.util.*; public class SharedPreferences {Map<String,Object> v=new HashMap<>(); public String getString(String k,String d){return (String)v.getOrDefault(k,d);} public long getLong(String k,long d){return ((Number)v.getOrDefault(k,d)).longValue();} public int getInt(String k,int d){return ((Number)v.getOrDefault(k,d)).intValue();} public boolean getBoolean(String k,boolean d){return (Boolean)v.getOrDefault(k,d);}public Editor edit(){return new Editor();} public class Editor {public Editor putBoolean(String k,boolean x){v.put(k,x);return this;}public Editor putString(String k,String x){v.put(k,x);return this;} public Editor putLong(String k,long x){v.put(k,x);return this;} public Editor putInt(String k,int x){v.put(k,x);return this;} public Editor remove(String k){v.remove(k);return this;} public void apply(){}}}''',
'android/content/Context.java':'''package android.content; import java.util.*; public class Context {public static final int MODE_PRIVATE=0; public static final String ALARM_SERVICE="alarm"; public final android.app.AlarmManager alarm=new android.app.AlarmManager(); final Map<String,SharedPreferences> prefs=new HashMap<>(); public Object getSystemService(String k){return alarm;} public SharedPreferences getSharedPreferences(String k,int m){return prefs.computeIfAbsent(k,x->new SharedPreferences());}}''',
'android/content/Intent.java':'''package android.content; import java.util.*; public class Intent {public Class<?> target; public Map<String,Object> extras=new HashMap<>(); public Intent(Context c,Class<?> cls){target=cls;} public Intent putExtra(String k,Object v){extras.put(k,v);return this;}}''',
'android/app/PendingIntent.java':'''package android.app; import android.content.*;import java.util.*; public class PendingIntent {public static final int FLAG_UPDATE_CURRENT=1,FLAG_IMMUTABLE=2,FLAG_NO_CREATE=4; static Map<String,PendingIntent> all=new HashMap<>(); public Intent intent;public int code; public static PendingIntent getBroadcast(Context c,int code,Intent i,int flags){String key=i.target.getName()+code; PendingIntent p=all.get(key); if((flags&4)!=0)return p;if(p==null){p=new PendingIntent();all.put(key,p);}p.intent=i;p.code=code;return p;} public static PendingIntent getActivity(Context c,int code,Intent i,int flags){return getBroadcast(c,code,i,flags);}}''',
'android/app/AlarmManager.java':'''package android.app;import java.util.*;public class AlarmManager {public static final int RTC_WAKEUP=0;public boolean exact=true;public int clockCalls,inexactCalls,cancelCalls;public Map<Integer,Long> scheduled=new HashMap<>();public boolean canScheduleExactAlarms(){return exact;}public void setAndAllowWhileIdle(int t,long at,PendingIntent p){inexactCalls++;scheduled.put(p.code,at);}public void setAlarmClock(AlarmClockInfo info,PendingIntent p){clockCalls++;scheduled.put(p.code,info.at);}public void cancel(PendingIntent p){cancelCalls++;scheduled.remove(p.code);}public static class AlarmClockInfo {long at;public AlarmClockInfo(long t,PendingIntent p){at=t;}}}''',
'android/os/Build.java':'''package android.os;public class Build {public static class VERSION {public static int SDK_INT=36;}}''',
'android/util/Log.java':'''package android.util;public class Log {public static int e(String t,String m,Throwable e){return 0;}}''',
'org/json/JSONObject.java':'''package org.json;import java.util.*;public class JSONObject {Map<String,Object> v=new HashMap<>(); public JSONObject put(String k,Object x){v.put(k,x);return this;} public int optInt(String k,int d){return ((Number)v.getOrDefault(k,d)).intValue();}public org.json.JSONArray optJSONArray(String k){return null;}public boolean optBoolean(String k,boolean d){return (Boolean)v.getOrDefault(k,d);}public String optString(String k,String d){return (String)v.getOrDefault(k,d);}public JSONObject optJSONObject(String k){return (JSONObject)v.get(k);}public JSONObject getJSONObject(String k){if(!v.containsKey(k))throw new IllegalArgumentException();return (JSONObject)v.get(k);}}''',
 'org/json/JSONArray.java':'package org.json;public class JSONArray {}',
 'com/wird/app/NativePrayerEngine.java':'package com.wird.app;public class NativePrayerEngine {public static long next(org.json.JSONArray a,String p,long n){throw new AssertionError("Unexpected day data in legacy fixture");}}',
 'com/wird/app/PrayerDayData.java':'package com.wird.app;public class PrayerDayData {public static org.json.JSONObject refresh(android.content.Context c,org.json.JSONObject o){return o;}}',
'com/wird/app/MainActivity.java':'package com.wird.app; public class MainActivity {}',
'com/wird/app/PrayerAlarmReceiver.java':'package com.wird.app; public class PrayerAlarmReceiver {}',
'com/wird/app/PrayerPrefs.java':'package com.wird.app; public class PrayerPrefs {static org.json.JSONObject saved; public static org.json.JSONObject load(android.content.Context c){return saved;}public static void save(android.content.Context c,org.json.JSONObject o){saved=o;}}',
'com/wird/app/TestPrayerAlarm.java':'''package com.wird.app;
import android.content.*;import org.json.*;import java.util.*;
public class TestPrayerAlarm {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static long utc(int y,int m,int d,int h,int min,int sec){Calendar c=Calendar.getInstance();c.clear();c.set(y,m-1,d,h,min,sec);return c.getTimeInMillis();}
 public static void main(String[] args){
  TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  check(PrayerAlarmTime.nextTrigger("00:01",utc(2026,12,31,23,59,50))==utc(2027,1,1,0,1,0),"year rollover");
  check(PrayerAlarmTime.nextTrigger("05:34",utc(2026,10,13,5,33,59))==utc(2026,10,13,5,34,0),"upcoming minute");
  check(PrayerAlarmTime.nextTrigger("05:34",utc(2026,10,13,5,34,0))==utc(2026,10,14,5,34,0),"no past triggers");
  check(PrayerAlarmTime.nextTrigger("05:34",utc(2028,2,28,6,0,0))==utc(2028,2,29,5,34,0),"leap day");
  for(String bad:new String[]{"24:00","12:60","-1:00","","12:xx","12:34 extra"}){boolean rejected=false;try{PrayerAlarmTime.nextTrigger(bad,0);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"reject "+bad);}
  Context c=new Context();JSONObject times=new JSONObject();for(String name:PrayerScheduler.NAMES)times.put(name,"23:59");
  JSONObject prayers=new JSONObject();JSONObject root=new JSONObject().put("enabled",true).put("times",times).put("prayers",prayers).put("city","بوجدور");PrayerPrefs.saved=root;
  c.alarm.exact=false;PrayerScheduler.scheduleFromJson(c,root);check(PrayerScheduler.scheduledCount(c)==0 && c.alarm.clockCalls==0,"permission denied");check(!PrayerScheduler.scheduleError(c).isEmpty(),"actionable error");
  c.alarm.exact=true;PrayerScheduler.rescheduleSaved(c,true);check(c.alarm.clockCalls==5 && PrayerScheduler.scheduledCount(c)==5,"grant restoration");
  PrayerScheduler.scheduleFromJson(c,root);check(c.alarm.clockCalls==5,"repeated sync is idempotent");
  c.getSharedPreferences("wird_alarm_schedule",0).edit().putLong("at_0",System.currentTimeMillis()-1).apply();PrayerScheduler.scheduleFromJson(c,root);check(c.alarm.clockCalls==5,"sync does not cancel due delivery");
  PrayerScheduler.markDelivered(c,0);PrayerScheduler.rescheduleSaved(c);check(c.alarm.clockCalls==6,"delivered alarm rescheduled");
  prayers.put("Dhuhr",false);PrayerScheduler.scheduleFromJson(c,root);check(PrayerScheduler.scheduledCount(c)==4 && !c.alarm.scheduled.containsKey(7001),"disabled prayer cancelled");
  root.put("city","مدينة أخرى");PrayerScheduler.scheduleFromJson(c,root);check(c.alarm.clockCalls==10,"city change reapplied");PrayerScheduler.rescheduleSaved(c,true);check(c.alarm.clockCalls==14,"reboot forces restoration");
  long before=System.currentTimeMillis();check(PrayerScheduler.scheduleTest(c),"background test accepted");long test=c.alarm.scheduled.get(7099);check(test>=before+60000 && test<=System.currentTimeMillis()+60000,"one minute test");
  times.put("Asr","24:90");PrayerScheduler.scheduleFromJson(c,root);check(PrayerScheduler.scheduledCount(c)==3 && !PrayerScheduler.scheduleError(c).isEmpty(),"malformed time surfaced");
  times.put("Asr","23:59");c.alarm.exact=false;root.put("alertMode","notification");PrayerScheduler.scheduleFromJson(c,root);check(c.alarm.inexactCalls==4,"notification fallback and permission mode update");
  root.put("enabled",false);PrayerScheduler.scheduleFromJson(c,root);check(PrayerScheduler.scheduledCount(c)==0,"master switch cancels all prayers");
  System.out.println("Prayer alarm timing, permission gating, restoration, due-minute race, disabled prayers and 60s test passed");
 }
}'''
}
def run_test(test_sources, entrypoint="com.wird.app.TestPrayerAlarm", production=("PrayerAlarmTime.java", "PrayerScheduler.java")):
 with tempfile.TemporaryDirectory() as tmp:
  p=Path(tmp)
  for name,code in test_sources.items():f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(code)
  for name in production:
   (p/'com/wird/app'/name).write_text((ROOT/'app/src/main/java/com/wird/app'/name).read_text())
  compiler=['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
  subprocess.run([*compiler,'-d',str(p/'classes'),*[str(f) for f in p.rglob('*.java')]],check=True)
  subprocess.run(['java','-cp',str(p/'classes'),entrypoint],check=True)
 
if __name__=="__main__":run_test(sources)
