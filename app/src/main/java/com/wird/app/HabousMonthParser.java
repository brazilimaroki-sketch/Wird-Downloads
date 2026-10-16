package com.wird.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

/** Parses a whole dated Ministry table. Never assigns today's month to an undated row. */
final class HabousMonthParser {
    static final String[] KEYS={"Fajr","Sunrise","Dhuhr","Asr","Maghrib","Isha"};
    private static final Pattern TIME=Pattern.compile("^([01]?[0-9]|2[0-3]):([0-5][0-9])$");
    private static final String[] MONTHS={"يناير","فبراير","مارس","أبريل|ابريل","ماي|مايو","يونيو|يونية","يوليوز|يوليو|يولية","غشت|أغسطس|اغسطس","شتنبر|سبتمبر","أكتوبر|اكتوبر","نونبر|نوفمبر","دجنبر|ديسمبر"};
    private static final String[] WEEK={"الأحد","الاثنين","الثلاثاء","الأربعاء","الخميس","الجمعة","السبت"};
    static String clean(String s) {return s.replaceAll("(?is)<script[^>]*>.*?</script>|<style[^>]*>.*?</style>"," ")
        .replaceAll("(?is)<[^>]+>"," ").replaceAll("&(?:nbsp|rlm|lrm);|&#160;|[\\u200e\\u200f\\u202a-\\u202e]"," ")
        .replace('٠','0').replace('١','1').replace('٢','2').replace('٣','3').replace('٤','4').replace('٥','5').replace('٦','6').replace('٧','7').replace('٨','8').replace('٩','9')
        .replaceAll("\\s+"," ").trim();}
    static String norm(String s){return clean(s).replaceAll("[\\u064b-\\u065f]","").replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ى','ي').replace('ة','ه').replaceAll("\\s+","");}
    static String key(Calendar c){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setTimeZone(TimeZone.getTimeZone("UTC"));return f.format(c.getTime());}
    static Calendar date(String key)throws Exception{SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setTimeZone(TimeZone.getTimeZone("UTC"));f.setLenient(false);Date d=f.parse(key);if(!f.format(d).equals(key))throw new IllegalArgumentException("Invalid date");Calendar c=Calendar.getInstance(TimeZone.getTimeZone("UTC"));c.setTime(d);return c;}
    static String today(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("UTC"));return key(c);}
    static String plus(String day,int n)throws Exception{Calendar c=date(day);c.add(Calendar.DATE,n);return key(c);}
    static boolean validTimes(JSONObject t){if(t==null)return false;int prev=-1;for(String k:KEYS){String v=t.optString(k);if(!TIME.matcher(v).matches())return false;String[] p=v.split(":");int m=Integer.parseInt(p[0])*60+Integer.parseInt(p[1]);if(m<=prev)return false;prev=m;}return true;}

    static JSONObject parse(String html,String city,int cityId,long now)throws Exception {
        // Verify the selected city, not merely its presence in the full city dropdown.
        Matcher sel=Pattern.compile("(?is)<option\\b([^>]*)>(.*?)</option>").matcher(html);boolean selected=false,matched=false;
        while(sel.find())if(Pattern.compile("(?i)\\bselected\\b").matcher(sel.group(1)).find()){
            selected=true;Matcher v=Pattern.compile("(?i)\\bvalue\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(sel.group(1));
            if(v.find() && (v.group(1).equals(String.valueOf(cityId))||v.group(1).matches(".*[?&]ville="+cityId+"(?:&.*)?")))matched=true;
        }
        if(selected&&!matched)throw new IllegalArgumentException("Wrong selected city");
        if(!selected){Matcher title=Pattern.compile("(?is)مواقيت\\s+الصلاة\\s+لمدينة\\s*(.{1,180}?)<(?:/h|/div|/p|br)").matcher(html);
            if(!title.find()||!norm(clean(title.group(1))).equals(norm(city)))throw new IllegalArgumentException("City not identified");}
        Matcher table=Pattern.compile("(?is)<table\\b[^>]*\\bid\\s*=\\s*['\"]horaire['\"][^>]*>(.*?)</table>").matcher(html);
        String body=table.find()?table.group(1):html;
        List<List<String>> data=new ArrayList<>();Matcher rows=Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr>").matcher(body);
        while(rows.find()) {List<String> cells=new ArrayList<>();Matcher cm=Pattern.compile("(?is)<t[dh]\\b[^>]*>(.*?)</t[dh]>").matcher(rows.group(1));while(cm.find())cells.add(clean(cm.group(1)));if(cells.size()==9&&TIME.matcher(cells.get(3)).matches())data.add(cells);}
        if(data.size()<29||data.size()>30||!data.get(0).get(1).equals("1"))throw new IllegalArgumentException("Incomplete monthly table");
        String heading=clean(body.substring(0,Math.max(0,body.indexOf(data.get(0).get(3))))); // months are in the table header
        List<Integer> months=new ArrayList<>();List<int[]> positions=new ArrayList<>();
        for(int i=0;i<MONTHS.length;i++){Matcher m=Pattern.compile("(?<![\\u0600-\\u06ff])(?:"+MONTHS[i]+")(?![\\u0600-\\u06ff])").matcher(heading);if(m.find())positions.add(new int[]{m.start(),i});}
        positions.sort(Comparator.comparingInt(a->a[0]));for(int[] p:positions)months.add(p[1]);
        if(months.isEmpty()||months.size()>2)throw new IllegalArgumentException("Gregorian month missing");
        // A printed Gregorian year is mandatory. No current-year guess for an old page.
        Matcher ym=Pattern.compile("(?<![0-9])(20[0-9]{2})(?![0-9])").matcher(clean(html.substring(0,Math.max(0,html.indexOf(data.get(0).get(3))))));Set<Integer> years=new HashSet<>();while(ym.find())years.add(Integer.parseInt(ym.group(1)));
        if(years.isEmpty())throw new IllegalArgumentException("Gregorian year missing");
        JSONObject found=null;Calendar reference=Calendar.getInstance(TimeZone.getTimeZone("UTC"));reference.setTimeInMillis(now);
        Set<Integer> candidates=new TreeSet<>();for(int printed:years){candidates.add(printed-1);candidates.add(printed);}
        for(int y:candidates){
            Calendar c=Calendar.getInstance(TimeZone.getTimeZone("UTC"));c.clear();c.setLenient(false);c.set(y,months.get(0),Integer.parseInt(data.get(0).get(2)),12,0);
            JSONArray days=new JSONArray();boolean ok=true;Set<Integer> actualMonths=new LinkedHashSet<>();Set<Integer> actualYears=new HashSet<>();
            try{for(List<String> cells:data){int gd=Integer.parseInt(cells.get(2)),hd=Integer.parseInt(cells.get(1));
                if(c.get(Calendar.DAY_OF_MONTH)!=gd||hd<1||hd>30||!norm(cells.get(0)).equals(norm(WEEK[c.get(Calendar.DAY_OF_WEEK)-1]))) {ok=false;break;}
                if(days.length()>0&&hd!=Integer.parseInt(data.get(days.length()-1).get(1))+1){ok=false;break;}
                actualMonths.add(c.get(Calendar.MONTH));actualYears.add(c.get(Calendar.YEAR));JSONObject t=new JSONObject();for(int i=0;i<6;i++){String[] parts=cells.get(i+3).split(":");t.put(KEYS[i],String.format(Locale.US,"%02d:%02d",Integer.parseInt(parts[0]),Integer.parseInt(parts[1])));}if(!validTimes(t)){ok=false;break;}
                JSONObject day=new JSONObject().put("city",city).put("cityId",cityId).put("dateKey",key(c)).put("times",t).put("success",true).put("source","habous.gov.ma").put("officialTable",true);
                // Hijri month/year must be read, never inferred from Gregorian arithmetic.
                Matcher hm=Pattern.compile("(محرم|صفر|ربيع\\s+(?:الأول|الاول|الآخر|الاخر|الثاني)|جمادى\\s+(?:الأولى|الاولى|الآخرة|الاخره|الثانية)|رجب|شعبان|رمضان|شوال|ذو\\s+(?:القعدة|الحجة))\\s+(?:(?:لعام|عام|لسنة|سنة)\\s+)?(14[0-9]{2})").matcher(clean(html).replaceAll("[\\u064b-\\u065f]", "").replace('ٱ','ا'));
                if(hm.find())day.put("hijri",new JSONObject().put("day",hd).put("month",hm.group(1)).put("year",Integer.parseInt(hm.group(2))).put("weekday",cells.get(0)));
                days.put(day);c.add(Calendar.DATE,1);
            }}catch(Exception e){ok=false;}
            if(!ok||!new ArrayList<>(actualMonths).equals(months)||!years.containsAll(actualYears))continue;
            String first=days.getJSONObject(0).getString("dateKey"),last=days.getJSONObject(days.length()-1).getString("dateKey"),today=key(reference);
            if(last.compareTo(plus(today,-3))<0||first.compareTo(plus(today,35))>0)continue;
            if(found!=null)throw new IllegalArgumentException("Ambiguous month");
            found=new JSONObject().put("schema",1).put("cityId",cityId).put("city",city).put("source","habous.gov.ma").put("timezone","UTC").put("fetchedAt",now).put("firstDate",first).put("lastDate",last).put("days",days);
        }
        if(found==null)throw new IllegalArgumentException("Month/year/weekday mismatch or expired table");return found;
    }
    private HabousMonthParser(){}
}
