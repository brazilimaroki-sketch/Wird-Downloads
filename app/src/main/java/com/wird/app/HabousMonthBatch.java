package com.wird.app;
import android.content.Context;
import org.json.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Resumable unit of work, separate from Android's service lifecycle for deterministic tests. */
final class HabousMonthBatch {
    interface Fetcher {void fetch(String city,int id)throws Exception;}
    enum Outcome {DONE,CONTINUE,RETRY}
    static Outcome run(Context c,BooleanSupplier stopped,Fetcher fetcher)throws Exception {
        JSONArray cities=HabousCityCatalog.all(c);LinkedHashMap<Integer,String> queue=new LinkedHashMap<>();JSONObject root=PrayerPrefs.load(c);int selected=root==null?0:root.optInt("cityId");
        if(selected>0)try{queue.put(selected,HabousCityCatalog.name(c,selected));}catch(Exception ignored){}
        int cursor=c.getSharedPreferences("wird_month_sync",0).getInt("cursor",0);
        for(int n=0;n<cities.length();n++){JSONObject city=cities.getJSONObject((cursor+n)%cities.length());queue.put(city.getInt("id"),city.getString("name"));}
        long end=System.currentTimeMillis()+TimeUnit.MINUTES.toMillis(3);int count=0,failures=0;boolean waiting=false;
        for(Map.Entry<Integer,String> city:queue.entrySet()) {
            if(stopped.getAsBoolean())return Outcome.RETRY;int id=city.getKey();if(!HabousMonthStore.needsRefresh(c,id,System.currentTimeMillis()))continue;
            long tried=c.getSharedPreferences("wird_month_sync",0).getLong("try_"+id,0);
            if(System.currentTimeMillis()-tried<TimeUnit.HOURS.toMillis(12)){waiting=true;continue;}
            if(failures>=2)return Outcome.RETRY;
            if(count>=24||System.currentTimeMillis()>end)return failures>0?Outcome.RETRY:Outcome.CONTINUE;
            c.getSharedPreferences("wird_month_sync",0).edit().putLong("lastAttempt",System.currentTimeMillis()).putLong("try_"+id,System.currentTimeMillis()).apply();
            try{fetcher.fetch(city.getValue(),id);c.getSharedPreferences("wird_month_sync",0).edit().putString("error","").apply();
                if(id==selected){PrayerScheduler.rescheduleSaved(c,true);PrayerWidgetProvider.updateAll(c);}
            }catch(Exception e){failures++;c.getSharedPreferences("wird_month_sync",0).edit().putString("error","official_source_unavailable").apply();}
            count++;for(int n=0;n<cities.length();n++)if(cities.getJSONObject(n).getInt("id")==id){c.getSharedPreferences("wird_month_sync",0).edit().putInt("cursor",(n+1)%cities.length()).apply();break;}
        }
        if(failures>0||waiting)return Outcome.RETRY;
        c.getSharedPreferences("wird_month_sync",0).edit().putLong("lastComplete",System.currentTimeMillis()).apply();return Outcome.DONE;
    }
    private HabousMonthBatch(){}
}
