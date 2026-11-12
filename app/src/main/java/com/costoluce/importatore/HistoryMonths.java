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
        JSONArray source = new JSONArray(saved); JSONArray out = new JSONArray();
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Europe/Rome"));
        c.setTimeInMillis(reference.getTimeInMillis()); c.set(Calendar.DAY_OF_MONTH,1); c.add(Calendar.MONTH,-3);
        for(int i=0;i<4;i++,c.add(Calendar.MONTH,1)) {
            JSONObject slot = new JSONObject();
            slot.put("year", c.get(Calendar.YEAR)); slot.put("month",c.get(Calendar.MONTH)+1);
            slot.put("available",false); slot.put("partial",i==3);
            for(int j=0;j<source.length();j++) {
                JSONObject r=source.getJSONObject(j);
                if(r.optInt("year")==c.get(Calendar.YEAR) && r.optInt("month")==c.get(Calendar.MONTH)+1
                        && r.optInt("hourSamples")>0 && r.optInt("days")>0
                        && Double.isFinite(r.optDouble("kwh")) && r.optDouble("kwh")>=0) {
                    slot.put("kwh",r.getDouble("kwh")); slot.put("available",true);
                    slot.put("partial",i==3 || r.optInt("days")<r.optInt("expectedDays")
                            || r.optInt("hourSamples")<r.optInt("days")*23);
                    slot.put("lastMeasuredDay",r.optInt("lastMeasuredDay"));
                }
            }
            out.put(slot);
        }
        return out;
    }
}
