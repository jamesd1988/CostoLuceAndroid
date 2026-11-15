package com.costoluce.importatore;

import java.time.LocalDate;
import java.util.*;
import org.json.*;

/** Native counterpart of projection.js, evaluated from cached real days even with the app closed. */
final class WidgetProjection {
    static final class Result {
        final boolean available;final String reason;final double value,kwh,observedKwh,average;final int sampleDays;final LocalDate from,to;
        Result(String reason){this.available=false;this.reason=reason;value=kwh=observedKwh=average=Double.NaN;sampleDays=0;from=to=null;}
        Result(double value,double kwh,double observed,double average,int sampleDays,LocalDate from,LocalDate to){this.available=true;reason="";this.value=value;this.kwh=kwh;observedKwh=observed;this.average=average;this.sampleDays=sampleDays;this.from=from;this.to=to;}
    }
    private static final class Day {final double kwh;final boolean complete;Day(double kwh,boolean complete){this.kwh=kwh;this.complete=complete;}}
    private static boolean integer(JSONObject row,String key){Object v=row.opt(key);return v instanceof Number&&Double.isFinite(((Number)v).doubleValue())&&((Number)v).doubleValue()==Math.rint(((Number)v).doubleValue());}
    private static Result missing(String reason){return new Result(reason);}
    static Result calculate(JSONArray rows,int months,int anchor,LocalDate today,WidgetPricing price){
        try{return evaluate(rows,months,anchor,today,price);}catch(Exception invalid){return missing("Dati o tariffe non validi: aggiorna Enel e verifica le impostazioni.");}
    }
    private static Result evaluate(JSONArray rows,int months,int anchor,LocalDate today,WidgetPricing price)throws Exception{
        if(rows==null||today==null||price==null||months<1||months>3||anchor<1||anchor>12)return missing("Dati di proiezione non disponibili.");
        NavigableMap<LocalDate,Day> daily=new TreeMap<>();Map<LocalDate,JSONObject> monthly=new HashMap<>();
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);if(row==null||!integer(row,"year")||!integer(row,"month")||row.optInt("year")<2000||row.optInt("year")>2100||row.optInt("month")<1||row.optInt("month")>12)continue;
            LocalDate month=LocalDate.of(row.getInt("year"),row.getInt("month"),1);
            if(monthly.put(month,row)!=null)return missing("Consumi mensili duplicati: aggiorna Enel.");
            JSONArray records=row.optJSONArray("daily");if(records==null)continue;
            if(records.length()>31)return missing("Dati giornalieri non validi: aggiorna Enel.");
            for(int d=0;d<records.length();d++){
                JSONObject day=records.optJSONObject(d);if(day==null||!integer(day,"day")||day.optInt("day")<1||day.optInt("day")>month.lengthOfMonth()||!(day.opt("kwh") instanceof Number)||!Double.isFinite(day.optDouble("kwh"))||day.optDouble("kwh")<0||day.optDouble("kwh")>250000)return missing("Dati giornalieri non validi: aggiorna Enel.");
                LocalDate date=month.withDayOfMonth(day.getInt("day"));if(daily.containsKey(date)||date.isAfter(today))return missing("Dati giornalieri duplicati o futuri: aggiorna Enel.");
                boolean complete=Boolean.TRUE.equals(day.opt("complete"))&&integer(day,"hourSamples")&&day.optInt("hourSamples")>=23&&day.optInt("hourSamples")<=25;
                daily.put(date,new Day(day.getDouble("kwh"),complete));
            }
        }
        LocalDate last=null;for(Map.Entry<LocalDate,Day> day:daily.entrySet())if(day.getKey().isBefore(today)&&day.getValue().complete)last=day.getKey();
        if(last==null)return missing("Servono almeno 7 giorni completi registrati. Aggiorna Enel per acquisire le letture giornaliere.");
        if(java.time.temporal.ChronoUnit.DAYS.between(last,today)>7)return missing("Le letture giornaliere sono ferme da oltre 7 giorni. Aggiorna Enel.");
        double average=0;int samples=0;for(Map.Entry<LocalDate,Day> day:daily.descendingMap().entrySet())if(!day.getKey().isBefore(last.minusDays(6))&&!day.getKey().isAfter(last)&&day.getValue().complete){average+=day.getValue().kwh;samples++;}
        if(samples<7)return missing("Servono almeno 7 giorni completi nella settimana di riferimento ("+samples+" disponibili).");average/=samples;
        LocalDate start=today.withDayOfMonth(1).minusMonths(Math.floorMod(today.getMonthValue()-anchor,months)),end=start.plusMonths(months).minusDays(1);
        double total=0,kwh=0,observed=0;
        for(int i=0;i<months;i++){
            LocalDate first=start.plusMonths(i),lastDay=first.withDayOfMonth(first.lengthOfMonth());JSONObject row=monthly.get(first);double actual=0,projected=0;
            if(lastDay.isBefore(today)&&row!=null&&row.optBoolean("available")&&!row.optBoolean("partial")&&row.opt("kwh") instanceof Number&&Double.isFinite(row.optDouble("kwh"))&&row.optDouble("kwh")>=0){
                JSONArray records=row.optJSONArray("daily");if(records!=null&&records.length()>0){double verified=0;for(int d=1;d<=first.lengthOfMonth();d++){Day day=daily.get(first.withDayOfMonth(d));if(day==null)return missing("Manca una giornata nel dettaglio del mese concluso. Aggiorna Enel.");verified+=day.kwh;}if(Math.abs(verified-row.getDouble("kwh"))>.02)return missing("Totale mensile e dettaglio giornaliero non coincidono. Aggiorna Enel.");}
                actual=projected=row.getDouble("kwh");
            }else if(lastDay.isBefore(today))return missing("Manca un mese concluso completo nel periodo. Aggiorna Enel.");
            else{
                if(row!=null&&row.optBoolean("available")&&(row.optJSONArray("daily")==null||row.optJSONArray("daily").length()==0))return missing("Premi Aggiorna: manca il dettaglio giornaliero dei consumi salvati.");
                for(int d=1;d<=first.lengthOfMonth();d++){LocalDate date=first.withDayOfMonth(d);Day day=daily.get(date);double measured=day==null?0:day.kwh;actual+=measured;projected+=date.isBefore(today)&&day!=null&&day.complete?measured:Math.max(measured,average);}
                if(row!=null&&row.optBoolean("available")&&(!(row.opt("kwh") instanceof Number)||!Double.isFinite(row.optDouble("kwh"))||row.optDouble("kwh")<0||Math.abs(row.optDouble("kwh")-actual)>.02))return missing("Totale mensile e dettaglio giornaliero non coincidono. Aggiorna Enel.");
            }
            total+=price.estimatedCost(projected,1);kwh+=projected;observed+=actual;
        }
        return new Result(Math.round((total+Math.ulp(1.0))*100)/100.0,kwh,observed,average,samples,start,end);
    }
}
