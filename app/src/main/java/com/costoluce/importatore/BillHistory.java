package com.costoluce.importatore;

import java.util.*;
import org.json.*;

/** Complete configured electricity estimate: each month contributes fixed + variable + taxes. */
final class BillHistory {
    private static final String[] MONTHS={"Gen","Feb","Mar","Apr","Mag","Giu","Lug","Ago","Set","Ott","Nov","Dic"};
    private static JSONArray estimates(JSONArray archive,Calendar reference,int months,int anchor,WidgetPricing pricing)throws JSONException{
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
                if(pricing!=null&&group==0)total+=pricing.estimatedCost(usage,1); // Monthly excise, one fixed fee per begun month.
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
    static JSONArray rows(JSONArray archive,Calendar reference,int months,int anchor,WidgetPricing pricing)throws JSONException{
        return rows(archive,reference,months,anchor,pricing,new JSONArray());
    }
    static JSONArray rows(JSONArray archive,Calendar reference,int months,int anchor,WidgetPricing pricing,JSONArray invoices)throws JSONException{
        JSONArray estimates=estimates(archive,reference,months,anchor,pricing);JSONObject current=estimates.getJSONObject(3);
        java.time.LocalDate boundary=java.time.LocalDate.of(current.getInt("year"),current.getInt("month"),1);
        java.util.List<JSONObject> confirmed=new java.util.ArrayList<>(),all=new java.util.ArrayList<>();
        for(int i=0;i<invoices.length();i++){
            JSONObject invoice=invoices.getJSONObject(i);java.time.LocalDate from=java.time.LocalDate.parse(invoice.getString("from")),to=java.time.LocalDate.parse(invoice.getString("to"));
            if(!to.isBefore(boundary))continue; // An overlapping/current invoice is never mixed into a forecast.
            JSONObject row=new JSONObject();row.put("year",from.getYear()).put("month",from.getMonthValue()).put("endYear",to.getYear()).put("endMonth",to.getMonthValue());
            row.put("label",MONTHS[from.getMonthValue()-1]+(from.getMonthValue()!=to.getMonthValue()||from.getYear()!=to.getYear()?"–"+MONTHS[to.getMonthValue()-1]:""));
            row.put("years",from.getYear()==to.getYear()?""+from.getYear():from.getYear()+"–"+to.getYear());
            row.put("from",from.toString()).put("to",to.toString()).put("available",true).put("partial",false).put("status","issued").put("billEuro",invoice.getLong("electricityCents")/100.0);
            confirmed.add(row);
        }
        for(int i=0;i<3;i++){
            JSONObject row=estimates.getJSONObject(i);java.time.LocalDate from=java.time.LocalDate.of(row.getInt("year"),row.getInt("month"),1),to=java.time.LocalDate.of(row.getInt("endYear"),row.getInt("endMonth"),1);to=to.withDayOfMonth(to.lengthOfMonth());
            boolean overlaps=false;for(JSONObject invoice:confirmed)if(!java.time.LocalDate.parse(invoice.getString("from")).isAfter(to)&&!java.time.LocalDate.parse(invoice.getString("to")).isBefore(from))overlaps=true;
            if(!overlaps){row.put("available",false).put("status","missing");row.remove("billEuro");all.add(row);}
        }
        all.addAll(confirmed);all.sort(java.util.Comparator.comparingInt(r->r.optInt("endYear")*12+r.optInt("endMonth")));
        current.put("status","forecast");all.add(current);JSONArray result=new JSONArray();for(int i=Math.max(0,all.size()-4);i<all.size();i++)result.put(all.get(i));return result;
    }
    static JSONArray monthlyRows(JSONArray history,Calendar today,int months,int anchor,JSONArray invoices)throws JSONException{
        return monthlyRows(history,today,months,anchor,invoices,history);
    }
    static JSONArray monthlyRows(JSONArray history,Calendar today,int months,int anchor,JSONArray invoices,JSONArray archive)throws JSONException{
        BillingPeriod.Window window=BillingPeriod.select(today,months,anchor);int boundary=window.startYear*12+window.startMonth;
        Map<Integer,JSONObject> attributed=new HashMap<>();Set<Integer> conflicts=new HashSet<>();
        for(int i=0;i<invoices.length();i++){
            JSONObject invoice=invoices.getJSONObject(i);java.time.LocalDate end=java.time.LocalDate.parse(invoice.getString("to"));
            if(end.getYear()*12+end.getMonthValue()>=boundary)continue;
            JSONArray parts=InvoiceAllocation.split(invoice,archive);for(int j=0;j<parts.length();j++){JSONObject row=parts.getJSONObject(j);int key=row.getInt("year")*12+row.getInt("month");if(attributed.put(key,row)!=null)conflicts.add(key);}
        }
        JSONArray result=new JSONArray();for(int i=0;i<history.length();i++){
            JSONObject row=new JSONObject(history.getJSONObject(i).toString());int key=row.getInt("year")*12+row.getInt("month");
            if(key>=boundary)row.put("status","forecast");
            else{JSONObject match=conflicts.contains(key)?null:attributed.get(key);if(match!=null){Iterator<String> keys=match.keys();while(keys.hasNext()){String name=keys.next();row.put(name,match.get(name));}}else row.put("available",false).put("status","missing");}
            result.put(row);
        }return result;
    }
    private static String normalize(String name){return java.text.Normalizer.normalize(name.toLowerCase(Locale.ROOT),java.text.Normalizer.Form.NFD).replaceAll("[\\p{M}]","").replaceAll("[^a-z0-9]+"," ").trim();}
    private static int kind(String id,String name){
        if("custom_offer_commercialization".equals(id)||Arrays.asList("commercializzazione","commercializzazione vendita","commercializzazione e vendita","ccv","ccv luce").contains(normalize(name)))return 0;
        if("custom_offer_dispbt".equals(id)||Arrays.asList("dispbt","dispbt parte fissa","componente di dispacciamento dispbt parte fissa","dispacciamento parte fissa","dispacciamento fisso").contains(normalize(name)))return 1;
        if("custom_arera_rete_fissa".equals(id)||Arrays.asList("rete quota fissa","rete fissa","quota fissa rete").contains(normalize(name)))return 2;
        if("custom_arera_rete_potenza".equals(id)||Arrays.asList("rete quota potenza","rete potenza","quota potenza rete").contains(normalize(name)))return 3;
        if("custom_arera_qualita_potenza".equals(id)||Arrays.asList("rete qualita quota potenza","rete qualita potenza","qualita quota potenza").contains(normalize(name)))return 4;
        return -1;
    }
    /** Mirrors the app's recognition warning; this is not a certification of arbitrary contracts. */
    static boolean offerCostsRecognized(String serialized)throws JSONException{
        JSONObject rates=new JSONObject(serialized),meta=rates.optJSONObject("_meta");int[] counts={0,0,0,0,0};boolean conflict=false;
        if(meta!=null){Iterator<String> keys=meta.keys();while(keys.hasNext()){String key=keys.next();JSONObject row=meta.optJSONObject(key);if(row==null||!row.optBoolean("enabled",true))continue;int k=kind(key,row.optString("name",key));if(k>=0){counts[k]++;if(!"fixed".equals(row.optString("type")))conflict=true;}}}
        JSONArray custom=rates.optJSONArray("_custom");if(custom!=null)for(int i=0;i<custom.length();i++){JSONObject row=custom.getJSONObject(i);int k=kind(row.optString("id"),row.optString("name"));if(k>=0){counts[k]++;if(!"fixed".equals(row.optString("type")))conflict=true;}}
        if(conflict)return false;for(int count:counts)if(count!=1)return false;return true;
    }
}
