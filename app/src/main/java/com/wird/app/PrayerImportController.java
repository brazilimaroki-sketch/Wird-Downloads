package com.wird.app;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import androidx.appcompat.app.AlertDialog;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Uses Android's document picker; no broad storage permission and no network imports. */
final class PrayerImportController {
    static final int REQUEST=9081;
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    static void choose(MainActivity a,int cityId){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/csv","text/comma-separated-values","text/plain","application/json","application/octet-stream"});a.startActivityForResult(i,REQUEST);}
    static void handle(MainActivity a,Uri uri,int cityId){IO.execute(()->{try{
        String city=HabousCityCatalog.name(a,cityId);ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=a.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("تعذر فتح الملف");byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1){if(out.size()+n>1048576)throw new IOException("الملف أكبر من 1 ميغابايت");out.write(bytes,0,n);}}
        JSONObject table=PrayerTableParser.parse(out.toString(StandardCharsets.UTF_8.name()),cityId);
        JSONObject saved=PrayerPrefs.load(a);String label=saved!=null&&saved.optInt("cityId")==cityId?saved.optString("city"):city+"، المغرب";
        PrayerCorrections.applyDays(table.getJSONArray("days"),PrayerCorrections.get(a,cityId,label));
        String preview="المدينة: "+city+"\nالتوقيت: GMT\nالأيام: "+table.getJSONArray("days").length()+"\nمن "+table.getString("firstDate")+" إلى "+table.getString("lastDate")+"\n\nهذا جدول مستورد من اختيارك، ولا يتحقق وِرد من كونه صادراً عن الوزارة. سيُستعمل لأيامه بدل المصدر التلقائي. يمكن حذفه والعودة للمصدر التلقائي.";
        a.runOnUiThread(()->{if(a.isFinishing()||a.isDestroyed())return;new AlertDialog.Builder(a).setTitle("معاينة جدول المواقيت").setMessage(preview).setNegativeButton("إلغاء",null).setPositiveButton("اعتماد الجدول",(dialog,which)->IO.execute(()->{try{ImportedPrayerStore.save(a,table);refresh(a);a.prayerToolsChanged("تم اعتماد الجدول المستورد");}catch(Exception e){a.prayerToolsChanged(e.getMessage());}})).show();});
    }catch(Exception e){a.prayerToolsChanged("تعذر الاستيراد: "+(e.getMessage()==null?"ملف غير صالح":e.getMessage()));}});}
    static void refresh(MainActivity a){PrayerScheduler.rescheduleSaved(a,true);PrayerWidgetProvider.updateAll(a);}
    private PrayerImportController(){}
}
