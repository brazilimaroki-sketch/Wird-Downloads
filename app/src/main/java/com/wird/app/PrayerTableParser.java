package com.wird.app;
import org.json.*;
import java.util.*;

/** Strict user-import format. Explicit ISO dates; no inferred year, city or authority. */
final class PrayerTableParser {
    static JSONObject parse(String text,int cityId)throws Exception {
        text=text.replace("\ufeff","").trim();if(text.length()>1048576)throw new IllegalArgumentException("الملف أكبر من 1 ميغابايت");
        JSONArray input;
        if(text.startsWith("{")) {
            JSONObject doc=new JSONObject(text);if(doc.has("cityId")&&doc.getInt("cityId")!=cityId)throw new IllegalArgumentException("الملف يخص مدينة أخرى");
            if(doc.has("timezone")&&!"UTC".equals(doc.getString("timezone"))&&!"GMT".equals(doc.getString("timezone")))throw new IllegalArgumentException("الملف يجب أن يستعمل توقيت GMT المعتمد في وِرد");
            input=doc.getJSONArray("days");
        }else if(text.startsWith("["))input=new JSONArray(text);
        else input=csv(text,cityId);
        if(input.length()<1||input.length()>366)throw new IllegalArgumentException("الملف يجب أن يحتوي بين يوم و366 يوماً");
        TreeMap<String,JSONObject> dates=new TreeMap<>();
        for(int i=0;i<input.length();i++){
            JSONObject row=input.getJSONObject(i);if(row.has("cityId")&&row.getInt("cityId")!=cityId)throw new IllegalArgumentException("يوجد صف لمدينة أخرى");
            String date=row.optString("dateKey",row.optString("date"));if(!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new IllegalArgumentException("التاريخ مطلوب بصيغة YYYY-MM-DD");HabousMonthParser.date(date);
            JSONObject raw=row.optJSONObject("times");if(raw==null)raw=row;JSONObject times=new JSONObject();
            Iterator<String> keys=raw.keys();while(keys.hasNext()){String field=keys.next(),k=column(field);if(Arrays.asList(HabousMonthParser.KEYS).contains(k)){if(times.has(k))throw new IllegalArgumentException("اسم صلاة مكرر في JSON");times.put(k,raw.getString(field));}}
            if(!HabousMonthParser.validTimes(times))throw new IllegalArgumentException("وقت ناقص أو غير صحيح في "+date);
            for(String k:HabousMonthParser.KEYS){String[] p=times.getString(k).split(":");times.put(k,String.format(Locale.US,"%02d:%02d",Integer.parseInt(p[0]),Integer.parseInt(p[1])));}
            if(dates.containsKey(date))throw new IllegalArgumentException("يوجد تاريخ مكرر: "+date);
            dates.put(date,new JSONObject().put("dateKey",date).put("cityId",cityId).put("times",times).put("source","imported").put("success",true));
        }
        String previous=null;JSONArray rows=new JSONArray();for(Map.Entry<String,JSONObject> e:dates.entrySet()){
            if(previous!=null&&!HabousMonthParser.plus(previous,1).equals(e.getKey()))throw new IllegalArgumentException("أيام الجدول غير متسلسلة");rows.put(e.getValue());previous=e.getKey();}
        String today=HabousMonthParser.today();if(dates.lastKey().compareTo(today)<0)throw new IllegalArgumentException("الجدول انتهى؛ اختر حصة حالية أو قادمة");
        if(dates.firstKey().compareTo(HabousMonthParser.plus(today,740))>0)throw new IllegalArgumentException("تاريخ الجدول بعيد؛ تحقق من السنة");
        return new JSONObject().put("schema",1).put("cityId",cityId).put("timezone","UTC").put("source","imported").put("firstDate",dates.firstKey()).put("lastDate",dates.lastKey()).put("importedAt",System.currentTimeMillis()).put("days",rows);
    }
    private static String column(String text){String k=text.trim().toLowerCase(Locale.ROOT);switch(k){
        case "date":case "datekey":case "التاريخ":return "dateKey";
        case "cityid":case "city_id":return "cityId";
        case "fajr":case "الفجر":case "الصبح":return "Fajr";
        case "sunrise":case "الشروق":return "Sunrise";
        case "dhuhr":case "الظهر":return "Dhuhr";
        case "asr":case "العصر":return "Asr";
        case "maghrib":case "المغرب":return "Maghrib";
        case "isha":case "ishaa":case "العشاء":return "Isha";
        default:return k;}}
    private static JSONArray csv(String text,int cityId)throws Exception {
        String[] lines=text.split("\\r?\\n");if(lines.length<2)throw new IllegalArgumentException("ملف CSV يحتاج سطر عناوين وصفوف مواقيت");
        char separator=lines[0].indexOf(';')>=0?';':lines[0].indexOf('\t')>=0?'\t':',';
        List<String> header=split(lines[0],separator);Set<String> fields=new HashSet<>();for(int i=0;i<header.size();i++){String k=column(header.get(i));if(!fields.add(k))throw new IllegalArgumentException("عنوان عمود مكرر");header.set(i,k);}
        if(!fields.contains("dateKey")||!fields.containsAll(Arrays.asList(HabousMonthParser.KEYS)))throw new IllegalArgumentException("العناوين المطلوبة: date,Fajr,Sunrise,Dhuhr,Asr,Maghrib,Isha");
        JSONArray rows=new JSONArray();for(int i=1;i<lines.length;i++){if(lines[i].trim().isEmpty())continue;List<String> cols=split(lines[i],separator);if(cols.size()!=header.size())throw new IllegalArgumentException("عدد الخانات غير صحيح في السطر "+(i+1));JSONObject row=new JSONObject(),times=new JSONObject();
            for(int j=0;j<header.size();j++){String k=header.get(j),v=cols.get(j);if("dateKey".equals(k))row.put(k,v);else if("cityId".equals(k)){if(Integer.parseInt(v)!=cityId)throw new IllegalArgumentException("الملف يخص مدينة أخرى");}else if(Arrays.asList(HabousMonthParser.KEYS).contains(k))times.put(k,v);}
            rows.put(row.put("times",times));if(rows.length()>366)throw new IllegalArgumentException("عدد الأيام أكبر من 366");}
        return rows;
    }
    private static List<String> split(String line,char delimiter){List<String> out=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false;
        for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='"'){if(quoted&&i+1<line.length()&&line.charAt(i+1)=='"'){cell.append('"');i++;}else quoted=!quoted;}else if(c==delimiter&&!quoted){out.add(cell.toString().trim());cell.setLength(0);}else cell.append(c);}
        if(quoted)throw new IllegalArgumentException("علامة اقتباس غير مغلقة في CSV");out.add(cell.toString().trim());return out;}
    private PrayerTableParser(){}
}
