package com.costoluce.importatore;

import java.util.*;
import java.text.SimpleDateFormat;
import org.json.*;

/** Calendar-month history is independent from the billing-cycle summary. */
final class HistoryMonths {
    static String from(Calendar reference) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Europe/Rome"));
        c.setTimeInMillis(reference.getTimeInMillis());
        c.set(Calendar.DAY_OF_MONTH, 1); c.add(Calendar.MONTH, -3);
        SimpleDateFormat f = new SimpleDateFormat("ddMMyyyy", Locale.ITALY);
        f.setTimeZone(c.getTimeZone()); return f.format(c.getTime());
    }
    static String from(Calendar reference,int months,int anchor) {
        BillingPeriod.Window window=BillingPeriod.select(reference,months,anchor);
        Calendar first=Calendar.getInstance(TimeZone.getTimeZone("Europe/Rome"));first.clear();first.set(window.startYear,window.startMonth-1,1);first.add(Calendar.MONTH,-3*months);
        Calendar annual=Calendar.getInstance(first.getTimeZone());annual.setTimeInMillis(reference.getTimeInMillis());annual.set(Calendar.DAY_OF_MONTH,1);annual.add(Calendar.MONTH,-12);
        BillingPeriod.Window annualGroup=BillingPeriod.select(annual,months,anchor);
        first.clear();first.set(annualGroup.startYear,annualGroup.startMonth-1,1);
        SimpleDateFormat f=new SimpleDateFormat("ddMMyyyy",Locale.ITALY);f.setTimeZone(first.getTimeZone());return f.format(first.getTime());
    }
    static JSONArray serialize(List<EnelConnector.MonthUsage> months) throws JSONException {
        JSONArray rows = new JSONArray();
        for (EnelConnector.MonthUsage m : months) {
            JSONObject r = new JSONObject();
            r.put("year",m.year); r.put("month",m.month); r.put("kwh",m.kwh);
            r.put("days",m.days); r.put("expectedDays",m.expectedDays);
            r.put("hourSamples",m.hourSamples); r.put("lastMeasuredDay",m.lastMeasuredDay);
            rows.put(r);
        }
        return rows;
    }
    /** Always four slots for TODAY: old snapshots never become new-month readings. */
    static JSONArray slots(String saved, Calendar reference) throws JSONException {
        return slots(saved,reference,4);
    }
    static JSONArray slots(String saved, Calendar reference,int count) throws JSONException {
        if(count<1||count>24)throw new JSONException("Storico non valido");
        JSONArray source = new JSONArray(saved); JSONArray out = new JSONArray();
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Europe/Rome"));
        c.setTimeInMillis(reference.getTimeInMillis()); c.set(Calendar.DAY_OF_MONTH,1); c.add(Calendar.MONTH,1-count);
        for(int i=0;i<count;i++,c.add(Calendar.MONTH,1)) {
            JSONObject slot = new JSONObject();
            slot.put("year", c.get(Calendar.YEAR)); slot.put("month",c.get(Calendar.MONTH)+1);
            slot.put("available",false); slot.put("partial",i==count-1);
            for(int j=0;j<source.length();j++) {
                JSONObject r=source.getJSONObject(j);
                if(r.optInt("year")==c.get(Calendar.YEAR) && r.optInt("month")==c.get(Calendar.MONTH)+1
                        && r.optInt("hourSamples")>0 && r.optInt("days")>0
                        && Double.isFinite(r.optDouble("kwh")) && r.optDouble("kwh")>=0) {
                    slot.put("kwh",r.getDouble("kwh")); slot.put("available",true);
                    slot.put("partial",i==count-1 || r.optInt("days")<c.getActualMaximum(Calendar.DAY_OF_MONTH)
                            || r.optInt("hourSamples")<r.optInt("days")*23);
                    slot.put("lastMeasuredDay",r.optInt("lastMeasuredDay"));
                }
            }
            out.put(slot);
        }
        return out;
    }
    static JSONArray merge(String saved,JSONArray incoming)throws JSONException {
        java.util.TreeMap<Integer,JSONObject> map=new java.util.TreeMap<>();
        for(JSONArray rows:new JSONArray[]{new JSONArray(saved),incoming})for(int i=0;i<rows.length();i++){
            JSONObject r=rows.getJSONObject(i);if(r.optInt("hourSamples")>0&&r.optInt("days")>0)map.put(r.optInt("year")*100+r.optInt("month"),r);
        }
        while(map.size()>24)map.pollFirstEntry();JSONArray result=new JSONArray();for(JSONObject row:map.values())result.put(row);return result;
    }
    static int archiveCount(String saved,Calendar reference)throws JSONException{
        int current=reference.get(Calendar.YEAR)*12+reference.get(Calendar.MONTH),count=13;
        JSONArray rows=new JSONArray(saved);for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(r.optInt("hourSamples")>0&&r.optInt("days")>0)count=Math.max(count,current-(r.optInt("year")*12+r.optInt("month")-1)+1);}
        return Math.min(24,count);
    }
}
