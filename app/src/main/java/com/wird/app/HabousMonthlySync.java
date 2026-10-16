package com.wird.app;

import android.app.job.*;
import android.content.*;
import org.json.*;
import java.util.concurrent.TimeUnit;

/** Persisted system job; no service/audio is started to download prayer tables. */
public final class HabousMonthlySync {
    static final int PERIODIC=7580,NOW=7581;
    public static void ensure(Context c){JobScheduler jobs=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);if(jobs==null)return;
        if(jobs.getPendingJob(PERIODIC)==null)jobs.schedule(new JobInfo.Builder(PERIODIC,new ComponentName(c,HabousMonthJobService.class)).setPersisted(true).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(TimeUnit.HOURS.toMillis(24)).build());
        long last=c.getSharedPreferences("wird_month_sync",0).getLong("lastComplete",0);
        if(System.currentTimeMillis()-last>TimeUnit.HOURS.toMillis(23))request(c,false);
    }
    static void request(Context c,boolean force){JobScheduler jobs=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);if(jobs==null)return;
        if(force){jobs.cancel(NOW);c.getSharedPreferences("wird_month_sync",0).edit().clear().apply();}
        else if(jobs.getPendingJob(NOW)!=null)return;
        jobs.schedule(new JobInfo.Builder(NOW,new ComponentName(c,HabousMonthJobService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(force?0:TimeUnit.SECONDS.toMillis(15)).setBackoffCriteria(TimeUnit.MINUTES.toMillis(30),JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
    }
    static void continueBatch(Context c){JobScheduler jobs=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);if(jobs!=null)jobs.schedule(new JobInfo.Builder(NOW,new ComponentName(c,HabousMonthJobService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(TimeUnit.MINUTES.toMillis(1)).setBackoffCriteria(TimeUnit.MINUTES.toMillis(30),JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());}
    static synchronized JSONObject status(Context c,int cityId){JSONObject s=new JSONObject();try{JSONArray all=HabousCityCatalog.all(c);int ready=0,complete=0;String today=HabousMonthParser.today();for(int i=0;i<all.length();i++){int id=all.getJSONObject(i).getInt("id");if(HabousMonthStore.day(c,id,today)!=null)ready++;if(!HabousMonthStore.needsRefresh(c,id,System.currentTimeMillis()))complete++;}
        JSONObject table=HabousMonthStore.load(c,cityId);s.put("total",all.length()).put("ready",ready).put("complete",complete).put("coverageEnd",table.optString("lastDate")).put("fetchedAt",table.optLong("fetchedAt")).put("todayAvailable",HabousMonthStore.day(c,cityId,today)!=null)
        .put("lastAttempt",c.getSharedPreferences("wird_month_sync",0).getLong("lastAttempt",0)).put("error",c.getSharedPreferences("wird_month_sync",0).getString("error",""));
    }catch(Exception ignored){}return s;}
    private HabousMonthlySync(){}
}
