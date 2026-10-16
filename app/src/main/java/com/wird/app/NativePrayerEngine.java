package com.wird.app;

import com.batoulapps.adhan.CalculationMethod;
import com.batoulapps.adhan.CalculationParameters;
import com.batoulapps.adhan.Coordinates;
import com.batoulapps.adhan.Madhab;
import com.batoulapps.adhan.PrayerAdjustments;
import com.batoulapps.adhan.PrayerTimes;
import com.batoulapps.adhan.data.DateComponents;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** MIT Adhan engine. Calculated results are explicitly distinct from ministry tables. */
public final class NativePrayerEngine {
    public static JSONArray days(double lat, double lon, String method, String zone, boolean hanafi, long now) throws Exception {
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat)>90 || Math.abs(lon)>180)
            throw new IllegalArgumentException("Invalid coordinates");
        TimeZone tz=TimeZone.getTimeZone(zone);
        Calendar date=Calendar.getInstance(tz); date.setTimeInMillis(now);
        SimpleDateFormat key=new SimpleDateFormat("yyyy-MM-dd",Locale.US); key.setTimeZone(tz);
        SimpleDateFormat time=new SimpleDateFormat("HH:mm",Locale.US); time.setTimeZone(tz);
        JSONArray result=new JSONArray();
        for (int i=0;i<7;i++) {
            CalculationParameters params;
            switch(method) {
                case "4": params=CalculationMethod.UMM_AL_QURA.getParameters();break;
                case "5": params=CalculationMethod.EGYPTIAN.getParameters();break;
                case "13": params=new CalculationParameters(18.0,17.0);break;
                case "21": params=new CalculationParameters(19.0,17.0);
                    params.adjustments=new PrayerAdjustments(0,-2,5,0,3,-1);break;
                default: params=CalculationMethod.MUSLIM_WORLD_LEAGUE.getParameters();
            }
            params.madhab=hanafi?Madhab.HANAFI:Madhab.SHAFI;
            PrayerTimes p=new PrayerTimes(new Coordinates(lat,lon),new DateComponents(
                    date.get(Calendar.YEAR),date.get(Calendar.MONTH)+1,date.get(Calendar.DAY_OF_MONTH)),params);
            Date[] values={p.fajr,p.sunrise,p.dhuhr,p.asr,p.maghrib,p.isha};
            String[] names={"Fajr","Sunrise","Dhuhr","Asr","Maghrib","Isha"};
            JSONObject times=new JSONObject();
            for(int j=0;j<names.length;j++) {
                if(values[j]==null)throw new IllegalStateException("Unavailable solar event");
                times.put(names[j],time.format(values[j]));
            }
            result.put(new JSONObject().put("dateKey",key.format(date.getTime())).put("times",times).put("source","calculated"));
            date.add(Calendar.DAY_OF_YEAR,1);
        }
        return result;
    }
    public static long next(JSONArray days,String prayer,long now) throws Exception {
        long best=Long.MAX_VALUE;
        for(int i=0;i<days.length();i++) {
            JSONObject day=days.getJSONObject(i);
            String hhmm=day.getJSONObject("times").optString(prayer,"");
            if(!hhmm.matches("[0-9]{2}:[0-9]{2}"))continue;
            SimpleDateFormat parser=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US);
            parser.setLenient(false); // Same displayed wall-clock contract as existing builds.
            Date at=parser.parse(day.getString("dateKey")+" "+hhmm);
            if(at!=null && at.getTime()>now && at.getTime()<best)best=at.getTime();
        }
        if(best==Long.MAX_VALUE)throw new IllegalArgumentException("No future prayer time");
        return best;
    }
    private NativePrayerEngine() {}
}
