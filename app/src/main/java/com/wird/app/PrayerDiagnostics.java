package com.wird.app;
import android.content.Context;
import org.json.*;
final class PrayerDiagnostics {
    static JSONObject snapshot(Context c)throws Exception{
        JSONObject root=PrayerPrefs.load(c),out=new JSONObject();JSONArray alarms=new JSONArray();long now=System.currentTimeMillis();android.content.SharedPreferences state=c.getSharedPreferences("wird_alarm_schedule",0);
        for(int i=0;i<PrayerScheduler.NAMES.length;i++){long at=state.getLong("at_"+i,0);if(at>now)alarms.put(new JSONObject().put("prayer",PrayerScheduler.NAMES[i]).put("at",at).put("when",new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.US).format(new java.util.Date(at))));}
        out.put("alarms",alarms).put("checkedAt",now).put("deviceTimezone",java.util.TimeZone.getDefault().getID()).put("recordedAlarms",true);
        if(root!=null){for(String k:new String[]{"city","cityId","dateKey","method","source","baseTimes","times","corrections","timezone","dataError"})if(root.has(k))out.put(k,root.get(k));}
        String last=c.getSharedPreferences("wird_prayer_diagnostics",0).getString("lastDelivery",null);if(last!=null)out.put("lastDelivery",new JSONObject(last));
        return out;
    }
    static void recordDelivery(Context c,String prayer,long plannedAt){try{long now=System.currentTimeMillis();JSONObject d=new JSONObject().put("prayer",prayer).put("deliveredAt",now).put("scheduledAt",plannedAt);if(plannedAt>0)d.put("delaySeconds",(now-plannedAt)/1000L);c.getSharedPreferences("wird_prayer_diagnostics",0).edit().putString("lastDelivery",d.toString()).apply();}catch(Exception ignored){}}
    private PrayerDiagnostics(){}
}
