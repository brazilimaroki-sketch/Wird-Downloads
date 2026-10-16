package com.wird.app;
import android.app.job.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Persisted network work in short resumable batches. Never runs an adhan playback service. */
public final class HabousMonthJobService extends JobService {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<Integer,AtomicBoolean> stops=new ConcurrentHashMap<>();
    @Override public boolean onStartJob(JobParameters params){AtomicBoolean stop=new AtomicBoolean(false);stops.put(params.getJobId(),stop);
        executor.execute(()->{HabousMonthBatch.Outcome outcome=HabousMonthBatch.Outcome.RETRY;
            try{outcome=HabousMonthBatch.run(this,stop::get,(city,id)->HabousPrayerClient.fetchAndStoreMonth(this,city,id));}
            catch(Exception ignored){}
            finally{if(!stop.get()){
                stops.remove(params.getJobId(),stop);
                jobFinished(params,outcome==HabousMonthBatch.Outcome.RETRY);
                if(outcome==HabousMonthBatch.Outcome.CONTINUE)HabousMonthlySync.continueBatch(this);
                else if(params.getJobId()==HabousMonthlySync.PERIODIC&&outcome==HabousMonthBatch.Outcome.RETRY)HabousMonthlySync.request(this,false);
            }}
        });return true;}
    @Override public boolean onStopJob(JobParameters params){AtomicBoolean stop=stops.remove(params.getJobId());if(stop!=null)stop.set(true);return true;}
    @Override public void onDestroy(){for(AtomicBoolean stop:stops.values())stop.set(true);executor.shutdownNow();super.onDestroy();}
}
