package com.costoluce.importatore;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.*;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.util.Calendar;
import java.util.TimeZone;

/** Public, unauthenticated tariff catalogue. Checking never modifies the user's rates. */
final class AreraRepository {
    static final long DAY=24*60*60*1000L;
    static String source(int year){return "https://www.arera.it/fileadmin/area_operatori/prezzi_e_tariffe/Corrispettivi_libero_elettrico_domestico_"+year+".xlsx";}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("costoluce_arera_catalog_v1",Context.MODE_PRIVATE);}
    static boolean due(Context c){long delta=System.currentTimeMillis()-prefs(c).getLong("attempt",0);return delta<0||delta>=DAY;}
    static JSONObject state(Context c,String error){
        JSONObject result=new JSONObject();SharedPreferences p=prefs(c);
        try{result.put("months",new JSONObject(p.getString("catalog","{}")));result.put("checkedAt",p.getLong("checked",0));result.put("error",error);}
        catch(Exception ignored){}return result;
    }
    static JSONObject refresh(Context c){
        long now=System.currentTimeMillis();prefs(c).edit().putLong("attempt",now).apply();
        int year=Calendar.getInstance(TimeZone.getTimeZone("Europe/Rome")).get(Calendar.YEAR);
        try{
            JSONObject data=AreraTable.parse(download(source(year)),year);
            // Keep older downloaded years at January's rollover, without substituting a past year for a missing current one.
            JSONObject merged=new JSONObject(prefs(c).getString("catalog","{}"));
            java.util.Iterator<String> keys=data.keys();while(keys.hasNext()){String k=keys.next();merged.put(k,data.get(k));}
            prefs(c).edit().putString("catalog",merged.toString()).putLong("checked",now).apply();
            return state(c,"");
        }catch(Exception e){
            // Public source only: this diagnostic never contains account information.
            android.util.Log.w("CostoLuceARERA","Public catalogue year="+year+": "+e.getClass().getSimpleName()+": "+e.getMessage());
            return state(c,"Controllo ARERA non riuscito: conservati gli ultimi dati scaricati. Nessuna tariffa modificata.");
        }
    }
    private static byte[] download(String address)throws Exception{
        HttpsURLConnection connection=(HttpsURLConnection)new URL(address).openConnection();
        connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android) CostoLuce/6.7.0");
        connection.setRequestProperty("Accept","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/octet-stream;q=0.9");
        try{
            int status=connection.getResponseCode();
            if(status!=200)throw new IOException("Fonte pubblica HTTP "+status);
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1){if(out.size()+n>2_000_000)throw new IOException("File troppo grande");out.write(buffer,0,n);}return out.toByteArray();
            }
        }finally{connection.disconnect();}
    }
}
