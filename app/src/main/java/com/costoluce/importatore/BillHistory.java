package com.costoluce.importatore;

import java.util.*;
import org.json.*;

/** Complete configured electricity estimate: each month contributes fixed + variable + taxes. */
final class BillHistory {
    private static final String[] MONTHS={"Gen","Feb","Mar","Apr","Mag","Giu","Lug","Ago","Set","Ott","Nov","Dic"};
    static JSONArray rows(JSONArray archive,Calendar reference,int months,int anchor,WidgetPricing pricing)throws JSONException{
        BillingPeriod.Window window=BillingPeriod.select(reference,months,anchor);
        int first=window.startYear*12+window.startMonth-1,current=window.currentYear*12+window.currentMonth-1;
        JSONArray result=new JSONArray();
        for(int group=3;group>=0;group--){
            int start=first-group*months,end=start+months-1,active=0;double total=0,kwh=0;boolean available=pricing!=null,partial=false;
            for(int index=start;index<=end;index++){
                if(index>current){partial=true;continue;}active++;
                JSONObject measured=null;
                for(int i=0;i<archive.length();i++){JSONObject r=archive.optJSONObject(i);if(r!=null&&r.optInt("year")==index/12&&r.optInt("month")==index%12+1){measured=r;break;}}
                if(measured==null||!measured.optBoolean("available")||!Double.isFinite(measured.optDouble("kwh"))||measured.optDouble("kwh")<0){available=false;continue;}
                double usage=measured.optDouble("kwh");kwh+=usage;
                if(pricing!=null)total+=pricing.estimatedCost(usage,1); // Monthly excise, one fixed fee per begun month.
                partial|=measured.optBoolean("partial")||index==current;
            }
            JSONObject row=new JSONObject();row.put("year",start/12);row.put("month",start%12+1);row.put("endYear",end/12);row.put("endMonth",end%12+1);
            row.put("label",MONTHS[start%12]+(months>1?"–"+MONTHS[end%12]:""));
            row.put("years",start/12==end/12?Integer.toString(start/12):start/12+"–"+end/12);
            row.put("kwh",kwh);row.put("available",available&&active>0);row.put("partial",partial);
            if(pricing!=null)row.put("billEuro",Math.round((total+Math.ulp(1.0))*100)/100.0);
            result.put(row);
        }
        return result;
    }
    private static String normalize(String name){return java.text.Normalizer.normalize(name.toLowerCase(Locale.ROOT),java.text.Normalizer.Form.NFD).replaceAll("[\\p{M}]","").replaceAll("[^a-z0-9]+"," ").trim();}
    private static int kind(String id,String name){
        if("custom_offer_commercialization".equals(id)||Arrays.asList("commercializzazione","commercializzazione vendita","commercializzazione e vendita","ccv","ccv luce").contains(normalize(name)))return 0;
        if("custom_offer_dispbt".equals(id)||Arrays.asList("dispbt","dispbt parte fissa","componente di dispacciamento dispbt parte fissa","dispacciamento parte fissa","dispacciamento fisso").contains(normalize(name)))return 1;
        return -1;
    }
    /** Mirrors the app's recognition warning; this is not a certification of arbitrary contracts. */
    static boolean offerCostsRecognized(String serialized)throws JSONException{
        JSONObject rates=new JSONObject(serialized),meta=rates.optJSONObject("_meta");int[] counts={0,0};boolean conflict=false;
        if(meta!=null){Iterator<String> keys=meta.keys();while(keys.hasNext()){String key=keys.next();JSONObject row=meta.optJSONObject(key);if(row==null||!row.optBoolean("enabled",true))continue;int k=kind(key,row.optString("name",key));if(k>=0){counts[k]++;if(!"fixed".equals(row.optString("type")))conflict=true;}}}
        JSONArray custom=rates.optJSONArray("_custom");if(custom!=null)for(int i=0;i<custom.length();i++){JSONObject row=custom.getJSONObject(i);int k=kind(row.optString("id"),row.optString("name"));if(k>=0){counts[k]++;if(!"fixed".equals(row.optString("type")))conflict=true;}}
        return !conflict&&counts[0]==1&&counts[1]==1;
    }
}
