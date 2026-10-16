"""Android boundary doubles shared by native tests; production algorithms are compiled unchanged."""
from pathlib import Path
import tempfile,subprocess
from prayer_alarm_test import ROOT,sources
s={k:v for k,v in sources.items() if k.startswith('android/')}
s['android/content/SharedPreferences.java']=s['android/content/SharedPreferences.java'].replace('public void apply(){}','public boolean commit(){return true;}public Editor clear(){v.clear();return this;}public void apply(){}')
s['android/content/Context.java']=s['android/content/Context.java'].replace('public static final int MODE_PRIVATE=0;', 'public static final int MODE_PRIVATE=0;public static final String JOB_SCHEDULER_SERVICE="job";public final android.app.job.JobScheduler jobs=new android.app.job.JobScheduler();public android.content.res.AssetManager getAssets(){return new android.content.res.AssetManager();} public Context getApplicationContext(){return this;}').replace('return alarm;','return k.equals("job")?jobs:alarm;')
s['android/content/res/AssetManager.java']='package android.content.res;import java.io.*;public class AssetManager{public InputStream open(String n)throws Exception{return new FileInputStream(System.getProperty("assets")+"/"+n);}}'
s['android/content/ComponentName.java']='package android.content;public class ComponentName{public ComponentName(Context c,Class<?> cls){}}'
s['android/app/job/JobInfo.java']='''package android.app.job;import android.content.*;public class JobInfo{public static final int NETWORK_TYPE_ANY=1,BACKOFF_POLICY_EXPONENTIAL=1;public int id,network;public boolean persisted;public long period;public static class Builder{JobInfo j=new JobInfo();public Builder(int id,ComponentName c){j.id=id;}public Builder setPersisted(boolean p){j.persisted=p;return this;}public Builder setRequiredNetworkType(int n){j.network=n;return this;}public Builder setPeriodic(long p){j.period=p;return this;}public Builder setMinimumLatency(long x){return this;}public Builder setBackoffCriteria(long x,int p){return this;}public JobInfo build(){return j;}}}'''
s['android/app/job/JobScheduler.java']='package android.app.job;import java.util.*;public class JobScheduler{public Map<Integer,JobInfo> jobs=new HashMap<>();public void cancel(int id){jobs.remove(id);}public JobInfo getPendingJob(int id){return jobs.get(id);}public int schedule(JobInfo j){jobs.put(j.id,j);return 1;}}'
s['android/app/job/JobParameters.java']='package android.app.job;public class JobParameters{int id;public JobParameters(int n){id=n;}public int getJobId(){return id;}}'
s['android/app/job/JobService.java']='package android.app.job;public class JobService extends android.content.Context{public boolean onStartJob(JobParameters p){return false;}public boolean onStopJob(JobParameters p){return false;}public void onDestroy(){}public void jobFinished(JobParameters p,boolean retry){}}'
s['com/wird/app/MainActivity.java']='package com.wird.app;public class MainActivity{}'
s['com/wird/app/PrayerAlarmReceiver.java']='package com.wird.app;public class PrayerAlarmReceiver{}'
s['com/wird/app/AdhanCatalog.java']='package com.wird.app;public class AdhanCatalog{public static int resource(String s,boolean f){return 1;}}'
s['com/wird/app/PrayerWidgetProvider.java']='package com.wird.app;import android.content.Context;public class PrayerWidgetProvider{public static void updateAll(Context c){}}'
