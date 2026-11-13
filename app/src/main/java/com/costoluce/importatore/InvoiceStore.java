package com.costoluce.importatore;

import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.*;

/** Separate per-supply private storage: neither credentials nor PDFs are copied to widget data. */
final class InvoiceStore {
    private static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("issued_invoices_v1",Context.MODE_PRIVATE);}
    private static String account(String pod)throws Exception{byte[] hash=MessageDigest.getInstance("SHA-256").digest(pod.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:hash)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
    static JSONArray read(Context c){
        try{EnelConnector.Credentials credentials=new EnelCredentialStore(c).read();String key=credentials==null?prefs(c).getString("active",""):account(credentials.pod);return new JSONArray(prefs(c).getString(key,"[]"));}catch(Exception invalid){return new JSONArray();}
    }
    static void save(Context c,IssuedInvoice invoice)throws Exception{
        EnelConnector.Credentials credentials=new EnelCredentialStore(c).read();
        if(credentials!=null&&!credentials.pod.equals(invoice.pod))throw new IllegalArgumentException("La fattura appartiene a un POD diverso da quello collegato. Nessun importo salvato.");
        String key=account(invoice.pod);JSONArray old=new JSONArray(prefs(c).getString(key,"[]")),next=new JSONArray();
        for(int i=0;i<old.length();i++){JSONObject r=old.getJSONObject(i);if(r.getString("id").equals(invoice.id)){if(!r.toString().equals(invoice.json().toString()))throw new IllegalArgumentException("Numero fattura già presente con dati diversi: verifica il documento.");return;}if(r.getString("from").compareTo(invoice.to)<=0&&r.getString("to").compareTo(invoice.from)>=0)throw new IllegalArgumentException("Periodo sovrapposto a una fattura importata: possibile conguaglio, da verificare.");next.put(r);}
        if(next.length()>=120)throw new IllegalArgumentException("Archivio pieno (120 fatture).");next.put(invoice.json());if(!prefs(c).edit().putString(key,next.toString()).putString("active",key).commit())throw new IllegalStateException("Impossibile salvare l'archivio fatture.");
        CostoLuceWidgetProvider.refresh(c);
    }
}
