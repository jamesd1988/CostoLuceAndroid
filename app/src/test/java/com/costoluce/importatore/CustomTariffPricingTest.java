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
}
