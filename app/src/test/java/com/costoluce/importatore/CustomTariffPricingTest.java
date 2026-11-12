package com.costoluce.importatore;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class CustomTariffPricingTest {
    private JSONObject rates()throws Exception{return new JSONObject("{\"energy\":0.135,\"losses\":10,\"dispatch\":0.021117,\"balancing\":0.000787,\"transport\":0.0119,\"equalization\":0.00283,\"asos\":0.030095,\"arim\":0.001638,\"excise\":0.0227,\"vat\":10}");}
    private void customize(JSONObject r,String key,String name,String type)throws Exception{JSONObject meta=r.optJSONObject("_meta");if(meta==null){meta=new JSONObject();r.put("_meta",meta);}meta.put(key,new JSONObject().put("name",name).put("type",type));}
    @Test public void monthlyFeeReplacesConsumptionRateAndIncludesVat()throws Exception{
        JSONObject r=rates();double previous=WidgetPricing.parse(r.toString(),true).estimatedCost(479,2,218);
        r.put("dispatch",8);customize(r,"dispatch","Quota fissa mensile","fixed");WidgetPricing p=WidgetPricing.parse(r.toString(),true);
        assertEquals(previous+(16-479*0.021117)*1.1,p.estimatedCost(479,2,218),0.0000001);
        assertEquals(8.8,p.estimatedCost(479,2,218,2)-p.estimatedCost(479,2,218,1),0.0000001);assertTrue(p.hasFixedCosts());
    }
    @Test public void renamedVariableDoesNotChangeTaxRules()throws Exception{JSONObject r=rates();double previous=WidgetPricing.parse(r.toString(),true).estimatedCost(500,2,400);customize(r,"excise","Imposta personalizzata","variable");assertEquals(previous,WidgetPricing.parse(r.toString(),true).estimatedCost(500,2,400),0);}
    @Test public void energyMonthlyModeMakesLossesFollowItsBase()throws Exception{JSONObject r=rates();double old=WidgetPricing.parse(r.toString(),true).estimatedCost(100,2,0);r.put("energy",20);customize(r,"energy","Energia €/kWh","fixed");assertEquals(old+(40-13.5)*1.1*1.1,WidgetPricing.parse(r.toString(),true).estimatedCost(100,2,0),0.0000001);}
    @Test(expected=JSONException.class)public void energyCannotBeRenamed()throws Exception{JSONObject r=rates();customize(r,"energy","Altro","variable");WidgetPricing.parse(r.toString(),true);}
    @Test(expected=JSONException.class)public void percentageCannotBecomeMonthlyMoney()throws Exception{JSONObject r=rates();customize(r,"vat","IVA","fixed");WidgetPricing.parse(r.toString(),true);}
    @Test(expected=JSONException.class)public void controlCharactersInNamesAreRejected()throws Exception{JSONObject r=rates();customize(r,"dispatch","Nome\nNon valido","fixed");WidgetPricing.parse(r.toString(),true);}

    private JSONObject row(String id,String type,double value)throws Exception{return new JSONObject().put("id",id).put("name","Voce aggiunta").put("type",type).put("value",value);}
    @Test public void addedFixedAndVariableRowsMatchAppTotals()throws Exception{JSONObject r=rates();double old=WidgetPricing.parse(r.toString(),true).estimatedCost(479,2,218);r.put("_custom",new JSONArray().put(row("custom_month","fixed",8)).put(row("custom_kwh","variable",.02)));WidgetPricing p=WidgetPricing.parse(r.toString(),true);assertEquals(old+(16+479*.02)*1.1,p.estimatedCost(479,2,218),1e-8);assertEquals(8.8,p.estimatedCost(479,2,218,2)-p.estimatedCost(479,2,218,1),1e-8);assertTrue(p.hasFixedCosts());}
    @Test public void deletedDefaultChargeDoesNotAffectTotals()throws Exception{JSONObject r=rates();double old=WidgetPricing.parse(r.toString(),true).estimatedCost(479,2,218);customize(r,"dispatch","Dispacciamento","variable");r.getJSONObject("_meta").getJSONObject("dispatch").put("enabled",false);assertEquals(old-479*.021117*1.1,WidgetPricing.parse(r.toString(),true).estimatedCost(479,2,218),1e-8);}
    @Test public void disabledVatIsExcludedAndDisabledFixedDoesNotChangeCaption()throws Exception{JSONObject r=rates();customize(r,"dispatch","Quota","fixed");r.put("dispatch",8);r.getJSONObject("_meta").getJSONObject("dispatch").put("enabled",false);WidgetPricing noFixed=WidgetPricing.parse(r.toString(),true);assertFalse(noFixed.hasFixedCosts());double taxed=noFixed.estimatedCost(479,2,218);customize(r,"vat","IVA","percent");r.getJSONObject("_meta").getJSONObject("vat").put("enabled",false);assertEquals(taxed/1.1,WidgetPricing.parse(r.toString(),true).estimatedCost(479,2,218),1e-8);}
    @Test(expected=JSONException.class)public void energyCannotBeDisabled()throws Exception{JSONObject r=rates();customize(r,"energy","Energia €/kWh","variable");r.getJSONObject("_meta").getJSONObject("energy").put("enabled",false);WidgetPricing.parse(r.toString(),true);}
    @Test(expected=JSONException.class)public void duplicateExtraIdsAreRejected()throws Exception{JSONObject r=rates();r.put("_custom",new JSONArray().put(row("custom_same","fixed",8)).put(row("custom_same","variable",.02)));WidgetPricing.parse(r.toString(),true);}
    @Test(expected=JSONException.class)public void negativeExtraRateIsRejected()throws Exception{JSONObject r=rates();r.put("_custom",new JSONArray().put(row("custom_bad","fixed",-1)));WidgetPricing.parse(r.toString(),true);}
    @Test(expected=JSONException.class)public void tooManyExtraRowsAreRejected()throws Exception{JSONObject r=rates();JSONArray rows=new JSONArray();for(int i=0;i<31;i++)rows.put(row("custom_"+i,"fixed",1));r.put("_custom",rows);WidgetPricing.parse(r.toString(),true);}
}
