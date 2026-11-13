package com.costoluce.importatore;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class BillHistoryTest {
    private Calendar day(int year,int month){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("Europe/Rome"));c.clear();c.set(year,month-1,13,12,0);return c;}
    private JSONObject rates()throws Exception{
        JSONObject r=new JSONObject("{\"energy\":0.135,\"losses\":10,\"dispatch\":0.025043,\"balancing\":0.001042598,\"transport\":0.0119,\"equalization\":0.00283,\"asos\":0.031515,\"arim\":0.001638,\"excise\":0.0227,\"vat\":10}");
        JSONArray custom=new JSONArray();
        add(custom,"custom_arera_rete_fissa","Rete quota fissa",1.92);
        add(custom,"custom_arera_rete_potenza","Rete quota potenza",5.88);
        add(custom,"custom_arera_qualita_potenza","Rete qualità potenza",.0497);
        add(custom,"custom_offer_commercialization","Commercializzazione vendita",12);
        add(custom,"custom_offer_dispbt","DISPbt parte fissa",.092250);r.put("_custom",custom);return r;
    }
    private void add(JSONArray rows,String id,String name,double amount)throws Exception{rows.put(new JSONObject().put("id",id).put("name",name).put("type","fixed").put("value",amount));}
    private JSONObject usage(int year,int month,double kwh,boolean partial)throws Exception{return new JSONObject().put("year",year).put("month",month).put("available",true).put("partial",partial).put("kwh",kwh);}
    @Test public void billIncludesAllFixedVariableExciseAndVatNotJustEnergy()throws Exception{
        WidgetPricing pricing=WidgetPricing.parse(rates().toString(),true);JSONArray data=new JSONArray().put(usage(2026,8,177.9,false)).put(usage(2026,9,160.2,false));
        JSONObject bill=BillHistory.rows(data,day(2026,11),2,8,pricing).getJSONObject(2);
        assertTrue(bill.getBoolean("available"));assertFalse(bill.getBoolean("partial"));assertEquals("Ago–Set",bill.getString("label"));
        assertEquals(127.56,bill.getDouble("billEuro"),.000001);
        assertTrue(bill.getDouble("billEuro")>177.9*.135+160.2*.135+70); // Includes fees and taxes beyond energy alone.
        assertEquals(Math.round((pricing.estimatedCost(177.9,1)+pricing.estimatedCost(160.2,1))*100)/100.0,bill.getDouble("billEuro"),.000001);
    }
    @Test public void measuredZeroStillAccruesFixedCosts()throws Exception{
        JSONObject bill=BillHistory.rows(new JSONArray().put(usage(2026,11,0,true)),day(2026,11),1,10,WidgetPricing.parse(rates().toString(),true)).getJSONObject(3);
        assertTrue(bill.getBoolean("available"));assertEquals(21.94,bill.getDouble("billEuro"),.000001);
    }
    @Test public void quarterlyFutureMonthsDoNotAddFeesOrConsumption()throws Exception{
        WidgetPricing pricing=WidgetPricing.parse(rates().toString(),true);
        JSONObject bill=BillHistory.rows(new JSONArray().put(usage(2026,11,100,true)),day(2026,11),3,11,pricing).getJSONObject(3);
        assertTrue(bill.getBoolean("partial"));assertEquals("Nov–Gen",bill.getString("label"));assertEquals("2026–2027",bill.getString("years"));
        assertEquals(Math.round(pricing.estimatedCost(100,1)*100)/100.0,bill.getDouble("billEuro"),.000001);
    }
    @Test public void missingMonthNeverBecomesACompleteBill()throws Exception{
        JSONArray rows=BillHistory.rows(new JSONArray().put(usage(2026,8,178,false)),day(2026,11),2,8,WidgetPricing.parse(rates().toString(),true));
        assertEquals(4,rows.length());assertFalse(rows.getJSONObject(2).getBoolean("available"));assertFalse(rows.getJSONObject(0).getBoolean("available"));
    }
    @Test public void pricingConfigurationIsRequired()throws Exception{
        assertFalse(BillHistory.rows(new JSONArray().put(usage(2026,11,100,true)),day(2026,11),1,10,null).getJSONObject(3).getBoolean("available"));
    }
    @Test public void fixedOfferRecognitionPreservesRenamedStableIdsAndFlagsConflicts()throws Exception{
        JSONObject r=rates();assertTrue(BillHistory.offerCostsRecognized(r.toString()));r.getJSONArray("_custom").getJSONObject(3).put("name","Mio canone");assertTrue(BillHistory.offerCostsRecognized(r.toString()));
        add(r.getJSONArray("_custom"),"custom_other_fee","Commercializzazione",15);assertFalse(BillHistory.offerCostsRecognized(r.toString()));
        JSONObject missing=rates();missing.getJSONArray("_custom").remove(4);assertFalse(BillHistory.offerCostsRecognized(missing.toString()));
    }
    @Test public void networkChargesAreRequiredForCompleteFixedCostRecognition()throws Exception{
        JSONObject r=rates();r.getJSONArray("_custom").remove(0);assertFalse(BillHistory.offerCostsRecognized(r.toString()));
        JSONObject renamed=rates();renamed.getJSONArray("_custom").getJSONObject(1).put("name","La mia potenza");assertTrue(BillHistory.offerCostsRecognized(renamed.toString()));
    }

}
