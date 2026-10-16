package com.wird.app;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
final class HabousCityCatalog {
    static JSONArray all(Context c)throws Exception{try(InputStream in=c.getAssets().open("habous_cities.json")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new JSONArray(out.toString(StandardCharsets.UTF_8.name()));}}
    static String name(Context c,int id)throws Exception{JSONArray cities=all(c);for(int i=0;i<cities.length();i++)if(cities.getJSONObject(i).getInt("id")==id)return cities.getJSONObject(i).getString("name");throw new IllegalArgumentException("Unknown Ministry city");}
    private HabousCityCatalog(){}
}
